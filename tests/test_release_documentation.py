"""Check public release documentation against the checkout without network access.

Only the small publication surface is scanned: development archives and old UI
evidence are intentionally outside this documentation regression test.
"""

from pathlib import Path
import re
import unittest
from urllib.parse import unquote, urlsplit


ROOT = Path(__file__).resolve().parents[1]
APP_GRADLE = ROOT / "app/build.gradle.kts"
PROVIDER_SOURCE = ROOT / "app/src/main/java/com/peterpork/liuyao/data/Analysis.kt"


def read(relative_path):
    return (ROOT / relative_path).read_text(encoding="utf-8-sig")


def capture(pattern, text):
    match = re.search(pattern, text, re.MULTILINE)
    if match is None:
        raise AssertionError("Expected release metadata field was not found")
    return match.group(1)


class ReleaseDocumentationTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        gradle = APP_GRADLE.read_text(encoding="utf-8-sig")
        cls.version = capture(r'^\s*versionName\s*=\s*"([^"\n]+)"', gradle)
        cls.version_code = capture(r"^\s*versionCode\s*=\s*(\d+)", gradle)
        cls.release_path = f"docs/releases/v{cls.version}.md"
        cls.public_files = (
            "README.md",
            "README_EN.md",
            "CHANGELOG.md",
            "CONTRIBUTING.md",
            "SECURITY.md",
            "LICENSE",
            "ASSET_LICENSE.md",
            "docs/RELEASE_VERIFICATION.md",
            "THIRD_PARTY_NOTICES.md",
            ".github/ISSUE_TEMPLATE/bug_report.yml",
            ".github/ISSUE_TEMPLATE/feature_request.yml",
            ".github/pull_request_template.md",
            cls.release_path,
        )

    def test_publication_documents_exist_and_are_readable(self):
        for relative in self.public_files:
            with self.subTest(document=relative):
                self.assertTrue((ROOT / relative).is_file())
                self.assertTrue(read(relative).strip())

    def test_relative_markdown_links_resolve_inside_repository(self):
        # Public docs use ordinary inline Markdown links. Skip remote URLs and
        # same-page fragments; never make network requests as part of this test.
        for relative in self.public_files:
            if not relative.endswith(".md"):
                continue
            document = ROOT / relative
            links = re.findall(r"!?\[[^\]\n]*\]\(([^)\n]+)\)", read(relative))
            for index, destination in enumerate(links):
                with self.subTest(document=relative, link_number=index + 1):
                    parsed = urlsplit(destination.strip().strip("<>"))
                    if parsed.scheme or parsed.netloc:
                        self.assertEqual(parsed.scheme, "https", "Public links must use HTTPS")
                        self.assertIsNone(parsed.username, "Link contains credentials")
                        self.assertIsNone(parsed.password, "Link contains credentials")
                        continue
                    if not parsed.path:
                        continue
                    target = (document.parent / unquote(parsed.path)).resolve()
                    self.assertTrue(target.is_relative_to(ROOT), "Link escapes repository")
                    self.assertTrue(target.exists(), "Local documentation link is broken")

    def test_documented_release_version_matches_gradle(self):
        chinese = read("README.md")
        english = read("README_EN.md")
        changelog = read("CHANGELOG.md")
        release = read(self.release_path)
        self.assertEqual(chinese.splitlines()[0], "# 问爻")
        self.assertEqual(
            capture(r"\| 应用版本 \| `([^`]+)`", chinese), self.version
        )
        self.assertEqual(capture(r"versionName ([^`\s]+)", english), self.version)
        self.assertEqual(
            capture(r"versionName\s*=\s*([^`\s]+)", changelog), self.version
        )
        self.assertEqual(release.splitlines()[0], f"# WenYao v{self.version}")
        for document, content in (
            ("README.md", chinese),
            ("README_EN.md", english),
            ("CHANGELOG.md", changelog),
            (self.release_path, release),
        ):
            with self.subTest(document=document):
                self.assertEqual(
                    capture(r"versionCode\s*(?:=\s*)?(\d+)", content),
                    self.version_code,
                )
        for document in ("docs/RELEASE_VERIFICATION.md",):
            with self.subTest(document=document):
                self.assertEqual(capture(r"versionName\s*=\s*([^`\s]+)", read(document)), self.version)
                self.assertEqual(capture(r"versionCode\s*=\s*(\d+)", read(document)), self.version_code)

    def test_readme_provider_tables_match_source_profiles(self):
        source = PROVIDER_SOURCE.read_text(encoding="utf-8-sig")
        # Read the actual profile constants, so a provider or model change must
        # be accompanied by current user-facing configuration instructions.
        blocks = re.findall(r"object (\w+Profile)\s*\{(.*?)\n\}", source, re.DOTALL)
        self.assertTrue(blocks, "No provider profiles found")
        for profile_name, block in blocks:
            model = capture(r'const val MODEL\s*=\s*"([^"\n]+)"', block)
            base_url = capture(r'const val BASE_URL\s*=\s*"([^"\n]+)"', block)
            for relative in ("README.md", "README_EN.md"):
                with self.subTest(document=relative, provider=profile_name):
                    rows = [line for line in read(relative).splitlines() if line.startswith("|")]
                    self.assertTrue(
                        any(f"`{model}`" in row and f"`{base_url}`" in row for row in rows),
                        "Provider model and endpoint must appear together in the configuration table",
                    )
            with self.subTest(document=self.release_path, provider=profile_name):
                self.assertIn(f"`{model}`", read(self.release_path))

    def test_publication_surface_has_no_personal_absolute_paths(self):
        patterns = (
            # Drive paths and network shares are local-machine evidence, not
            # portable instructions. Failure output deliberately omits values.
            r"(?i)\b[a-z]:[\\/]",
            r"\\\\[A-Za-z0-9_.-]+\\",
            r"(?i)/(?:Users|home)/[^\s/]+/",
            r"(?i)file://",
        )
        for relative in self.public_files:
            content = read(relative)
            with self.subTest(document=relative):
                for pattern in patterns:
                    self.assertFalse(
                        bool(re.search(pattern, content)),
                        "Publication document contains a machine-specific absolute path",
                    )


if __name__ == "__main__":
    unittest.main()
