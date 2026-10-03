"""Remove PNG text metadata containing local paths; preserve image data bytes."""
import argparse
from pathlib import Path
import re
import struct
import zlib

SIGNATURE = b'\x89PNG\r\n\x1a\n'
PRIVATE_PATH = re.compile(rb'(?:[A-Z]:[\\/]+|/(?:Users|home)/)', re.I)
TEXT_TYPES = {b'tEXt', b'zTXt', b'iTXt'}


def strip_private_metadata(data):
    if not data.startswith(SIGNATURE):
        raise ValueError('Not a PNG')
    result = bytearray(SIGNATURE)
    offset, removed, ended = 8, 0, False
    while offset < len(data):
        if offset + 12 > len(data):
            raise ValueError('Truncated PNG chunk')
        length = struct.unpack('>I', data[offset:offset + 4])[0]
        end = offset + length + 12
        if end > len(data):
            raise ValueError('Truncated PNG payload')
        kind = data[offset + 4:offset + 8]
        payload = data[offset + 8:offset + 8 + length]
        crc = struct.unpack('>I', data[end - 4:end])[0]
        if zlib.crc32(kind + payload) & 0xffffffff != crc:
            raise ValueError('Invalid PNG CRC')
        searchable = payload
        if kind == b'zTXt':
            _, compressed = payload.split(b'\0', 1)
            if not compressed or compressed[0] != 0:
                raise ValueError('Unsupported PNG text compression')
            searchable += zlib.decompress(compressed[1:])
        elif kind == b'iTXt':
            _, rest = payload.split(b'\0', 1)
            flag, method = rest[:2]
            _, rest = rest[2:].split(b'\0', 1)
            _, content = rest.split(b'\0', 1)
            if method != 0 or flag not in (0, 1):
                raise ValueError('Unsupported international PNG text')
            searchable += zlib.decompress(content) if flag else content
        if kind in TEXT_TYPES and PRIVATE_PATH.search(searchable):
            removed += 1
        else:
            result.extend(data[offset:end])
        offset = end
        if kind == b'IEND':
            ended = True
            if offset != len(data):
                raise ValueError('Unexpected bytes after PNG end')
            break
    if not ended:
        raise ValueError('Missing PNG end')
    return bytes(result), removed


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('paths', nargs='+', type=Path)
    parser.add_argument('--write', action='store_true', help='Rewrite only files with private metadata')
    args = parser.parse_args()
    for path in args.paths:
        sanitized, count = strip_private_metadata(path.read_bytes())
        if count:
            if args.write:
                path.write_bytes(sanitized)
            print(f'{path}: {count} private text chunk(s); write={args.write}')


if __name__ == '__main__':
    main()
