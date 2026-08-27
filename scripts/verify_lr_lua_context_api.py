#!/usr/bin/env python3
"""校验 LRTactical Lua 状态机脚本调用的 context 方法在 Java 侧真实存在。

为什么需要它（2026-08-27 实测事故）
--------------------------------
`src/main/resources/assets/lrtactical/scripts/*.lua` 里写的 `context:xxx(...)`
是对 Java 上下文类的<b>动态</b>调用：LuaJ 把 Java 对象包成 `JavaInstance`，
查不到同名成员时返回 `NIL`，随后 `NIL` 被当作函数调用会抛
`LuaError: attempt to call 'xxx' (a nil value)`。

调用链上<b>没有任何 catch</b>（`LuaAnimationState#transition` →
`AnimationStateMachine#trigger` → `MeleeAttackKeys#triggerAttackAnimation`
→ Fabric 客户端输入回调），因此装了内容包的玩家一按左键轻击就会炸，
而编译期完全无感 —— javac 根本不看 Lua。

本仓库确实中过这一枪：默认近战脚本调用 `context:getActionCount("attack_left")`
做连击动画取模，而 `BaseAnimationStateContext` 一直没有这个方法
（同步自姊妹仓 TaCZ_Renovated 26.2 后补上）。

用法
----
    python3 scripts/verify_lr_lua_context_api.py          # 报告，恒退出 0
    python3 scripts/verify_lr_lua_context_api.py --strict # 有缺口则退出 1（CI/合并前用）

局限（如实声明）
----------------
* 只做<b>静态方法名</b>比对，不校验参数个数/类型，也不校验 Lua 里的分支是否真的会走到；
* 只覆盖本仓库自带的 `assets/lrtactical/scripts/`，第三方内容包不在范围内；
* 继承链靠源码里的 `extends` 逐级解析（含 `com.tacz.guns` 与 `me.xjqsh.lrtactical`
  两个包），遇到源码里没有的父类会明确报出，而不是静默当成"没有该方法"。
"""

from __future__ import annotations

import argparse
import pathlib
import re
import sys

REPO = pathlib.Path(__file__).resolve().parent.parent
JAVA_ROOT = REPO / "src" / "main" / "java"
SCRIPT_DIR = REPO / "src" / "main" / "resources" / "assets" / "lrtactical" / "scripts"

# Lua 状态机脚本里 `context:foo(` / `context.foo(` 的调用点
CALL_RE = re.compile(r"[\w.]*context\s*[:.]\s*([A-Za-z_]\w*)\s*\(")
CLASS_RE = re.compile(r"\b(?:class|interface|enum|record)\s+(\w+)(?:\s*<[^>]*>)?\s+extends\s+([\w.]+)")
METHOD_RE = re.compile(
    r"(?m)^\s*public\s+(?:static\s+|final\s+|synchronized\s+|abstract\s+|default\s+)*"
    r"[\w.<>\[\],\s?]+?\s+([A-Za-z_]\w*)\s*\("
)
SOURCE_INDEX: dict[str, pathlib.Path] = {}


def index_sources() -> None:
    for path in JAVA_ROOT.rglob("*.java"):
        SOURCE_INDEX.setdefault(path.stem, path)


def resolve_chain(start: str) -> tuple[list[str], list[str]]:
    """返回 (链上的类名, 源码里找不到的类名)。"""
    chain: list[str] = []
    unresolved: list[str] = []
    current: str | None = start
    while current:
        chain.append(current)
        path = SOURCE_INDEX.get(current)
        if path is None:
            unresolved.append(current)
            break
        match = CLASS_RE.search(path.read_text(encoding="utf-8", errors="ignore"))
        current = match.group(2).split(".")[-1].split("<")[0] if match else None
    return chain, unresolved


def public_methods(class_name: str) -> set[str]:
    path = SOURCE_INDEX.get(class_name)
    if path is None:
        return set()
    return set(METHOD_RE.findall(path.read_text(encoding="utf-8", errors="ignore")))


def available_methods(contexts: list[str]) -> tuple[set[str], list[str]]:
    available: set[str] = set()
    unresolved: list[str] = []
    for context in contexts:
        chain, missing = resolve_chain(context)
        for cls in chain:
            available |= public_methods(cls)
        unresolved.extend(f"{context} -> {m}" for m in missing)
    return available, sorted(set(unresolved))


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--strict", action="store_true", help="有缺口时返回非 0")
    args = parser.parse_args()

    index_sources()

    # 三个上下文类 = 三类 LR 物品各自的动画上下文。
    # ConsumableAnimationStateContext 若尚未移植，会作为"未解析父类"如实报出。
    contexts = [
        "BaseAnimationStateContext",
        "ThrowableAnimationStateContext",
        "ConsumableAnimationStateContext",
    ]
    available, unresolved = available_methods(contexts)
    print(f"context 可用方法 {len(available)} 个（继承链已展开）")
    for item in unresolved:
        print(f"  [警告] 源码中找不到父类：{item}")

    if not SCRIPT_DIR.is_dir():
        print(f"[跳过] 没有 {SCRIPT_DIR.relative_to(REPO)}")
        return 0

    gaps: list[tuple[str, str]] = []
    for lua in sorted(SCRIPT_DIR.glob("*.lua")):
        text = lua.read_text(encoding="utf-8", errors="ignore")
        called = sorted(set(CALL_RE.findall(text)))
        missing = [name for name in called if name not in available]
        status = "OK" if not missing else "GAP"
        print(f"  [{status}] {lua.name}: {len(called)} 个调用")
        for name in missing:
            print(f"        缺少 Java 侧实现：context:{name}(...)")
            gaps.append((lua.name, name))

    if gaps:
        print(f"\n结论：{len(gaps)} 处 Lua 调用在 Java 侧没有对应方法（运行期 LuaError）。")
        return 1 if args.strict else 0
    print("\n结论：全部 Lua 调用都有对应的 Java 方法。")
    return 0


if __name__ == "__main__":
    sys.exit(main())
