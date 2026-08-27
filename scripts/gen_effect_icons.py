#!/usr/bin/env python3
"""生成 LRTactical 状态效果图标（纯 stdlib，不依赖 Pillow）。

为什么要有这个脚本
------------------
`assets/lrtactical/textures/mob_effect/<id>.png` 缺文件时，效果图标会显示成
<b>紫黑块</b>（missing texture）。26.2 分支只有 `deafened.png`，`blinded.png` 一直缺失；
26.1.2 / 1.21.11 / 姊妹仓 TaCZ_Renovated 两张都没有。

图标<b>不</b>取自原作 LRTactical（其美术为 All Rights Reserved，本仓不分发、不二次创作），
而是用本脚本按同一套配色现画，风格与已有的 `deafened.png`（18×18 RGBA 手绘耳朵）保持一致。
需要调整或给别的分支补文件时，直接跑本脚本即可产出逐字节可复现的 PNG。

用法
----
    python3 scripts/gen_effect_icons.py            # 只补缺失的（默认）
    python3 scripts/gen_effect_icons.py --force    # 覆盖已存在的
    python3 scripts/gen_effect_icons.py --print blinded   # 只打印 ASCII 预览，不写文件
"""

from __future__ import annotations

import argparse
import math
import pathlib
import struct
import zlib

OUT_DIR = pathlib.Path(__file__).resolve().parent.parent / \
    "src/main/resources/assets/lrtactical/textures/mob_effect"

SIZE = 18  # 与既有 deafened.png 一致（18×18 RGBA）

# 与 deafened.png 同一套暖棕配色，让两个图标看起来是一组
OUTLINE = (52, 31, 20, 255)
MID = (126, 76, 48, 255)
LIGHT = (214, 156, 108, 255)
PALE = (242, 202, 160, 255)
DARK = (36, 20, 12, 255)
SLASH = (150, 62, 50, 255)


def _png(pixels: list[list[tuple[int, int, int, int]]]) -> bytes:
    """把 RGBA 像素阵列编码成 PNG（filter 0，zlib 压缩）。"""
    w, h = len(pixels[0]), len(pixels)
    raw = b"".join(b"\x00" + b"".join(struct.pack("4B", *px) for px in row) for row in pixels)

    def chunk(tag: bytes, payload: bytes) -> bytes:
        return (struct.pack(">I", len(payload)) + tag + payload
                + struct.pack(">I", zlib.crc32(tag + payload) & 0xFFFFFFFF))

    return (b"\x89PNG\r\n\x1a\n"
            + chunk(b"IHDR", struct.pack(">IIBBBBB", w, h, 8, 6, 0, 0, 0))
            + chunk(b"IDAT", zlib.compress(raw, 9))
            + chunk(b"IEND", b""))


def _blank() -> list[list[tuple[int, int, int, int]]]:
    return [[(0, 0, 0, 0) for _ in range(SIZE)] for _ in range(SIZE)]


def blinded() -> list[list[tuple[int, int, int, int]]]:
    """闭上的眼睛 + 一道斜杠（「看不见」）。"""
    px = _blank()
    cx = cy = (SIZE - 1) / 2.0

    # 眼眶：杏仁形，中间高两端尖
    for x in range(SIZE):
        t = (x - 2) / (SIZE - 5.0)
        if not 0.0 <= t <= 1.0:
            continue
        half = 4.4 * math.sin(math.pi * t)
        top, bottom = cy - half, cy + half
        for y in range(SIZE):
            if top - 0.6 <= y <= bottom + 0.6:
                edge = (y <= top + 0.9) or (y >= bottom - 0.9) or half < 1.4
                px[y][x] = OUTLINE if edge else PALE

    # 虹膜与瞳孔（闭眼也留一点轮廓，读起来才像眼睛）
    for y in range(SIZE):
        for x in range(SIZE):
            d = math.hypot(x - cx, y - cy)
            if d <= 1.6:
                px[y][x] = DARK
            elif d <= 3.4 and px[y][x] != OUTLINE:
                px[y][x] = MID

    # 上下眼睑的阴影，增加厚度感
    for x in range(4, SIZE - 4):
        if px[6][x] == PALE:
            px[6][x] = LIGHT
        if px[11][x] == PALE:
            px[11][x] = LIGHT

    # 斜杠：左下 → 右上，2px 宽
    for y in range(SIZE):
        for x in range(SIZE):
            d = abs((x - 2) + (y - 15)) / math.sqrt(2)
            if d <= 0.8:
                px[y][x] = SLASH
    return px


ICONS = {"blinded": blinded}


def main() -> int:
    ap = argparse.ArgumentParser(description=__doc__,
                                 formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--force", action="store_true", help="覆盖已存在的 PNG")
    ap.add_argument("--print", dest="only", choices=sorted(ICONS), help="只打印预览，不写文件")
    args = ap.parse_args()

    for name, fn in ICONS.items():
        pixels = fn()
        if args.only == name:
            for row in pixels:
                print("".join("." if px[3] < 16 else ("#" if px == OUTLINE else
                                                      ("X" if px == SLASH else "o")) for px in row))
            continue
        OUT_DIR.mkdir(parents=True, exist_ok=True)
        path = OUT_DIR / f"{name}.png"
        if path.exists() and not args.force:
            print(f"skip（已存在，加 --force 覆盖）: {path}")
            continue
        path.write_bytes(_png(pixels))
        print(f"wrote {path} ({path.stat().st_size} bytes, {SIZE}x{SIZE} RGBA)")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
