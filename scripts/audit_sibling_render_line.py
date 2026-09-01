#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""对照兄弟分支（26.1.2 渲染线）做"全量移植核对"——三层判据，任何一层单独用都会漏。

用法：
    python3 scripts/audit_sibling_render_line.py <theirs-ref> <base-ref>

为什么需要三层（2026-09-01 的实机教训，见 docs/lineage/AUDIT_2612_RENDER_42COMMITS_20260901.md）：
  * 第 1 层（逐 commit cherry-pick）只能保证"带独立方法/配置键的改动"不漏；
  * 第 2 层（终态代码行 diff）才能看见**嵌在既有方法体里的守卫**（`if (...) return;`）——
    我方漏掉的正是这种：825d2c5 与 95590b0 各有一条窄遍守卫，方法名两侧完全一样；
  * 第 3 层（新增符号全树搜索）用来兜"方法/键整块没搬"，同时点出"本线有意不搬"的项。
注释不参与比对：两边注释语言/密度不同，比了只会淹没有效信号。
"""
import re
import subprocess
import sys

SCOPE_PATHS = (
    'src/main/java/com/tacz/guns/client/render/scope/',
    'src/main/java/com/tacz/guns/compat/',
    'src/main/java/com/tacz/guns/mixin/client/',
    'src/main/java/com/tacz/guns/config/client/RenderConfig.java',
    'src/main/java/cn/sh1rocu/tacz/compat/meshloader/render/',
)
IGNORE = re.compile(r'^\s*(\*|/\*|//)|^\s*$')


def sh(*args, binary=False):
    return subprocess.run(list(args), capture_output=True, text=not binary, errors='replace').stdout


def strip_comments(text):
    return '\n'.join(l for l in text.split('\n') if not IGNORE.match(l))


def code_files(ref, base):
    out = sh('git', 'log', '--format=%H', '--no-merges', f'{base}..{ref}')
    files = set()
    for sha in out.split():
        for f in sh('git', 'show', '--pretty=format:', '--name-only', sha).split('\n'):
            if f.endswith('.java') and f.startswith(SCOPE_PATHS):
                files.add(f)
    return sorted(files)


def layer1_symbol_scan(ref, base):
    """每个 commit 新增的方法名/配置键，是否在我方全树存在。"""
    rows = []
    for line in sh('git', 'log', '--format=%H\t%s', '--no-merges', f'{base}..{ref}').strip().split('\n'):
        if not line.strip():
            continue
        sha, subj = line.split('\t', 1)
        patch = sh('git', 'show', '--pretty=format:', '--unified=0', sha)
        names = set()
        for l in patch.split('\n'):
            if not l.startswith('+') or l.startswith('+++'):
                continue
            m = re.search(r'(?:public|private|protected)\s+(?:static\s+)?[\w<>\[\],. ]+\s+(\w+)\s*\(', l)
            if m and m.group(1) not in {'if', 'for', 'while', 'return', 'catch', 'switch'}:
                names.add(m.group(1))
            names.update(re.findall(r'\b(?:SCOPE_PIP_|VOXY_|MESH_)\w+', l))
        if not names:
            continue
        missing = sorted(n for n in names
                         if subprocess.run(['grep', '-rq', '--include=*.java', '-w', n, 'src/main/java'],
                                           capture_output=True).returncode != 0)
        rows.append((sha[:7], subj[:72], missing))
    return rows


def layer2_state_diff(ref, files):
    """终态代码行 diff 的行数（0 = 两侧代码完全同形）。"""
    out = []
    for f in files:
        theirs = strip_comments(sh('git', 'show', f'{ref}:{f}'))
        try:
            ours = strip_comments(open(f, encoding='utf-8').read())
        except OSError:
            out.append((f, 'NOFILE'))
            continue
        import difflib, os, tempfile
        with tempfile.NamedTemporaryFile('w', delete=False, suffix='.t') as ft, \
             tempfile.NamedTemporaryFile('w', delete=False, suffix='.o') as fo:
            ft.write(theirs + '\n'); fo.write(ours + '\n')
            d = list(difflib.unified_diff(open(ft.name, encoding='utf-8').read().split('\n'),
                                          open(fo.name, encoding='utf-8').read().split('\n'),
                                          lineterm='', n=0))
            os.unlink(ft.name); os.unlink(fo.name)
        n = len([l for l in d if l.startswith(('+', '-')) and not l.startswith(('++', '--'))])
        out.append((f, n))
    return out


def layer3_method_inventory(ref, files):
    """他有而我方没有的方法名（比整文件 diff 更直接的"整块没搬"信号）。"""
    out = []
    for f in files:
        def meth(text):
            names = set()
            for l in text.split('\n'):
                if re.match(r'^\s*(public|private|protected)', l) and '(' in l:
                    names.add(re.sub(r'\(.*', '', l).split()[-1])
            return names
        t = meth(sh('git', 'show', f'{ref}:{f}'))
        try:
            o = meth(open(f, encoding='utf-8').read())
        except OSError:
            out.append((f, 'NOFILE'))
            continue
        out.append((f, sorted(t - o)))
    return out


def main():
    if len(sys.argv) != 3:
        print(__doc__)
        return 2
    ref, base = sys.argv[1], sys.argv[2]
    files = code_files(ref, base)
    print(f'## 范围 {base[:7]}..{ref[:7]}；触达渲染/compat/config 的文件 {len(files)} 个\n')
    print('### 层 1 · 每个 commit 新增但我方全树搜不到的符号')
    for sha, subj, missing in layer1_symbol_scan(ref, base):
        if missing:
            print(f'- {sha} {subj}\n  - 缺：{", ".join(missing)}')
    print('\n### 层 2 · 终态代码行 diff 规模（0 = 同形；非 0 需人工判"世代改写"还是"漏项"）')
    for f, n in sorted(layer2_state_diff(ref, files), key=lambda x: (x[1] == 'NOFILE', -(x[1] if x[1] != 'NOFILE' else 0))):
        print(f'- {n:>6}  {f}')
    print('\n### 层 3 · 他有而我方没有的方法')
    for f, miss in layer3_method_inventory(ref, files):
        if miss and miss != 'NOFILE':
            print(f'- {f}\n  - {", ".join(miss)}')
    return 0


if __name__ == '__main__':
    sys.exit(main())
