#!/usr/bin/env python3
"""Compare the effective built-in pack on committed 26.x refs to the pinned Fabric target.

Read-only; refs must already be available locally. Models the source layouts used by the
refs in the 2026-11-18 audit, not arbitrary Gradle builds or a player's installed packs.
"""
import argparse
from collections import Counter
from hashlib import sha1
from io import BytesIO
from pathlib import Path
import subprocess
from zipfile import ZipFile

from audit_upstream_120 import ROOT, git, tree
from port_default_pack_120 import REL, effective_paths

PACK = REL.as_posix() + '/'
ZIP_PACK = REL.relative_to('src/main/resources').as_posix() + '/'
BUNDLE = 'resources/tacz-26.1.2-source-and-resource-bundle.jar'


def local_pack(ref):
    files = tree(ROOT, ref)
    source = {p.removeprefix(PACK): blob for p, blob in files.items() if p.startswith(PACK)}
    fallback = {}
    if BUNDLE in files:
        build = git(ROOT, 'show', f'{ref}:build.gradle')
        if BUNDLE not in build or 'from(zipTree(bundledSourcesAndResources))' not in build:
            raise ValueError(f'{ref}: unexpected fallback layout; check processResources before counting')
        raw = subprocess.check_output(['git', '-C', ROOT, 'cat-file', 'blob', files[BUNDLE]])
        with ZipFile(BytesIO(raw)) as archive:
            for entry in archive.infolist():
                if entry.is_dir() or not entry.filename.startswith(ZIP_PACK):
                    continue
                # Compare uncompressed contents, not the ZIP CRC or timestamp, with Git blob IDs.
                digest = sha1(f'blob {entry.file_size}\0'.encode())
                digest.update(archive.read(entry))
                fallback[entry.filename.removeprefix(ZIP_PACK)] = digest.hexdigest()
    if not source and not fallback:
        raise ValueError(f'{ref}: no built-in pack in the audited layouts')
    return fallback | source, len(source), len(fallback)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('upstream', type=Path, help='Git object store containing the pinned upstream commit')
    parser.add_argument('refs', nargs='+', help='local repository commits to inspect (no checkout)')
    parser.add_argument('--paths', action='store_true', help='list nonidentical destinations instead of totals')
    args = parser.parse_args()
    target = {p: blob for p, (_, blob) in effective_paths(args.upstream).items()}
    index_groups = ('guns', 'attachments', 'ammo', 'slot_adapters', 'blocks')
    if args.paths:
        print('commit', 'relation', 'destination', sep='\t')
    else:
        print('commit', 'explicit', 'bundle', 'effective', 'shared', 'same_blob', 'missing', 'extra',
              *index_groups, sep='\t')
    for ref in args.refs:
        commit = git(ROOT, 'rev-parse', '--verify', ref + '^{commit}').strip()
        local, explicit, bundled = local_pack(commit)
        shared = target.keys() & local.keys()
        changed = {p for p in shared if local[p] != target[p]}
        missing, extra = target.keys() - local.keys(), local.keys() - target.keys()
        if args.paths:
            for label, paths in [('missing', missing), ('different-blob', changed), ('local-only', extra)]:
                for p in sorted(paths):
                    print(commit, label, p, sep='\t')
        else:
            counts = Counter(p.split('/')[3] for p in local if p.startswith('data/tacz/index/') and p.endswith('.json'))
            print(commit, explicit, bundled, len(local), len(shared), len(shared) - len(changed),
                  len(missing), len(extra), *(counts[group] for group in index_groups), sep='\t')


if __name__ == '__main__':
    main()
