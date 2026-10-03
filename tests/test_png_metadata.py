import importlib.util
from pathlib import Path
import struct
import unittest
import zlib

spec = importlib.util.spec_from_file_location('png_metadata', Path(__file__).resolve().parents[1] / 'scripts/strip_png_private_metadata.py')
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)


def chunk(kind, payload):
    return struct.pack('>I', len(payload)) + kind + payload + struct.pack('>I', zlib.crc32(kind + payload) & 0xffffffff)


class PngPrivacyTests(unittest.TestCase):
    def test_removal_preserves_image_bytes_and_safe_attribution(self):
        image_chunks = chunk(b'IHDR', b'header') + chunk(b'IDAT', b'exact pixel stream')
        credit = chunk(b'tEXt', b'Author\0WenYao contributors')
        expected = module.SIGNATURE + image_chunks + credit + chunk(b'IEND', b'')
        private = chunk(b'tEXt', b'File\0C:/Users/example/project/render.blend')
        actual, removed = module.strip_private_metadata(module.SIGNATURE + image_chunks + private + credit + chunk(b'IEND', b''))
        self.assertEqual((expected, 1), (actual, removed))
        self.assertEqual((expected, 0), module.strip_private_metadata(actual))

    def test_compressed_and_international_text_are_checked(self):
        payloads = [
            (b'zTXt', b'File\0\0' + zlib.compress(b'/Users/example/model.blend')),
            (b'iTXt', b'File\0\x01\x00\0\0' + zlib.compress(b'/home/example/render.blend')),
        ]
        for kind, payload in payloads:
            with self.subTest(kind=kind):
                data = module.SIGNATURE + chunk(kind, payload) + chunk(b'IEND', b'')
                result, count = module.strip_private_metadata(data)
                self.assertEqual(1, count)
                self.assertEqual(module.SIGNATURE + chunk(b'IEND', b''), result)

    def test_bad_crc_and_truncation_fail_without_sanitizing(self):
        valid = module.SIGNATURE + chunk(b'IEND', b'')
        for malformed in [valid[:-1], valid[:-1] + bytes([valid[-1] ^ 1]), b'not png']:
            with self.assertRaises(ValueError):
                module.strip_private_metadata(malformed)


if __name__ == '__main__':
    unittest.main()
