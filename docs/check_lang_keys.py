#!/usr/bin/env python3
"""语言键守卫（1211 复核文档 §2 的建议，2026-09-01 采纳）。两项检查，任一违规非 0 退出：

1. **lang 只许增不许减**：`assets/tacz/lang/{en_us,zh_cn}.json` 的键集必须是
   上一提交（HEAD）的**超集**。9ed6b93 那轮曾把 298 键的整文件换成 36 键的
   mesh 段（整文件覆盖而非 merge），游戏内所有 `item.*`/`tooltip.*` 退回裸键
   —— 这条一行断言就能拦住。
2. **代码引用到的键必须存在**：从 `src/main/java` 抽字面量翻译键
   （`translatable("…")` / `.key("…")` / `setTranslationKey("…")` /
   `translationKey("…")`），与 `src/main/resources/assets/*/lang/en_us.json`
   的**全命名空间并集**求差（1211 的 `jei.tacz.ammo_query.*` 就在
   `assets/tacz_ammo_query/lang/`，只扫 `assets/tacz/lang` 会凭空多误报）。

白名单三类（与 1211 复核的数字一致：321 个字面量键、23 个未命中全在此列）：
- 运行时拼接的前缀（`item.` / `itemGroup.` / `attribute.modifier.` / `potion.potency.`）；
- 原版自带键（`narration.checkbox.usage.*` / `potion.whenDrank` / `potion.withAmplifier` /
  `potion.withDuration`）；
- 上游遗留（`tacz.type.scope.name` / `tacz.type.extended_mag.name` /
  `tacz.type.grip.name`：26.2 的 en_us.json 同样没有，不是移植引入的，不"顺手补"）。
"""
import glob
import json
import re
import subprocess
import sys

LANG_FILES = [
    "src/main/resources/assets/tacz/lang/en_us.json",
    "src/main/resources/assets/tacz/lang/zh_cn.json",
]
ALL_LANG_GLOB = "src/main/resources/assets/*/lang/en_us.json"
SOURCE_SCAN_GLOB = "src/main/java/**/*.java"

# 前缀按「运行时拼接」处理：这些键的字面量在源码里只是前缀
PREFIX_WHITELIST = (
    "item.",
    "itemGroup.",
    "attribute.modifier.",
    "potion.potency.",
    "narration.checkbox.usage.",
)
EXACT_WHITELIST = {
    # vanilla 自带
    "potion.whenDrank", "potion.withAmplifier", "potion.withDuration",
    # 上游遗留（26.2 同样没有；见模块 docstring）。整族 tacz.type.<x>.name 都在：
    # scope / extended_mag / grip 三条来自 1211 复核 §2 的白名单；其余十条是本仓
    # 首跑补齐的同族成员（2026-09-01），处置相同 —— 上游遗留，不"顺手补"。
    "tacz.type.scope.name", "tacz.type.extended_mag.name", "tacz.type.grip.name",
    "tacz.type.laser.name", "tacz.type.mg.name", "tacz.type.muzzle.name",
    "tacz.type.pistol.name", "tacz.type.rifle.name", "tacz.type.rpg.name",
    "tacz.type.shotgun.name", "tacz.type.smg.name", "tacz.type.sniper.name",
    "tacz.type.stock.name",
}

PATTERNS = (
    re.compile(r'translatable\(\s*"([^"]+)"'),
    re.compile(r'\.key\(\s*"([^"]+)"'),
    re.compile(r'setTranslationKey\(\s*"([^"]+)"'),
    re.compile(r'translationKey\(\s*"([^"]+)"'),
)


def load(path: str):
    with open(path, encoding="utf-8") as fh:
        return json.load(fh)


def main() -> int:
    failures = []

    # 1) superset guard vs HEAD
    for path in LANG_FILES:
        head = subprocess.run(
            ["git", "show", f"HEAD:{path}"], capture_output=True, text=True
        )
        if head.returncode != 0:
            continue  # file is new this commit; nothing to compare against
        old_keys = set(json.loads(head.stdout))
        new_keys = set(load(path))
        lost = sorted(old_keys - new_keys)
        for key in lost:
            failures.append(f"{path} lost a key that HEAD had: {key}")

    # 2) referenced keys must exist (union over every namespace's en_us)
    available = set()
    for path in glob.glob(ALL_LANG_GLOB):
        available |= set(load(path))

    used = set()
    for path in glob.glob(SOURCE_SCAN_GLOB, recursive=True):
        with open(path, encoding="utf-8", errors="replace") as fh:
            text = fh.read()
        for pat in PATTERNS:
            used |= set(pat.findall(text))

    missing = []
    for key in sorted(used):
        if key in available or key in EXACT_WHITELIST:
            continue
        if any(key.startswith(p) for p in PREFIX_WHITELIST):
            continue
        missing.append(key)
    for key in missing:
        failures.append(f'translation key referenced in code but missing from lang files: "{key}"')

    if failures:
        for line in failures:
            print("FAIL:", line)
        return 1

    print(f"lang keys OK: superset of HEAD; {len(used)} literal keys referenced, all present/whitelisted")
    return 0


if __name__ == "__main__":
    sys.exit(main())
