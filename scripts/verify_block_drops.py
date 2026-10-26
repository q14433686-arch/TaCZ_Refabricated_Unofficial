#!/usr/bin/env python3
"""离线校验工作台/标靶/雕像的方块掉落接线（26.2 线）。

为什么需要它（2026-10-26 实机报告）
----------------------------------
生存模式破坏 B 工作台（`gun_smith_table` / `workbench_b`）的 head、或 C 工作台
（`workbench_c`）的 upper 时会爆出**两个**工作台。根因：

1. 原版 `Block#updateOrDestroy` 在邻居 `updateShape` 返回空气时执行
   `level.destroyBlock(pos, dropResources=true, …)`（标志位无 `UPDATE_SUPPRESS_DROPS`），
   所以「被连带清掉的另一半」会自己走战利品表 —— 原版高花/床就是这个机制；
2. 我们的 `AbstractGunSmithTableBlock#playerWillDestroy` 又对非根半边手工
   `popResource` 了一份克隆 ⇒ 两条叠加成两份。

修复只删了第 2 条。这个脚本把「掉落只由战利品表负责」这条约定钉住，防止有人再把
手工掉落加回来（26.3 线 commit `021b546` 已经踩过一次同样的坑）。

检查项
------
A. `src/main/java/com/tacz/guns/block/` 下的方块类（不含 `entity/`）不得出现
   `popResource` / `Block.popResource` —— 方块的产出只允许由战利品表决定。
   （`StatueBlockEntity` 掉的是雕像上的**枪**，不在方块战利品表里，属正常，不在此目录下。）
B. 六份 `data/tacz/loot_table/blocks/*.json` 必须是 26.2 认的**旧 schema**
   （`conditions` / `functions` / `block_state_property`），并且：
   - 双方块（B/C/statue/target）的产出条件必须锁在**根**半边（`part=foot` / `half=lower`），
     与 Java 侧 `isRoot()` 一致；
   - 带 `BlockId` 的工作台必须有 `copy_custom_data` 函数。

用法
----
    python3 scripts/verify_block_drops.py            # 报告，恒退出 0
    python3 scripts/verify_block_drops.py --strict   # 有问题则退出 1
"""

from __future__ import annotations

import argparse
import json
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
BLOCK_DIR = ROOT / "src/main/java/com/tacz/guns/block"
LOOT_DIR = ROOT / "src/main/resources/data/tacz/loot_table/blocks"

# 双方块：loot 条件属性名 -> 必须是根半边的值
DOUBLE_BLOCK_LOOT = {
    "gun_smith_table.json": ("part", "foot"),
    "workbench_b.json": ("part", "foot"),
    "workbench_c.json": ("half", "lower"),
    "statue.json": ("half", "lower"),
    "target.json": ("half", "lower"),
}
# 需要从方块实体拷 BlockId 的物品（枪包工作台）
BLOCK_ID_LOOT = {"workbench_a.json", "workbench_b.json", "workbench_c.json"}

errors: list[str] = []
notes: list[str] = []


def check_no_manual_drops() -> None:
    """A. 方块类里不得手工 popResource。"""
    offenders = []
    for path in sorted(BLOCK_DIR.rglob("*.java")):
        if "entity" in path.relative_to(BLOCK_DIR).parts:
            continue
        text = path.read_text(encoding="utf-8")
        for lineno, line in enumerate(text.splitlines(), 1):
            stripped = line.strip()
            if stripped.startswith("//") or stripped.startswith("*"):
                continue
            if re.search(r"\bpopResource\s*\(", stripped):
                offenders.append(f"{path.relative_to(ROOT)}:{lineno}: {stripped}")
    if offenders:
        errors.append(
            "方块类里出现了手工掉落调用（掉落应只由战利品表负责）：\n  "
            + "\n  ".join(offenders)
        )


def iter_conditions(node, out: list[dict]) -> None:
    """收集 JSON 里所有 dict（旧 schema 的 condition 对象也在其中）。"""
    if isinstance(node, dict):
        out.append(node)
        for value in node.values():
            iter_conditions(value, out)
    elif isinstance(node, list):
        for value in node:
            iter_conditions(value, out)


