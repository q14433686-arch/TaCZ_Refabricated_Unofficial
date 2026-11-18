#!/usr/bin/env python3
"""Full Git *path* inventory (unmodified files and native-only work included).

Writes TSV to stdout; reads committed blobs only. --local selects the version of BOTH
source and pack provenance. A matching blob is evidence of content, not of runtime use.
See audit_upstream_120.py --paths for the independent rename-aware delta view.
"""
import argparse
from collections import Counter
from hashlib import sha256
from pathlib import Path
import subprocess
import sys

from audit_upstream_120 import BASE, TARGET, ROOT, git, tree, group

NATIVE_BASE = 'cc990c3f784aa31c8fd7340f8cc158609c9bd16d'
PACK = 'src/main/resources/assets/tacz/custom/tacz_default_gun/'


def inventory(upstream, local_ref, out):
    old, target = (tree(upstream, rev) for rev in (BASE, TARGET))
    native, local = (tree(ROOT, rev) for rev in (NATIVE_BASE, local_ref))
    # Decode the *winning* overlay source, not just the spelling of a destination path.
    winners = {}
    for line in git(ROOT, 'show', f'{local_ref}:DEFAULT_PACK_120.tsv').splitlines():
        if line.startswith('#'):
            continue
        dest, digest, blob, rule, src = line.split('\t')
        path = PACK + (dest if src == '=' else src)
        assert target[path] == blob, path
        winners[path] = (PACK + dest, rule, digest)
    # Identify overridden originals using the same singular-directory convention as the importer.
    by_dest = {dest: src for src, (dest, _, _) in winners.items()}
    deltas = {}
    for line in git(upstream, 'diff', '--name-status', '-M50%', BASE, TARGET).splitlines():
        change, *paths = line.split('\t')
        deltas[paths[-1]] = (change, paths[0] if len(paths) == 2 else '=')
    counts = Counter()
    print(f'# old={BASE} target={TARGET} native={NATIVE_BASE} local={git(ROOT, "rev-parse", local_ref).strip()}', file=out)
    print('group\tpath\tupstream-delta\trename-from\told-blob\ttarget-blob\tnative-base-blob\tlocal-blob\tresolved-path\tresolved-blob\trelation', file=out)
    for p in sorted(old.keys() | target.keys() | native.keys() | local.keys()):
        dest, rule, digest = winners.get(p, (p, None, None))
        if rule is not None:
            if dest not in local:
                relation = 'missing-effective-file'
            else:
                matches = (local[dest] == target[p] if rule == 'source' else
                           sha256(subprocess.check_output(['git', '-C', ROOT, 'cat-file', 'blob', local[dest]])).hexdigest() == digest)
                relation = 'pack:' + rule if matches else 'different-from-pack-manifest'
        elif p.startswith(PACK) and p in target:
            dest = p.replace('/data/tacz/recipes/', '/data/tacz/recipe/').replace('/data/tacz/advancements/', '/data/tacz/advancement/')
            assert dest in by_dest and by_dest[dest].startswith(PACK + 'compat_1_21_1/'), p
            relation = 'pack:shadowed-by-component-overlay'
        elif p not in target:
            relation = ('port-only' if p not in old else 'retained-after-upstream-removal') if p in local else 'historical-only'
        elif p not in local:
            relation = 'no-same-path'
        else:
            relation = 'identical' if target[p] == local[p] else ('still-old-blob' if local[p] == old.get(p) else 'different')
        counts[relation] += 1
        print(group(p), p, *deltas.get(p, ('-', '-')), *(t.get(p, '-') for t in (old, target, native, local)), dest if dest != p else '=',
              local.get(dest, '-'), relation, sep='\t', file=out)
    print(f'Old {len(old)}, target {len(target)}, native base {len(native)}, local {len(local)}, union {sum(counts.values())}.', file=sys.stderr)
    print(dict(sorted(counts.items())), file=sys.stderr)


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('upstream', type=Path)
    parser.add_argument('--local', default='HEAD')
    args = parser.parse_args()
    inventory(args.upstream, args.local, sys.stdout)
