#!/usr/bin/env python3
"""Read-only three-tree inventory. Run with an upstream Git object store (bare is fine).

No fetch, checkout, text normalization or automatic conflict resolution. --paths emits
one TSV row per upstream delta (including renames); local-only integrations aren't deltas.
"""
import argparse
from collections import Counter
from pathlib import Path
import subprocess

BASE = 'd2903554da039d2355920953a81447784a3f2be2'
TARGET = '356bdf9d48f2225bac6ceb56adc5576a418517a8'
ROOT = Path(__file__).resolve().parents[1]


def git(repo, *args):
    return subprocess.check_output(['git', '-C', repo, *args], encoding='utf-8')


def tree(repo, ref):
    return dict(row.split('\t', 1)[::-1] for row in
                git(repo, 'ls-tree', '-r', '--format=%(objectname)%x09%(path)', ref).splitlines())


def group(path):
    if path.startswith('src/main/resources/assets/tacz/custom/tacz_default_gun/'):
        return 'default pack'
    if path.startswith('src/main/resources/'):
        return 'other resources / loader metadata'
    if path.endswith('.java'):
        return 'Java'
    return 'build / tools / docs'


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('upstream', type=Path)
    parser.add_argument('--local', default='HEAD', help='local commit, not working tree')
    parser.add_argument('--paths', action='store_true')
    args = parser.parse_args()
    old, new, local = tree(args.upstream, BASE), tree(args.upstream, TARGET), tree(ROOT, args.local)
    counts = Counter()
    if args.paths:
        print('group\tupstream\tlocal blob at destination\told path\tnew path')
    for line in git(args.upstream, 'diff', '--name-status', '-M50%', BASE, TARGET).splitlines():
        status, *paths = line.split('\t')
        src, dst = paths[0], paths[-1]
        here = local.get(dst)
        if here == new.get(dst):
            state = 'at target'  # includes already absent upstream deletions
        elif here is None:
            state = 'absent'
        elif here == old.get(src):
            state = 'at old'
        else:
            state = 'diverged'
        counts[group(dst), state] += 1
        if args.paths:
            print(group(dst), status, state, src, dst, sep='\t')
    if not args.paths:
        states = ('at target', 'at old', 'absent', 'diverged')
        print('local:', git(ROOT, 'rev-parse', args.local).strip())
        print('scope', *states, 'total', sep='\t')
        for name in sorted({k for k, _ in counts}):
            row = [counts[name, s] for s in states]
            print(name, *row, sum(row), sep='\t')


if __name__ == '__main__':
    main()
