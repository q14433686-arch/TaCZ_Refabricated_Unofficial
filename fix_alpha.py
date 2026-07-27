#!/usr/bin/env python3
"""
26.2 修复：GuiGraphicsExtractor#text / #centeredText 会静默丢弃 alpha==0 的文本
（反编译确认：`if (ARGB.alpha(color) != 0) { ... }`）。

1.21.x 的 GuiGraphics#drawString 会在 alpha 为 0 时自动补成不透明，所以历史代码里
大量使用 6 位 RGB 字面量（如 0x777777）是安全的。26.2 移除了该兜底，这些文本
全部变成完全透明 —— 表现为"物品说明文字缺失/tooltip 只有空框"。

本脚本把 text()/centeredText() 调用里作为 color 实参的 6 位十六进制字面量
统一补上 0xFF 前缀，语义与 1.21.x 的隐式补全完全一致。
"""
import io, os, re

ROOT = "/home/user/repo/src/main/java"

# 仅匹配 .text( / .centeredText( 调用中的 6 位色值实参（前面是逗号+空格，后面是 , 或 )）
CALL = re.compile(r'\.(text|centeredText)\s*\(')
HEX6 = re.compile(r'(?<![0-9a-fA-FxX])0x([0-9a-fA-F]{6})(?![0-9a-fA-F])')

def split_args(s, start):
    """从 '(' 后一位开始，返回参数区间 [start, end) 与结束下标（匹配到对应右括号）。"""
    depth, i, n = 1, start, len(s)
    while i < n and depth:
        c = s[i]
        if c in '([':  depth += 1
        elif c in ')]': depth -= 1
        elif c == '"':                      # 跳过字符串字面量
            i += 1
            while i < n and s[i] != '"':
                i += 2 if s[i] == '\\' else 1
        i += 1
    return start, i - 1

total = 0
touched = []
for dirpath, _, files in os.walk(ROOT):
    for fn in files:
        if not fn.endswith(".java"):
            continue
        p = os.path.join(dirpath, fn)
        src = io.open(p, encoding="utf-8").read()
        out, pos, cnt = [], 0, 0
        for m in CALL.finditer(src):
            if m.start() < pos:
                continue
            a, b = split_args(src, m.end())
            args = src[a:b]
            new_args, k = HEX6.subn(lambda x: "0xFF" + x.group(1), args)
            if k:
                out.append(src[pos:a]); out.append(new_args)
                pos = b
                cnt += k
        if cnt:
            out.append(src[pos:])
            io.open(p, "w", encoding="utf-8").write("".join(out))
            touched.append((p.replace(ROOT + "/", ""), cnt))
            total += cnt

for f, c in sorted(touched):
    print(f"  {c:>2}  {f}")
print(f"\ntotal color literals fixed: {total}")
