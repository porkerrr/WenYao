"""Regression tests for redaction and release-audit coverage."""
import importlib.util
import io
import json
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest
import zipfile

SPEC = importlib.util.spec_from_file_location("audit_release_security", Path(__file__).parents[1] / "scripts" / "audit_release_security.py")
MODULE = importlib.util.module_from_spec(SPEC)
sys.modules[SPEC.name] = MODULE
SPEC.loader.exec_module(MODULE)


class ReleaseSecurityTest(unittest.TestCase):
    def test_candidate_report_does_not_contain_secret(self):
        candidate = "sk-" + "q9Bz4DdV7aMc2" * 3
        findings = MODULE.scan_bytes(("first line\napi_key = '" + candidate + "'").encode(), "config.txt", "history", "object", "commit")
        self.assertIn("provider-key-candidate", {item.kind for item in findings})
        self.assertTrue(all(item.line == 2 for item in findings))
        self.assertNotIn(candidate, json.dumps([MODULE.asdict(item) for item in findings]))
        self.assertNotIn(candidate[-4:], json.dumps([MODULE.asdict(item) for item in findings]))

    def test_placeholder_hints_never_suppress_candidates(self):
        findings = MODULE.scan_bytes(b"sk-placeholder-for-unit-test-only", "test.kt", "workspace")
        self.assertEqual({item.kind for item in findings}, {"provider-key-candidate", "placeholder-hint"})

    def test_random_token_with_test_substring_is_still_reviewed(self):
        candidate = "sk-" + "q9Bz4DdV7aMc2" + "test" + "R8vLd3pNm9Qx6"
        findings = MODULE.scan_bytes(candidate.encode(), "config.txt", "history")
        self.assertIn("provider-key-candidate", {item.kind for item in findings})
        self.assertNotIn(candidate, json.dumps([MODULE.asdict(item) for item in findings]))

    def test_personal_path_is_not_printed(self):
        personal = "C:" + "\\Users\\" + "PersonalName\\Desktop\\secret.txt"
        findings = MODULE.scan_bytes(personal.encode(), "readme.md", "workspace")
        self.assertEqual(findings[0].kind, "personal-absolute-path")
        self.assertNotIn("PersonalName", repr(findings))

    def test_zip_members_are_scanned_without_extracting(self):
        archive = io.BytesIO()
        with zipfile.ZipFile(archive, "w") as item:
            item.writestr("../../example.txt", "https://127.0.0.1:8080/test")
        limitations = []
        findings = MODULE.scan_archive(archive.getvalue(), "review.zip", "history", "object", "commit", limitations)
        self.assertEqual(findings[0].kind, "private-network-url")
        self.assertEqual(findings[0].path, "review.zip!../../example.txt")
        self.assertEqual(limitations, [])

    def test_history_scans_removed_file_and_other_branch(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            def git(*args):
                return subprocess.check_output(["git", "-c", "safe.directory=" + root.as_posix(), *args], cwd=root, stderr=subprocess.PIPE)
            git("init", "-b", "main")
            git("config", "user.name", "Audit Test")
            git("config", "user.email", "audit@example.invalid")
            hidden = root / "removed.txt"
            hidden.write_text("C:" + "\\Users\\" + "HistoricalName\\file", encoding="utf-8")
            git("add", ".")
            git("commit", "-m", "first")
            git("branch", "retained-history")
            hidden.unlink()
            (root / "readme.txt").write_text("safe", encoding="utf-8")
            git("add", "-A")
            git("commit", "-m", "remove local path")
            result = MODULE.audit(root, include_history=True)
            self.assertEqual(result["coverage"]["reachable_commits"], 2)
            history = [item for item in result["findings"] if item["source"] == "history"]
            self.assertTrue(any(item["path"] == "removed.txt" for item in history))
            self.assertNotIn("HistoricalName", json.dumps(result))


if __name__ == "__main__":
    unittest.main()
