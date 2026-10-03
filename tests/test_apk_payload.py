import importlib.util
from pathlib import Path
import tempfile
import unittest
import zipfile

spec=importlib.util.spec_from_file_location('compare_apk',Path(__file__).resolve().parents[1]/'scripts/compare_apk_payload.py')
module=importlib.util.module_from_spec(spec);spec.loader.exec_module(module)
BASELINE='a'*40

class ApkPayloadTest(unittest.TestCase):
    def compare(self, changes=None, removed=(), baseline=BASELINE):
        payload={'classes.dex':b'compiled production code','resources.arsc':b'resources',
                 'assets/result.json':b'{\n"value": 6\n}\n',
                 module.VCS:f'repositories {{ revision: "{BASELINE}" }}'.encode()}
        with tempfile.TemporaryDirectory() as folder:
            signed=Path(folder)/'signed.apk';rebuilt=Path(folder)/'rebuilt.apk'
            with zipfile.ZipFile(signed,'w',compression=zipfile.ZIP_STORED) as z:
                for k,v in payload.items():z.writestr(k,v)
                z.writestr('META-INF/RELEASE.RSA',b'certificate fixture')
            payload.update(changes or {})
            for name in removed:payload.pop(name)
            with zipfile.ZipFile(rebuilt,'w',compression=zipfile.ZIP_DEFLATED) as z:
                for k,v in reversed(list(payload.items())):z.writestr(k,v)
            return module.compare(signed,rebuilt,baseline)

    def test_signing_layout_and_checked_build_metadata_are_not_code_differences(self):
        result=self.compare({module.VCS:b'generate_error_reason: NO_VALID_GIT_FOUND',
                             'assets/result.json':b'{\r\n"value": 6\r\n}\r\n'})
        self.assertTrue(result['match'])
        self.assertEqual(['assets/result.json'],result['line_ending_only_entries'])

    def test_changed_code_resource_or_asset_is_rejected(self):
        for path,value in [('classes.dex',b'experimental code'),('resources.arsc',b'other'),
                           ('assets/result.json',b'{"value": 9}')]:
            with self.subTest(path=path):self.assertFalse(self.compare({path:value})['match'])

    def test_missing_or_extra_payload_is_rejected(self):
        self.assertFalse(self.compare(removed=['classes.dex'])['match'])
        self.assertFalse(self.compare({'assets/experimental.glb':b'experiment'})['match'])

    def test_signed_revision_must_match_reviewed_baseline(self):
        self.assertFalse(self.compare(baseline='b'*40)['match'])

if __name__=='__main__':unittest.main()
