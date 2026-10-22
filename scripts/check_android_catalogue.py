#!/usr/bin/env python3
"""Check the actual APK, not just the source assets directory.

AAPT interprets .gz input filenames specially: it decompresses and strips the
extension. The bundled GZIP stream therefore uses .bin and explicit noCompress.
"""
import argparse
import gzip
import hashlib
import json
from pathlib import Path
import zipfile


def verify(apk: Path):
    folder = Path(__file__).resolve().parents[1] / 'android/app/src/main/assets/catalogue'
    manifest = json.loads((folder / 'manifest.json').read_text())
    with zipfile.ZipFile(apk) as package:
        packed = package.read('assets/catalogue/assets.sqlite.bin')
        assert packed == (folder / 'assets.sqlite.bin').read_bytes(), 'Packaged catalogue differs from source'
        for name in ('manifest.json', 'FINANCEDATABASE-LICENSE.txt'):
            assert package.read('assets/catalogue/' + name) == (folder / name).read_bytes(), name
        assert 'assets/catalogue/assets.sqlite' not in package.namelist(), 'AAPT transformed the GZIP input'
    database = gzip.decompress(packed)
    assert len(database) == manifest['databaseBytes']
    assert hashlib.sha256(database).hexdigest() == manifest['sha256']
    assert database.startswith(b'SQLite format 3\x00')
    print(f"Verified packaged catalogue: {manifest['total']} listings, GZIP stream and licence preserved")


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('apk', type=Path)
    verify(parser.parse_args().apk)
