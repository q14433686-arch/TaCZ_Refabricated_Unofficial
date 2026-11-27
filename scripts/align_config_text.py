#!/usr/bin/env python3
"""Compare every ForgeConfigSpec key with upstream 1.2.0: comment text, default/range literal and order.

Read-only. Usage: python3 scripts/align_config_text.py [--upstream REF] [--local REF|WORKTREE]
Exit status 1 when a key's comment, default or range differs, or a key is missing.
Keys listed in KNOWN_ABSENT are reported but not counted as failures (each has a recorded reason).
"""
import argparse
import re
import subprocess
import sys

UPSTREAM = '356bdf9d48f2225bac6ceb56adc5576a418517a8'
CONFIG_ROOT = 'src/main/java/com/tacz/guns/config/'
KNOWN_ABSENT = {
    'EnableSableCompat': 'no Sable / Create Aeronautics integration in this port',
    'EnableExperimentalDisplayGunVbo': 'see docs/lineage/UPSTREAM_120_ALIGNMENT_12111.md (VBO)',
    'EnableStaticItemGunVbo': 'see docs/lineage/UPSTREAM_120_ALIGNMENT_12111.md (VBO)',
}
# Upstream text that misdescribes behaviour shared by upstream and this port. Value: reason.
KNOWN_TEXT_DEVIATION = {
    'render.HitParticleEnable': 'both lines spawn fragments before the bullet-hole decal check '
                                '(upstream HitBlockEffectHandler, local BulletHoleParticle.Provider)',
}
DEFINE = re.compile(r'\.(define\w*)\(\s*"(\w+)"\s*,\s*([^;]*?)\)\s*;', re.S)
COMMENT = re.compile(r'comment\(((?:\s*"(?:[^"\\]|\\.)*"\s*(?:,|\+)?)+)\)', re.S)


def git(*a):
    return subprocess.run(['git', *a], capture_output=True, text=True).stdout


def files(ref):
    if ref == 'WORKTREE':
        out = subprocess.run(['git', 'ls-files', CONFIG_ROOT], capture_output=True, text=True).stdout.split()
    else:
        out = git('ls-tree', '-r', '--name-only', ref, '--', CONFIG_ROOT).split()
    return [p for p in out if p.endswith('.java')]


def read(ref, path):
    if ref == 'WORKTREE':
        with open(path, encoding='utf-8') as f:
            return f.read()
    return git('show', f'{ref}:{path}')


def norm_default(text):
    text = re.sub(r'\s+', ' ', text)
    text = re.sub(r',\s*(?:value|o|v)\s*->.*$', '', text)          # element validators
    text = re.sub(r',\s*\w+::\w+\s*$', '', text)                    # method-reference validators
    text = text.replace('Lists.newArrayList()', '[]').replace('ResourceLocation', 'Identifier')
    text = re.sub(r"\(double\)\s*", "", text)                   # widening casts do not change a default
    return text.strip()


def keys(src):
    src = re.sub(r'//[^\n]*', '', src)
    out, pending, push = {}, [], []
    tokens = sorted([(m.start(), 'c', m) for m in COMMENT.finditer(src)] +
                    [(m.start(), 'd', m) for m in DEFINE.finditer(src)] +
                    [(m.start(), 'p', m) for m in re.finditer(r'\.push\(\s*"(\w+)"\s*\)', src)] +
                    [(m.start(), 'o', m) for m in re.finditer(r'\.pop\(\s*\)', src)], key=lambda t: t[0])
    for _, kind, m in tokens:
        if kind == 'c':
            pending.append(''.join(re.findall(r'"((?:[^"\\]|\\.)*)"', m.group(1))) if '+' in m.group(1)
                           else '\n'.join(re.findall(r'"((?:[^"\\]|\\.)*)"', m.group(1))))
        elif kind == 'p':
            push.append(m.group(1))
        elif kind == 'o':
            push and push.pop()
        else:
            out['.'.join(push + [m.group(2)])] = {'comment': '\n'.join(pending).replace('\\n', '\n'),
                                                  'default': norm_default(m.group(3)), 'order': len(out)}
            pending = []
    return out


def collect(ref):
    merged = {}
    for p in files(ref):
        for k, v in keys(read(ref, p)).items():
            merged[(p.rsplit('/', 1)[1], k)] = v
    return merged


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument('--upstream', default=UPSTREAM)
    ap.add_argument('--local', default='WORKTREE')
    a = ap.parse_args()
    up, lo = collect(a.upstream), collect(a.local)
    lo_by_key = {k: (f, v) for (f, k), v in lo.items()}
    bad = 0
    for (f, k), u in sorted(up.items()):
        leaf = k.rsplit('.', 1)[-1]
        if k not in lo_by_key:
            if leaf in KNOWN_ABSENT:
                print(f'ABSENT-RECORDED\t{f}\t{k}\t{KNOWN_ABSENT[leaf]}')
            else:
                bad += 1
                print(f'MISSING\t{f}\t{k}')
            continue
        lf, l = lo_by_key[k]
        for field in ('comment', 'default'):
            if field == 'comment' and k in KNOWN_TEXT_DEVIATION and u[field] != l[field]:
                print(f'DEVIATION-RECORDED\t{f}\t{k}\t{KNOWN_TEXT_DEVIATION[k]}')
                continue
            if u[field] != l[field]:
                bad += 1
                print(f'{field.upper()}\t{f}\t{k}\n    upstream: {u[field]!r}\n    local:    {l[field]!r}')
    local_only = sorted(k for k in lo_by_key if k not in {k for _, k in up})
    print(f'# upstream keys {len(up)}, local keys {len(lo)}, differences {bad}, local-only {len(local_only)}')
    for k in local_only:
        print(f'LOCAL-ONLY\t{lo_by_key[k][0]}\t{k}')
    sys.exit(1 if bad else 0)


if __name__ == '__main__':
    main()
