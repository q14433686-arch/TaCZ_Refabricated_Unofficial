#!/usr/bin/env python3
"""离线校验工作台/标靶/雕像的方块掉落接线（1.21.11 线）。

来源：26.2 线 2026-10-26 的 `scripts/verify_block_drops.py`（其 commit `db62d25`），
按本线实际代码与 1.21.11 反编译源码适配；多出的 D 项是本线新增的。

为什么需要它（26.2 线 2026-10-25 玩家报告 issue #114「挖副碰撞箱掉两个」）
----------------------------------------------------------------------
生存模式破坏 B 工作台（`gun_smith_table` / `workbench_b`）的 head、或 C 工作台
（`workbench_c`）的 upper 时会爆出**两个**工作台。根因：

1. 原版 `Block#updateOrDestroy` 在邻居 `updateShape` 返回空气时执行
   `level.destroyBlock(pos, dropResources=(flags & UPDATE_SUPPRESS_DROPS)==0, …)`；
   而 `Level#setBlock` 向邻居传播标志位时用的是 `flags & -34`，会清掉
   `UPDATE_SUPPRESS_DROPS`，所以「被连带清掉的另一半」一定会自己走战利品表
   —— 原版高花/床就是这个机制（1.21.11 反编译源码核对）；
2. 我们的 `AbstractGunSmithTableBlock#playerWillDestroy` 又对非根半边手工
   `popResource` 了一份克隆 ⇒ 两条叠加成两份。

本线已于 2026-10-21 随 26.3 同病项删掉了第 2 条（`scripts/verify_263_sync_baseline.py`
也钉了「不得 popResource」）。这个脚本把「掉落只由战利品表负责」这条约定整体钉住，
并多钉一条创造模式的判据方向（D 项）。

检查项
------
A. `src/main/java/com/tacz/guns/block/` 下的方块类（不含 `entity/`）不得出现
   `popResource` / `Block.popResource` —— 方块的产出只允许由战利品表决定。
   （`StatueBlockEntity` 掉的是雕像上的**枪**，不在方块战利品表里，属正常，不在此目录下。）
B. 六份 `data/tacz/loot_table/blocks/*.json` 必须是本线认的**旧 schema**
   （`conditions` / `functions` / `block_state_property`），并且：
   - 双方块（B/C/statue/target）的产出条件必须锁在**根**半边（`part=foot` / `half=lower`），
     与 Java 侧 `isRoot()` 一致；
   - 带 `BlockId` 的工作台必须有 `copy_custom_data` 函数。
C. `GunSmithTableBlockB/C#isRoot()` 必须指向 loot 条件里的根半边。
D. 创造模式的「另一半不再掉落」判据（`GunSmithTableBlockB/C#playerWillDestroy`）：
   守卫必须挂在**非根半边**上，被静默拿掉的邻居必须是**根半边**（会产出的那半）。
   玩家挖掉一半后，另一半经 `updateShape→AIR→destroyBlock(drop=true)` 自己走战利品表，
   所以创造模式要在玩家那一半被移除之前，先把「会产出的根半边」用 `setBlock` 静默拿掉。
   判据写反（照抄原版 `BedBlock` 却没跟着我们的 loot 条件翻转 —— 原版床的 loot 条件在 head、
   我们的在 foot）会让创造模式挖任意一半都漏出一个工作台；这个 bug 在本线与 26.2 线都存在过，
   编译与运行都不报错。

用法
----
    python3 scripts/verify_block_drops.py            # 报告，恒退出 0
    python3 scripts/verify_block_drops.py --strict   # 有问题则退出 1

只是静态检查，不替代编译与实机掉落测试。
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
# 每一项各自的失败数，用来在汇总里分别打印 OK / FAIL
failed: dict[str, int] = {"A": 0, "B": 0, "C": 0, "D": 0}


def fail(item: str, message: str) -> None:
    failed[item] += 1
    errors.append(f"[{item}] {message}")


def strip_comments(text: str) -> str:
    text = re.sub(r"/\*.*?\*/", "", text, flags=re.S)
    return re.sub(r"//[^\n]*", "", text)


def method_body(text: str, header: str) -> str | None:
    """粗略取出以 header 开头的方法体（大括号配平）。"""
    idx = text.find(header)
    if idx < 0:
        return None
    start = text.find("{", idx)
    if start < 0:
        return None
    depth = 0
    for i in range(start, len(text)):
        if text[i] == "{":
            depth += 1
        elif text[i] == "}":
            depth -= 1
            if depth == 0:
                return text[start : i + 1]
    return None


def check_no_manual_drops() -> None:
    """A. 方块类里不得手工 popResource。"""
    offenders = []
    for path in sorted(BLOCK_DIR.rglob("*.java")):
        if "entity" in path.relative_to(BLOCK_DIR).parts:
            continue
        text = path.read_text(encoding="utf-8")
        for lineno, line in enumerate(text.splitlines(), 1):
            stripped = line.strip()
            if stripped.startswith("//") or stripped.startswith("*") or stripped.startswith("/*"):
                continue
            if re.search(r"\bpopResource\s*\(", stripped):
                offenders.append(f"{path.relative_to(ROOT)}:{lineno}: {stripped}")
    if offenders:
        fail(
            "A",
            "方块类里出现了手工掉落调用（掉落应只由战利品表负责）：\n    " + "\n    ".join(offenders),
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
        fail("B", f"缺少战利品表：{sorted(missing)}")

    for name in sorted(present & expected):
        path = LOOT_DIR / name
        try:
            data = json.loads(path.read_text(encoding="utf-8"))
        except json.JSONDecodeError as exc:
            fail("B", f"{name}: JSON 解析失败：{exc}")
            continue

        raw = path.read_text(encoding="utf-8")
        # 26.3 的新 schema 键（condition/modifier/match_block）在本线会被静默忽略
        # （RecordCodecBuilder 忽略未知键 ⇒ 产出条件与 copy_custom_data 全丢）。
        if re.search(r'"(modifier|random_sequence|match_block)"', raw):
            fail(
                "B",
                f"{name}: 出现了 26.3 的战利品表 schema 键（modifier/random_sequence/match_block）。"
                " 本线仍用 conditions/functions/block_state_property，混用的后果是条件与函数被静默丢弃。",
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
                fail(
                    "B",
                    f"{name}: 缺少 block_state_property 的 {prop}={root_value} 产出条件"
                    "（否则被连带清掉的另一半也会掉落 → 一次破坏掉两个）",
                )

        if name in BLOCK_ID_LOOT:
            has_copy = any(node.get("function") == "minecraft:copy_custom_data" for node in dicts)
            if not has_copy:
                fail("B", f"{name}: 缺少 copy_custom_data 函数（掉落物会丢 BlockId → 紫黑无名工作台）")


def check_is_root_matches_loot() -> None:
    """C. Java 的 isRoot 必须指向 loot 条件里的根半边。"""
    expectations = [
        (BLOCK_DIR / "GunSmithTableBlockB.java", r"Part\.FOOT", "GunSmithTableBlockB"),
        (BLOCK_DIR / "GunSmithTableBlockC.java", r"TableHalf\.LOWER", "GunSmithTableBlockC"),
    ]
    for path, needle, label in expectations:
        text = path.read_text(encoding="utf-8")
        match = re.search(r"public boolean isRoot\(BlockState blockState\)\s*\{(.*?)\n    \}", text, re.S)
        if not match:
            fail("C", f"{label}: 找不到 isRoot(...) 方法体，无法核对根半边")
            continue
        if not re.search(needle, match.group(1)):
            fail("C", f"{label}: isRoot() 不再是 {needle}，与战利品表的根半边条件不一致")


def check_creative_suppression_direction() -> None:
    """D. 创造模式的抑制判据：守卫挂在非根半边，被静默拿掉的是根半边。"""
    cases = [
        # (文件, 守卫 `if (var == Enum.X)`, 邻居核对 `getValue(PROP) == Enum.Y)`, loot 里的根半边值, 标签)
        (
            BLOCK_DIR / "GunSmithTableBlockB.java",
            r"if\s*\(\s*bedPart\s*==\s*BedPart\.(\w+)\s*\)",
            r"getValue\(\s*PART\s*\)\s*==\s*BedPart\.(\w+)\s*\)",
            DOUBLE_BLOCK_LOOT["workbench_b.json"][1],
            "GunSmithTableBlockB",
        ),
        (
            BLOCK_DIR / "GunSmithTableBlockC.java",
            r"if\s*\(\s*half\s*==\s*TableHalf\.(\w+)\s*\)",
            r"getValue\(\s*HALF\s*\)\s*==\s*TableHalf\.(\w+)\s*\)",
            DOUBLE_BLOCK_LOOT["workbench_c.json"][1],
            "GunSmithTableBlockC",
        ),
    ]
    for path, guard_re, neighbour_re, root_value, label in cases:
        text = strip_comments(path.read_text(encoding="utf-8"))
        body = method_body(text, "BlockState playerWillDestroy(")
        if body is None:
            fail("D", f"{label}: 找不到 playerWillDestroy(...)，无法核对创造模式抑制判据")
            continue
        guard = re.search(guard_re, body)
        neighbour = re.search(neighbour_re, body)
        if not guard or not neighbour:
            fail("D", f"{label}: playerWillDestroy 里认不出「守卫 / 邻居核对」写法，请同步更新本脚本的正则")
            continue
        guard_part = guard.group(1).lower()
        neighbour_part = neighbour.group(1).lower()
        if guard_part == root_value:
            fail(
                "D",
                f"{label}: 创造模式抑制的守卫挂在根半边（{guard_part}）上 —— 判据写反了。"
                f"会产出的是根半边（loot 条件 {root_value}），守卫应挂在非根半边上，"
                "否则创造模式挖任意一半都会漏出一个工作台",
            )
        if neighbour_part != root_value:
            fail(
                "D",
                f"{label}: 创造模式下被静默拿掉的邻居是 {neighbour_part}，应是根半边 {root_value}"
                "（要抑制的是会走战利品表产出的那一半）",
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
    check_creative_suppression_direction()

    def status(item: str) -> str:
        return "FAIL" if failed[item] else "OK"

    print("掉落接线检查：")
    print(f"  A. 方块类无手工 popResource —— {status('A')}")
    print(f"  B. 六份战利品表 schema + 根半边条件 —— {status('B')}")
    print(f"  C. isRoot() 与 loot 根半边一致 —— {status('C')}")
    print(f"  D. 创造模式抑制判据挂在非根半边、拿掉根半边 —— {status('D')}")
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
