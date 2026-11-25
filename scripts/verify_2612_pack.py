#!/usr/bin/env python3
"""Verify the 26.1.2 effective pack, not just the hand-written resources directory."""
import argparse
from collections import Counter
import hashlib
from pathlib import Path
from zipfile import ZipFile
from tempfile import TemporaryDirectory

from default_pack_graph import graph, read, resolve

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


def graph_and_provenance(bundle, overrides, rows):
    # A source-only traversal silently loses the receiver's unchanged fallback files. Materialize
    # the exact winning bytes outside the checkout, never a generated replacement for the bundle.
    with TemporaryDirectory(prefix='tacz-2612-pack-') as temp:
        pack = Path(temp)
        for name, _, blob, rule, _ in rows:
            winner = (SOURCES / name).read_bytes() if name in overrides else bundle.read(PREFIX + name)
            if rule == 'source' and hashlib.sha1(f'blob {len(winner)}\0'.encode() + winner).hexdigest() != blob:
                raise ValueError(f'Verbatim import differs from upstream Git blob: {name}')
            dest = pack / name
            dest.parent.mkdir(parents=True, exist_ok=True)
            dest.write_bytes(winner)
        gaps = ROOT/'docs/investigations/DEFAULT_PACK_120_SOURCE_GAPS.tsv'
        expected = {line for line in gaps.read_text().splitlines() if line and not line.startswith('#')}
        # Preserve all 246 *source* exceptions verbatim. This receiver deliberately drops
        # the redundant Glock draw cue; its missing upstream sound is no longer requested.
        expected.remove('assets/minecraft/tacz_sounds/p24_pi_golf17_stockskel_raise.ogg\tassets/tacz/animations/glock_17.animation.json')
        actual = set(graph(pack))
        if actual != expected:
            raise ValueError('Unreviewed resource graph change: ' + '\n'.join(sorted(actual ^ expected)[:15]))
        counts = Counter(p.parent.name for p in (pack/'data/tacz/index').rglob('*.json'))
        if counts != {'guns': 70, 'ammo': 25, 'attachments': 126, 'slot_adapters': 5, 'blocks': 3}:
            raise ValueError(f'Wrong index counts: {counts}')
        for item in (pack/'data/tacz/advancement').glob('*.json'):
            parent = read(item).get('parent')
            if parent and not resolve(pack, 'data', 'advancement', parent, '.json').is_file():
                raise ValueError(f'Unknown advancement parent {parent}: {item.name}')
    for row in (ROOT/'RESOURCE_IMPORT_120.tsv').read_text().splitlines():
        if not row or row.startswith('#'):
            continue
        sha, name = row.split('\t')
        source = ROOT/'src/main/resources'/name
        data = source.read_bytes() if source.is_file() else bundle.read(name)
        if hashlib.sha256(data).hexdigest() != sha:
            raise ValueError(f'Outside-pack resource winner differs: {name}')
    print(f'{sum(counts.values())} indexes; {len(actual)} remaining inherited reference gaps (246 in source); outside-pack ledger matches.')


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
        graph_and_provenance(bundle, overrides, rows)
    if args.jar:
        with ZipFile(args.jar) as product:
            verify(pack_entries(product), lambda n: product.read(PREFIX + n), manifest)
    print(f'{len(manifest)} verified pack files; {len(overrides)} source overrides, {len(retired)} retired bundle paths.' + (' JAR matches.' if args.jar else ''))


if __name__ == '__main__':
    main()
