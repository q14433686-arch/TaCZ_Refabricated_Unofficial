#!/usr/bin/env python3
"""Verify the 26.1.2 effective pack, not just the hand-written resources directory."""
import argparse
from collections import Counter
import hashlib
from pathlib import Path
from zipfile import ZipFile

ROOT = Path(__file__).resolve().parents[1]
PREFIX = 'assets/tacz/custom/tacz_default_gun/'
SOURCES = ROOT / 'src/main/resources' / PREFIX
BUNDLE = ROOT / 'resources/tacz-26.1.2-source-and-resource-bundle.jar'


def pack_entries(archive):
    names = [name[len(PREFIX):] for name in archive.namelist() if name.startswith(PREFIX) and not name.endswith('/')]
    duplicates = [name for name, count in Counter(names).items() if count != 1]
    if duplicates:
        raise ValueError(f'Duplicate pack files: {duplicates}')
    return set(names)


def verify(names, read, manifest):
    if names != manifest.keys():
        raise ValueError(f'Pack paths differ: {sorted(names ^ manifest.keys())}')
    for name, expected in manifest.items():
        if hashlib.sha256(read(name)).hexdigest() != expected:
            raise ValueError(f'Wrong winning bytes: {name}')


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--jar', type=Path, help='Also audit the actual distributable after processResources')
    args = parser.parse_args()
    rows = [line.split('\t') for line in (ROOT / 'DEFAULT_PACK_120.tsv').read_text().splitlines() if not line.startswith('#')]
    manifest = {name: digest for name, digest, blob, rule, source in rows}
    if len(rows) != len(manifest):
        raise ValueError('Duplicate destination in manifest')
    retired = set((ROOT / 'resources/retired-default-pack-paths.txt').read_text().splitlines())
    overrides = {p.relative_to(SOURCES).as_posix() for p in SOURCES.rglob('*') if p.is_file()}
    with ZipFile(BUNDLE) as bundle:
        fallback = pack_entries(bundle)
        if retired != fallback - manifest.keys() or retired & overrides:
            raise ValueError('Retirement list does not match obsolete bundle contents')
        verify((fallback - retired) | overrides, lambda n: (SOURCES / n).read_bytes() if n in overrides else bundle.read(PREFIX + n), manifest)
    if args.jar:
        with ZipFile(args.jar) as product:
            verify(pack_entries(product), lambda n: product.read(PREFIX + n), manifest)
    print(f'{len(manifest)} verified pack files; {len(overrides)} source overrides, {len(retired)} retired bundle paths.' + (' JAR matches.' if args.jar else ''))


if __name__ == '__main__':
    main()
