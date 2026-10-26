#!/usr/bin/env python3
"""离线审计方块实体的持久化与移除收尾（26.2 线）。

为什么需要它
------------
2026-10-26/27 连续爆出两个「代码在、行为不在、且不报错」的问题：

1. **雕像上的枪随方块一起消失** —— 上游在 1.20.1 用 {@code Block#onRemove} 掉枪，
   而 26.2 把 {@code onRemove} 整个删了，移植时只留下一条
   「方块实体移除时自动清理」的注释。**没有任何默认行为**：不覆写
   {@code BlockEntity#preRemoveSideEffects}，内容物就是静默消失。
2. **标靶主人名重启后丢失** —— `saveAdditional` 已经改写成扁平键
   `owner_uuid` / `owner_name`，但 `loadAdditional` 的前置判据还写着上游的
   `Owner` 键，而这个键再没有任何地方写入 ⇒ 判据恒 false ⇒ 整段读取被跳过。

这两类问题的共同点是：**编译器不会报错，游戏也不报错，数据直接没了**。本脚本用
静态分析把它们钉住，能覆盖的只有「键名/钩子是否存在」这一层，不覆盖运行期语义。

检查项
------
A. `loadAdditional` 里用 `getStringOr("K", "").isEmpty()` 当前置判据的键 `K`，
   必须由同一个类的 `saveAdditional`（或其 `store`/`putX` 调用）写入过。
B. `saveAdditional` 写的键与 `loadAdditional` 读的键应当对称（只写不读＝死数据，
   只读不写＝永远读不到，后者正是标靶那个 bug）。
C. 持有 `ItemStack` 字段的方块实体必须覆写 `preRemoveSideEffects`（或 `setRemoved`），
   否则内容物在方块被移除时会静默丢失。确实不需要掉的，用
   `// tacz:no-drop-on-remove` 注释显式豁免。
D. 不得出现 `onRemove` 覆写（26.2 已删除该 API，写了也不会被调用；
   编译期能挡住 `@Override`，这里兜住"顺手加个无注解同名方法"）。

用法
----
    python3 scripts/verify_block_entity_persistence.py            # 报告，恒退出 0
    python3 scripts/verify_block_entity_persistence.py --strict   # 有问题则退出 1
"""

from __future__ import annotations

import argparse
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SRC = ROOT / "src/main/java"

WRITE_CALL = re.compile(
    r"\.(?:putString|putInt|putBoolean|putFloat|putLong|putDouble|putUUID|store)\(\s*([^,]+),"
)
READ_CALL = re.compile(
    r"\.(?:getStringOr|getIntOr|getBooleanOr|getFloatOr|getLongOr|getDoubleOr|read)\(\s*([^,]+),"
)
GATE = re.compile(r'(?:getStringOr|getIntOr)\(\s*([^,]+),\s*(?:""|0)\s*\)\s*(?:\.isEmpty\(\)|> 0|== 0)')
CONST = re.compile(r'static\s+final\s+String\s+([A-Za-z0-9_]+)\s*=\s*"([^"]*)"')

errors: list[str] = []
checked: list[str] = []


def resolve(expr: str, consts: dict[str, str]) -> str | None:
    """把 `"Literal"` / `CONSTANT` 解析成字符串；解析不了返回 None（跳过，不误报）。"""
    expr = expr.strip()
    literal = re.fullmatch(r'"([^"]*)"', expr)
    if literal:
        return literal.group(1)
    if expr in consts:
        return consts[expr]
    return None


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


def keys_in(body: str, pattern: re.Pattern[str], consts: dict[str, str]) -> set[str]:
    out: set[str] = set()
    for expr in pattern.findall(body):
        key = resolve(expr, consts)
        if key:
            out.add(key)
    return out


def check_block_entity(path: Path) -> None:
    text = path.read_text(encoding="utf-8")
    if not re.search(r"class\s+\w+\s+extends\s+(?:[\w.]*\.)?BlockEntity\b", text):
        return

    name = path.stem
    checked.append(name)
    consts = dict(CONST.findall(text))

    save = method_body(text, "void saveAdditional(")
    load = method_body(text, "void loadAdditional(")

    if save is not None and load is not None:
        written = keys_in(save, WRITE_CALL, consts)
        read = keys_in(load, READ_CALL, consts)

        for key in sorted(keys_in(load, GATE, consts) - written):
            errors.append(
                f"{name}: loadAdditional 用 `{key}` 作前置判据，但 saveAdditional 从不写这个键"
                " —— 判据恒为假，整段数据重启后丢失（标靶主人的同款坑）"
            )
        for key in sorted(written - read):
            errors.append(
                f"{name}: saveAdditional 写了 `{key}` 却从不读回（死数据，或读取处漏了）"
            )
        for key in sorted(read - written):
            errors.append(
                f"{name}: loadAdditional 读 `{key}` 却从不写入（永远读不到默认值之外的内容）"
            )

    # C. 持有物品的方块实体必须在移除时处理内容物
    if re.search(r"\bItemStack\s+\w+\s*=", text) and "// tacz:no-drop-on-remove" not in text:
        if not re.search(r"\bvoid\s+preRemoveSideEffects\s*\(", text) and not re.search(
            r"\bvoid\s+setRemoved\s*\(", text
        ):
            errors.append(
                f"{name}: 持有 ItemStack 字段但没有 preRemoveSideEffects/setRemoved —— "
                "26.2 已无 Block#onRemove，也没有任何默认清理，方块被移除时内容物会静默消失"
                "（雕像枪的同款坑）。确实不需要掉的请加 `// tacz:no-drop-on-remove` 注释"
            )

    # D. onRemove 在 26.2 已删除
    if re.search(r"\b(?:public|protected)\s+void\s+onRemove\s*\(", text):
        errors.append(
            f"{name}: 出现了 `onRemove` 方法 —— 26.2 的 Block#onRemove 已删除，"
            "写了也不会被调用，应改用 preRemoveSideEffects"
        )


def main() -> int:
    parser = argparse.ArgumentParser(description="审计方块实体的持久化与移除收尾")
    parser.add_argument("--strict", action="store_true", help="有问题时退出 1")
    args = parser.parse_args()

    if not SRC.is_dir():
        print(f"[ERR] 找不到 {SRC}", file=sys.stderr)
        return 1 if args.strict else 0

    for path in sorted(SRC.rglob("*.java")):
        check_block_entity(path)

    print(f"方块实体持久化审计：检查 {len(checked)} 个类（{', '.join(checked) or '无'}）")
    if errors:
        print(f"[FAIL] 发现 {len(errors)} 项：")
        for error in errors:
            print(f"  - {error}")
        return 1 if args.strict else 0
    print("全部通过。注意：这只是静态检查，不替代编译与实机「破坏后重进存档」测试。")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
