"""Offline release audit; findings never include matched text or secret values.

Scans every blob and commit message reachable from local refs when --history is
used. This is a conservative heuristic, not proof that a repository is safe.
Binary data is searched as UTF-8/Latin-1 text; ZIP text members are also scanned.
Encrypted/compressed native formats and pixels require a separate human review.
"""
from __future__ import annotations

import argparse
from collections import Counter
from dataclasses import asdict, dataclass
import io
import json
import os
from pathlib import Path
import re
import subprocess
import zipfile


@dataclass(frozen=True)
class Finding:
    kind: str
    path: str
    line: int
    source: str
    object_id: str = ""
    commit: str = ""


PLACEHOLDER = re.compile(
    r"(?i)(?:example|placeholder|dummy|test|fake|replace|your[-_ ]|redact|sample|mock|demo|not[-_]real|x{6,})"
)
PATTERNS = {
    "provider-key-candidate": re.compile(r"(?<![\w-])sk-[A-Za-z0-9_-]{20,}"),
    "google-key-candidate": re.compile(r"\bAIza[A-Za-z0-9_-]{30,}"),
    "github-token-candidate": re.compile(r"\b(?:gh[pousr]_[A-Za-z0-9]{30,}|github_pat_[A-Za-z0-9_]{30,})"),
    "cloud-access-key-candidate": re.compile(r"\b(?:AKIA|ASIA)[A-Z0-9]{16}\b"),
    "private-key-material": re.compile(r"-----BEGIN (?:RSA |EC |OPENSSH |DSA )?PRIVATE KEY-----"),
    "credential-in-url": re.compile(r"https?://[^\s/:]+:[^\s/@]+@[^\s]+", re.I),
    "personal-absolute-path": re.compile(r"(?:[A-Z]:[\\/]+Users[\\/]+[^\s\\/<>\"']+|/(?:Users|home)/[^\s/<>\"']+)", re.I),
    "machine-absolute-path": re.compile(r"(?<![A-Za-z])[A-Z]:[\\/]+(?:Steam|Program Files|Android|CodexProjects|Tools|tools|Work|work)[\\/]+[^\r\n\"'`]*", re.I),
    "private-network-url": re.compile(r"https?://(?:localhost|127\.\d+\.\d+\.\d+|10\.\d+\.\d+\.\d+|192\.168\.\d+\.\d+|172\.(?:1[6-9]|2\d|3[01])\.\d+\.\d+|\[::1\])(?::\d+)?(?:/[^\s\"'<>]*)?", re.I),
    "bearer-literal-candidate": re.compile(r"\bBearer\s+[A-Za-z0-9_-]{20,}", re.I),
    "secret-assignment-candidate": re.compile(r"(?:api[_-]?key|token|secret|password|storePassword|keyPassword|PRIVATE_KEY)\s*[=:]\s*[\"']([^\"'\r\n]{16,})[\"']", re.I),
}
SKIP_DIRS = {".git", ".gradle", ".gradle-user-home", ".venv", ".kotlin", ".android-home", ".android-sdk-home", ".android-user-home", "__pycache__", "build"}
SIGNING_SUFFIXES = {".jks", ".keystore", ".p12", ".pfx"}
MAX_MEMBER_SIZE = 16 * 1024 * 1024
MAX_ARCHIVE_EXPANSION = 256 * 1024 * 1024


def is_placeholder(value: str) -> bool:
    return bool(PLACEHOLDER.search(value)) or len(set(value.removeprefix("sk-"))) <= 2


def scan_bytes(data: bytes, path: str, source: str, object_id: str = "", commit: str = "") -> list[Finding]:
    # ASCII is preserved even when the surrounding source is binary/UTF-16.
    text = data.decode("utf-8", errors="replace")
    findings: set[Finding] = set()
    for kind, pattern in PATTERNS.items():
        for match in pattern.finditer(text):
            value = match.group(1) if kind == "secret-assignment-candidate" else match.group()
            line = text.count("\n", 0, match.start()) + 1
            # A random real token can contain "test" or "mock". Hints must
            # never suppress a credential candidate or waive human review.
            findings.add(Finding(kind, path, line, source, object_id, commit))
            if "candidate" in kind and is_placeholder(value):
                findings.add(Finding("placeholder-hint", path, line, source, object_id, commit))
    if Path(path).suffix.lower() in SIGNING_SUFFIXES:
        findings.add(Finding("signing-file", path, 0, source, object_id, commit))
    return sorted(findings, key=lambda item: (item.path, item.line, item.kind))


