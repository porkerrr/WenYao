"""Release safeguards without Android, credentials, or network access."""
import ast
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess
import tempfile
import unittest
from unittest.mock import patch

ROOT = Path(__file__).resolve().parents[1]


class ReleasePackagingTests(unittest.TestCase):
    def ignored(self, path):
        result = subprocess.run(
            ['git', 'check-ignore', '--no-index', '--quiet', path],
            cwd=ROOT, capture_output=True, check=False,
        )
        self.assertIn(result.returncode, (0, 1), result.stderr.decode(errors='replace'))
        return result.returncode == 0

    def test_generated_and_secret_files_are_excluded(self):
        for path in ['.local/audit.json', '.gradle/cache', 'app/build/result.txt',
                     'local.properties', '.env', '.env.production', 'keys/release.jks',
                     'keys/release.keystore', 'signing.properties', 'secrets.properties',
                     '.codex/config.toml', 'hs_err_pid123.log', '.idea/workspace.xml',
                     'tools/blender/example/android-build/output.txt',
                     'wenyao_round4_review.zip']:
            with self.subTest(path=path):
                self.assertTrue(self.ignored(path))

    def test_sources_and_placeholder_example_remain_trackable(self):
        for path in ['.env.example', 'app/build.gradle.kts', 'gradle.properties',
                     'gradle/wrapper/gradle-wrapper.jar', 'app/schemas/db/5.json',
                     'app/src/main/assets/coins.glb', 'tools/blender/new_source.py',
                     '.github/workflows/android-ci.yml']:
            with self.subTest(path=path):
                self.assertFalse(self.ignored(path))

    def test_public_copy_excludes_development_archives(self):
        for name in ['.agents', '.codex', 'tools/blender', 'GITHUB_RELEASE_AUDIT.md',
                     'GITHUB_RELEASE_REPORT.md', 'docs/ui', 'skills-lock.json']:
            with self.subTest(path=name):
                self.assertFalse((ROOT / name).exists())

    def test_asset_license_boundary_and_inventory(self):
        manifest = json.loads((ROOT / 'docs/ASSET_MANIFEST.json').read_text(encoding='utf-8'))
        expected = {p.relative_to(ROOT).as_posix() for p in (ROOT / 'app/src').rglob('*')
                    if p.is_file() and (p.suffix in {'.png', '.glb'} or
                       p.name in {'wenyao_ink.json', 'wenyao_brush_mask.json'})}
        self.assertEqual(expected, {row['path'] for row in manifest})
        for row in manifest:
            self.assertEqual(row['sha256'], hashlib.sha256((ROOT / row['path']).read_bytes()).hexdigest())
        self.assertIn('ASSET_LICENSE.md', (ROOT / 'LICENSE').read_text(encoding='utf-8'))
        for readme in ['README.md', 'README_EN.md']:
            self.assertIn('ASSET_LICENSE', (ROOT / readme).read_text(encoding='utf-8'))

    def test_public_ci_cannot_publish_or_use_provider_secrets(self):
        workflow = (ROOT / '.github/workflows/android-ci.yml').read_text(encoding='utf-8')
        self.assertNotRegex(workflow, r'pull_request_target|secrets\.|contents:\s*write|continue-on-error:\s*true')
        self.assertIn('persist-credentials: false', workflow)
        for action in re.findall(r'uses:\s*(\S+)', workflow):
            self.assertRegex(action, r'^(?:actions/|gradle/actions/)[^@]+@[a-f0-9]{40}$')
        for task in ['compileDebugKotlin', 'compileReleaseKotlin', 'gradlew test',
                     'gradlew lint', ':app:assembleDebug', ':app:assembleRelease']:
            self.assertIn(task, workflow)
        self.assertNotRegex(workflow, r'(?m)^\s*run:.*(?:curl|wget).*\|\s*(?:sh|bash)')

    def test_archived_licenses_preserve_upstream_bytes(self):
        folder = ROOT / 'docs/licenses'
        manifest = json.loads((folder / 'sources.json').read_text(encoding='utf-8'))
        self.assertTrue(manifest)
        for entry in manifest:
            with self.subTest(license=entry['file']):
                path = (folder / entry['file']).resolve()
                self.assertTrue(path.is_relative_to(folder.resolve()))
                self.assertEqual('downloaded', entry['status'])
                self.assertTrue(entry['url'].startswith('https://'))
                self.assertEqual(entry['sha256'], hashlib.sha256(path.read_bytes()).hexdigest())


if __name__ == '__main__':
    unittest.main()
