#!/usr/bin/env python3
"""Mixin 注册双向守卫（1211 复核文档 §0/§1 的建议，2026-09-01 采纳）。

查两个方向，任一违规非 0 退出：
1. 树里存在 `*Mixin.java`，但没有任何 `src/main/resources/*.mixins.json` 注册它
   —— 这类缺口完全无声：`require=0` 只覆盖「目标不存在」，不覆盖「mixin 没注册」，
   组合症状与「设计上的静默回退」一模一样（本仓曾因此漏掉世界 GPU 表的消费钩子）。
2. 某个 `*.mixins.json` 注册了类，但树里没有对应的 `.java` —— 一旦该配置被
   fabric.mod.json 引用就会在 apply 阶段抛 `not found`（本仓的
   `tacz.compat.acceleratedrendering.mixins.json` 死文件即此形，已删）。

`INTENTIONALLY_UNREGISTERED` 是白名单：本仓与 1211 侧同样不注册、有明文理由的类。
新加 mixin 时若是有意不注册，请把类名和理由加进白名单，而不是绕过本检查。
"""
import glob
import json
import os
import sys

# 有意不注册的 mixin（类名 -> 理由摘要，理由详见各类头注 / 复核文档）
INTENTIONALLY_UNREGISTERED = {
    # 第 42 轮裁定：PlaySoundSourceEvent 全仓零消费者，修它 = 往 vanilla 音频热路径
    # 注入两个依赖 lambda 合成名（跨构建变号）的 mixin，纯负收益。1211 复核 §5 同判。
    "SoundEngineMixin",
    # 与 1211 侧同样未注册（复核 §5 核对过：两边一致，属既有约定而非缺口）
    "ChannelAccessHandleMixin",
    "ClipContextMixin",
    "HumanoidModelMixin",
    "ShapedRecipeMixin",
}


def main() -> int:
    registered = {}
    for cfg in glob.glob("src/main/resources/*.mixins.json"):
        with open(cfg, encoding="utf-8") as fh:
            data = json.load(fh)
        for entry in data.get("mixins", []) + data.get("client", []) + data.get("server", []):
            registered[entry.split(".")[-1]] = os.path.basename(cfg)

    files = {
        f[:-5]
        for root, _, fs in os.walk("src/main/java")
        for f in fs
        if f.endswith("Mixin.java")
    }

    failures = []

    orphan_files = sorted(files - set(registered) - INTENTIONALLY_UNREGISTERED)
    for name in orphan_files:
        failures.append(f"mixin class not registered in any config: {name}.java")

    missing_files = sorted(
        (name, cfg) for name, cfg in registered.items()
        if not glob.glob(f"src/main/java/**/{name}.java", recursive=True)
    )
    for name, cfg in missing_files:
        failures.append(f"{cfg} registers {name} but no such class exists in src/main/java")

    if failures:
        for line in failures:
            print("FAIL:", line)
        return 1

    print(f"mixin registration OK: {len(registered)} registered, "
          f"{len(files)} classes, {len(INTENTIONALLY_UNREGISTERED)} intentionally unregistered")
    return 0


if __name__ == "__main__":
    sys.exit(main())
