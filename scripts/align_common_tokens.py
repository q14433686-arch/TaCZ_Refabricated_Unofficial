#!/usr/bin/env python3
"""Find partial ports inside files shared by upstream 1.21.1, 26.3 and this tree (read-only).

Line diffs between Minecraft lines are dominated by API renames, so this compares per-file
behaviour fingerprints instead: string literals, numeric literals and called method names
(comments, imports and annotations stripped). A token that BOTH baselines contain but this
tree lacks is the strongest signal of an incompletely ported change.

Usage: python3 scripts/align_common_tokens.py [--upstream REF] [--r263 REF] [--local REF]
                                               [--only both|upstream|delta] [--min N]
--only delta: for every file upstream changed between OLD_UPSTREAM and --upstream, take the tokens
that change added and removed; report files where this tree lacks added tokens while still holding
removed ones (i.e. it still sits at the old upstream behaviour).
Prints `file<TAB>mode<TAB>count<TAB>tokens` sorted by count.
"""
import argparse
import re
import subprocess

UPSTREAM = '356bdf9d48f2225bac6ceb56adc5576a418517a8'
OLD_UPSTREAM = 'd2903554da039d2355920953a81447784a3f2be2'
# API spellings that differ only because of Minecraft/mapping versions.
RENAMES = {
    'ResourceLocation': 'Identifier', 'fromNamespaceAndPath': 'fromNamespaceAndPath',
    'withDefaultNamespace': 'withDefaultNamespace', 'writeResourceLocation': 'writeIdentifier',
    'readResourceLocation': 'readIdentifier', 'getCommandSenderWorld': 'level',
}
NOISE = {'get', 'set', 'of', 'add', 'put', 'size', 'equals', 'hashCode', 'toString', 'isEmpty',
         'copy', 'apply', 'accept', 'test', 'run', 'call', 'getId', 'build', 'create', 'value',
         'stream', 'map', 'filter', 'collect', 'orElse', 'ifPresent', 'isPresent', 'append',
         'length', 'contains', 'remove', 'clear', 'iterator', 'next', 'hasNext', 'format'}


def git(*a):
    return subprocess.run(['git', *a], capture_output=True, text=True).stdout


def java_files(ref):
    return {p for p in git('ls-tree', '-r', '--name-only', ref, '--', 'src/main/java').split() if p.endswith('.java')}


def tokens(src):
    src = re.sub(r'/\*.*?\*/', ' ', src, flags=re.S)
    src = re.sub(r'//[^\n]*', ' ', src)
    src = '\n'.join(l for l in src.splitlines() if not l.strip().startswith(('import ', 'package ', '@')))
    out = set()
    for s in re.findall(r'"((?:[^"\\\n]|\\.)*)"', src):
        if len(s) >= 2 and not re.fullmatch(r'[\s\W]*', s):
            out.add('S:' + s)
    body = re.sub(r'"((?:[^"\\\n]|\\.)*)"', '""', src)
    for n in re.findall(r'(?<![\w.])(\d+\.\d+|\d{2,})[fFdDL]?\b', body):
        out.add('N:' + n.rstrip('0').rstrip('.') if '.' in n else 'N:' + n)
    for m in re.findall(r'\.(\w+)\s*\(', body):
        m = RENAMES.get(m, m)
        if m not in NOISE and len(m) > 3:
            out.add('M:' + m)
    return out


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument('--upstream', default=UPSTREAM)
    ap.add_argument('--r263', default='origin/26.3')
    ap.add_argument('--local', default='HEAD')
    ap.add_argument('--only', choices=['both', 'upstream', 'delta'], default='both')
    ap.add_argument('--min', type=int, default=1)
    a = ap.parse_args()
    rows = []
    if a.only == 'delta':
        changed = git('diff', '--name-only', '--diff-filter=M', OLD_UPSTREAM, a.upstream, '--', 'src/main/java').split()
        local = java_files(a.local)
        for p in changed:
            if p not in local:
                continue
            o, u, l = (tokens(git('show', f'{r}:{p}')) for r in (OLD_UPSTREAM, a.upstream, a.local))
            added, removed = (u - o) - l, (o - u) & l
            if added and removed and len(added) + len(removed) >= a.min:
                rows.append((len(added) + len(removed), p, ['+' + t for t in sorted(added)] + ['-' + t for t in sorted(removed)]))
        rows.sort(key=lambda r: (-r[0], r[1]))
        for n, p, miss in rows:
            print(p.replace('src/main/java/', ''), 'delta', n, ' | '.join(miss), sep='\t')
        print(f'# {len(changed)} upstream-modified files; {len(rows)} still hold removed tokens and lack added ones')
        return
    common = java_files(a.upstream) & java_files(a.r263) & java_files(a.local)
    for p in sorted(common):
        u, t, l = (tokens(git('show', f'{r}:{p}')) for r in (a.upstream, a.r263, a.local))
        miss = (u & t) - l if a.only == 'both' else (u - t) - l
        if len(miss) >= a.min:
            rows.append((len(miss), p, sorted(miss)))
    rows.sort(key=lambda r: (-r[0], r[1]))
    for n, p, miss in rows:
        print(p.replace('src/main/java/', ''), f'missing-{a.only}', n, ' | '.join(miss), sep='\t')
    print(f'# {len(common)} common files; {len(rows)} with tokens missing ({a.only})')


if __name__ == '__main__':
    main()