def scan_archive(data: bytes, path: str, source: str, object_id: str, commit: str, limitations: list[dict]) -> list[Finding]:
    if not data.startswith(b"PK\x03\x04"):
        return []
    findings = []
    try:
        with zipfile.ZipFile(io.BytesIO(data)) as archive:
            total = 0
            for member in archive.infolist():
                if member.is_dir():
                    continue
                total += member.file_size
                if member.flag_bits & 1 or member.file_size > MAX_MEMBER_SIZE or total > MAX_ARCHIVE_EXPANSION:
                    limitations.append({"path": path + "!" + member.filename, "reason": "encrypted-or-expansion-limit", "object_id": object_id})
                    continue
                findings.extend(scan_bytes(archive.read(member), path + "!" + member.filename, source, object_id, commit))
    except (zipfile.BadZipFile, RuntimeError, NotImplementedError):
        limitations.append({"path": path, "reason": "unsupported-archive", "object_id": object_id})
    return findings


def git(root: Path, *arguments: str) -> bytes:
    return subprocess.check_output(["git", "-c", "safe.directory=" + root.as_posix(), *arguments], cwd=root, stderr=subprocess.PIPE)


def audit(root: Path, include_history: bool) -> dict:
    findings: list[Finding] = []
    limitations: list[dict] = []
    stats = Counter()
    large_files = []
    tracked = set(git(root, "ls-files", "-z").decode("utf-8").split("\0"))
    for directory, dirs, names in os.walk(root):
        for name in list(dirs):
            if name in SKIP_DIRS:
                dirs.remove(name)
                limitations.append({"path": (Path(directory) / name).relative_to(root).as_posix(), "reason": "local-generated-cache-directory-not-content-scanned"})
        for name in names:
            path = Path(directory) / name
            relative = path.relative_to(root).as_posix()
            try:
                data = path.read_bytes()
            except OSError:
                limitations.append({"path": relative, "reason": "unreadable-workspace-file"})
                continue
            stats["workspace_files"] += 1
            stats["workspace_bytes"] += len(data)
            source = "tracked-workspace" if relative in tracked else "untracked-workspace"
            findings.extend(scan_bytes(data, relative, source))
            findings.extend(scan_archive(data, relative, source, "", "", limitations))
            if len(data) >= 5 * 1024 * 1024:
                large_files.append({"path": relative, "bytes": len(data), "source": source})
    if include_history:
        commits = git(root, "rev-list", "--all", "--reverse").decode().splitlines()
        stats["reachable_commits"] = len(commits)
        objects: dict[str, dict] = {}
        for commit in commits:
            body = git(root, "cat-file", "commit", commit)
            findings.extend(scan_bytes(body, "<commit-message>", "history", commit, commit))
            for entry in git(root, "ls-tree", "-r", "-z", commit).split(b"\0"):
                if not entry:
                    continue
                metadata, path = entry.split(b"\t", 1)
                mode, kind, oid = metadata.decode().split()
                if kind != "blob":
                    limitations.append({"path": path.decode("utf-8"), "reason": "non-blob-tree-entry", "commit": commit})
                    continue
                obj = objects.setdefault(oid, {"paths": set(), "commit": commit})
                obj["paths"].add(path.decode("utf-8"))
        stats["reachable_blobs"] = len(objects)
        for oid, metadata in objects.items():
            data = git(root, "cat-file", "blob", oid)
            stats["history_blob_bytes"] += len(data)
            for path in sorted(metadata["paths"]):
                findings.extend(scan_bytes(data, path, "history", oid, metadata["commit"]))
                findings.extend(scan_archive(data, path, "history", oid, metadata["commit"], limitations))
                if len(data) >= 5 * 1024 * 1024:
                    large_files.append({"path": path, "bytes": len(data), "source": "history", "object_id": oid, "commit": metadata["commit"]})
    return {
        "coverage": dict(stats),
        "limitations": limitations,
        "findings_by_kind": dict(Counter(finding.kind for finding in findings)),
        "findings": [asdict(finding) for finding in findings],
        "large_files": sorted(large_files, key=lambda item: -item["bytes"]),
        "caveats": [
            "No secret values or source excerpts are retained in this report.",
            "All local refs reachable commits/blobs are covered when --history is selected; dangling/reflog-only objects and remote-only refs are excluded.",
            "Workspace generated/cache directories are enumerated but not content-scanned; tracked historical blobs in such directories are still scanned.",
            "Binary bytes and ZIP members are searched, but image/video pixels, nested archives, encrypted archives and proprietary/compressed binary contents are not decoded.",
            "UTF-16 text is not decoded; regex heuristics cannot establish absence of arbitrary opaque secrets.",
            "Candidate credentials require local human review; no live provider validation is performed.",
        ],
    }


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--root", type=Path, default=Path(__file__).resolve().parents[1])
    parser.add_argument("--history", action="store_true")
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    result = audit(args.root.resolve(), args.history)
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(result, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(json.dumps({"coverage": result["coverage"], "findings_by_kind": result["findings_by_kind"], "report": args.output.as_posix()}, ensure_ascii=False))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