def check_loot_tables() -> None:
    """B. 六份战利品表的 schema 与根半边条件。"""
    expected = set(DOUBLE_BLOCK_LOOT) | {"workbench_a.json"}
    present = {p.name for p in LOOT_DIR.glob("*.json")}
    missing = expected - present
    if missing:
        errors.append(f"缺少战利品表：{sorted(missing)}")

    for name in sorted(present & expected):
        path = LOOT_DIR / name
        try:
            data = json.loads(path.read_text(encoding="utf-8"))
        except json.JSONDecodeError as exc:
            errors.append(f"{name}: JSON 解析失败：{exc}")
            continue

        raw = path.read_text(encoding="utf-8")
        # 26.3 的新 schema 键（condition/modifier/match_block）在 26.2 会被静默忽略
        if re.search(r'"(modifier|random_sequence|match_block)"', raw):
            errors.append(
                f"{name}: 出现了 26.3 的战利品表 schema 键（modifier/random_sequence/match_block）。"
                " 26.2 仍用 conditions/functions/block_state_property，混用的后果是条件与函数被静默丢弃。"
            )

        dicts: list[dict] = []
        iter_conditions(data, dicts)

        if name in DOUBLE_BLOCK_LOOT:
            prop, root_value = DOUBLE_BLOCK_LOOT[name]
            matched = False
            for node in dicts:
                if node.get("condition") != "minecraft:block_state_property":
                    continue
                props = node.get("properties") or {}
                if props.get(prop) == root_value:
                    matched = True
                    break
            if not matched:
                errors.append(
                    f"{name}: 缺少 block_state_property 的 {prop}={root_value} 产出条件"
                    "（否则被连带清掉的另一半也会掉落 → 一次破坏掉两个）"
                )

        if name in BLOCK_ID_LOOT:
            has_copy = any(
                node.get("function") == "minecraft:copy_custom_data" for node in dicts
            )
            if not has_copy:
                errors.append(
                    f"{name}: 缺少 copy_custom_data 函数（掉落物会丢 BlockId → 紫黑无名工作台）"
                )


def check_is_root_matches_loot() -> None:
    """Java 的 isRoot 必须指向 loot 条件里的根半边。"""
    expectations = [
        (
            BLOCK_DIR / "GunSmithTableBlockB.java",
            r'Part\.FOOT',
            "GunSmithTableBlockB",
        ),
        (
            BLOCK_DIR / "GunSmithTableBlockC.java",
            r'TableHalf\.LOWER',
            "GunSmithTableBlockC",
        ),
    ]
    for path, needle, label in expectations:
        text = path.read_text(encoding="utf-8")
        match = re.search(r"public boolean isRoot\(BlockState blockState\)\s*\{(.*?)\n    \}", text, re.S)
        if not match:
            errors.append(f"{label}: 找不到 isRoot(...) 方法体，无法核对根半边")
            continue
        if not re.search(needle, match.group(1)):
            errors.append(
                f"{label}: isRoot() 不再是 {needle}，与战利品表的根半边条件不一致"
            )


def main() -> int:
    parser = argparse.ArgumentParser(description="校验方块掉落接线（离线静态检查）")
    parser.add_argument("--strict", action="store_true", help="有问题时退出 1")
    args = parser.parse_args()

    if not BLOCK_DIR.is_dir():
        print(f"[ERR] 找不到 {BLOCK_DIR}", file=sys.stderr)
        return 1 if args.strict else 0

    check_no_manual_drops()
    check_loot_tables()
    check_is_root_matches_loot()

    print("掉落接线检查：")
    print("  A. 方块类无手工 popResource —— %s" % ("FAIL" if errors else "OK"))
    print("  B. 六份战利品表 schema + 根半边条件 —— %s" % ("见下" if errors else "OK"))
    print("  C. isRoot() 与 loot 根半边一致 —— %s" % ("见下" if errors else "OK"))
    for note in notes:
        print(f"  · {note}")
    for error in errors:
        print(f"[FAIL] {error}")

    if not errors:
        print("全部通过。注意：这只是静态检查，不替代编译与实机掉落测试。")
        return 0
    return 1 if args.strict else 0


if __name__ == "__main__":
    raise SystemExit(main())
