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
B. `data/tacz/loot_table/blocks/*.json` 必须是 26.2 认的**旧 schema**
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
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
BLOCK_DIR = Path("src/main/java/com/tacz/guns/block")
LOOT_DIR = Path("src/main/resources/data/tacz/loot_table/blocks")
ROOT_STATES = {
    "gun_smith_table": ("part", "foot"),
    "workbench_b": ("part", "foot"),
    "workbench_c": ("half", "lower"),
    "statue": ("half", "lower"),
    "target": ("half", "lower"),
}
STORAGE = {"ammo_storage", "ammo_storage_gold", "ammo_storage_diamond"}
RACKS = {"gun_display_case", "gun_pegboard", "glow_gun_pegboard"}
WORKBENCHES = {"workbench_a", "workbench_b", "workbench_c"}


def java_code(path: Path) -> str:
    text = path.read_text(encoding="utf-8")
    return re.sub(r"/\*.*?\*/|//[^\n]*", "", text, flags=re.S)


def method_body(text: str, name: str) -> str:
    match = re.search(r"\b" + re.escape(name) + r"\s*\([^)]*\)\s*\{", text)
    if not match:
        return ""
    start = match.end()
    depth = 1
    for pos in range(start, len(text)):
        if text[pos] == "{":
            depth += 1
        elif text[pos] == "}":
            depth -= 1
            if depth == 0:
                return text[start:pos]
    return ""


def walk(node):
    if isinstance(node, dict):
        yield node
        for child in node.values():
            yield from walk(child)
    elif isinstance(node, list):
        for child in node:
            yield from walk(child)


def check(root: Path) -> list[str]:
    errors: list[str] = []
    block_dir = root / BLOCK_DIR
    loot_dir = root / LOOT_DIR
    if not block_dir.is_dir() or not loot_dir.is_dir():
        return ["missing block source or loot-table directory"]

    for path in sorted(block_dir.glob("*.java")):
        if re.search(r"\bpopResource\s*\(", java_code(path)):
            errors.append(f"{path.name}: block class manually spawns drops")

    for name in sorted(set(ROOT_STATES) | WORKBENCHES | STORAGE | RACKS):
        path = loot_dir / f"{name}.json"
        try:
            table = json.loads(path.read_text(encoding="utf-8"))
        except (OSError, json.JSONDecodeError) as exc:
            errors.append(f"{path.name}: cannot read loot table: {exc}")
            continue
        # A valid-looking condition/function under an ignored 26.3 key is NOT applied.
        if any({"modifier", "match_block"} & node.keys() or isinstance(node.get("condition"), dict)
               or node.get("type") == "minecraft:match_block" for node in walk(table)):
            errors.append(f"{path.name}: incompatible loot schema for 26.2")
        entries = [(pool, entry) for pool in table.get("pools", []) for entry in pool.get("entries", [])
                   if entry.get("type") == "minecraft:item" and entry.get("name") == f"tacz:{name}"]
        if len(entries) != 1:
            errors.append(f"{path.name}: expected exactly one item entry, got {len(entries)}")
            continue
        pool, entry = entries[0]
        if name in ROOT_STATES:
            prop, value = ROOT_STATES[name]
            if not any(c.get("condition") == "minecraft:block_state_property" and c.get("block") == f"tacz:{name}"
                       and c.get("properties", {}).get(prop) == value for c in entry.get("conditions", [])):
                errors.append(f"{path.name}: missing {prop}={value} condition on the item entry")
        if name in WORKBENCHES | STORAGE:
            key = "BlockId" if name in WORKBENCHES else "Entries"
            functions = pool.get("functions", []) + entry.get("functions", [])
            if not any(fn.get("function") == "minecraft:copy_custom_data" and fn.get("source") == "block_entity"
                       and {"source": key, "target": key, "op": "replace"} in fn.get("ops", []) for fn in functions):
                errors.append(f"{path.name}: missing effective copy_custom_data for {key}")

    for filename, root_part, broken_part, other_part in (
        ("GunSmithTableBlockB.java", "BedPart.FOOT", "BedPart.HEAD", "BedPart.FOOT"),
        ("GunSmithTableBlockC.java", "TableHalf.LOWER", "TableHalf.UPPER", "TableHalf.LOWER"),
    ):
        try:
            code = java_code(block_dir / filename)
        except OSError as exc:
            errors.append(f"{filename}: {exc}")
            continue
        if root_part not in method_body(code, "isRoot"):
            errors.append(f"{filename}: isRoot no longer matches the loot table ({root_part})")
        destroy = method_body(code, "playerWillDestroy")
        if not all(part in destroy for part in (
            "player.isCreative()", broken_part, other_part, "Block.UPDATE_SUPPRESS_DROPS"
        )):
            errors.append(f"{filename}: missing creative-mode companion drop suppression")
        # Merely mentioning both parts is insufficient: check which half is being broken.
        if not re.search(r"\b(?:bedPart|half)\s*==\s*" + re.escape(broken_part), destroy):
            errors.append(f"{filename}: creative suppression must trigger when breaking {broken_part}")
        if not re.search(r"blockstate\.getValue\([^)]*\)\s*==\s*" + re.escape(other_part), destroy):
            errors.append(f"{filename}: creative suppression must remove {other_part}")

    return errors


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--strict", action="store_true", help="return nonzero on a regression")
    parser.add_argument("--root", type=Path, default=ROOT, help=argparse.SUPPRESS)
    args = parser.parse_args()
    errors = check(args.root)
    for error in errors:
        print(f"[FAIL] {error}")
    if not errors:
        print("26.2 block drop wiring: OK (static checks only; in-game testing still required)")
    return int(bool(errors) and args.strict)


if __name__ == "__main__":
    raise SystemExit(main())
