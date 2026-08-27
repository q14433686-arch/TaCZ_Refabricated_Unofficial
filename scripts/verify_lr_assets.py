#!/usr/bin/env python3
"""校验 LRTactical 的客户端资源：sounds.json 结构、音源文件、效果图标。

为什么需要它（2026-08-27 实测事故）
----------------------------------
耳鸣声在 26.2 上一直不响，日志只有一行我们自己加的 WARN
（`result=NOT_STARTED`）。根因是 `assets/lrtactical/sounds.json` 顶层多了一个
`"_comment"` 字符串键：26.2 的 `SoundManager` 把这个文件整体按
`Map<String, SoundEventRegistration>` 用 Gson `fromJson` 反序列化
（`SoundManager` 常量池里能看到 `TypeToken<Map<String,SoundEventRegistration>>`），
**顶层的每个键都会被当成音效 id**，值是字符串而不是对象就直接解析失败 ——
整个文件作废，`lrtactical` 命名空间一个音效定义都没有。

注意这与我们其它 JSON 的写法不同：`items/*.json`、`models/item/*.json` 里的
`_comment` 是**对象内部**的一个字段，无害；`sounds.json` 的顶层是 map，不能这么写。

顺带覆盖另外两个「静默失效」：
- `sounds[].name` 指向的 ogg 不存在（26.1.2 / 1.21.11 就缺 `stun_ringing.ogg`）；
- `ModEffects` 注册的效果缺 `textures/mob_effect/<id>.png`（显示为紫黑块）。

用法
----
    python3 scripts/verify_lr_assets.py            # 报告，恒退出 0
    python3 scripts/verify_lr_assets.py --strict   # 有问题则退出 1
"""

from __future__ import annotations

import argparse
import json
import pathlib
import re
import sys

REPO = pathlib.Path(__file__).resolve().parent.parent
ASSETS = REPO / "src/main/resources/assets/lrtactical"
NS = "lrtactical"

problems: list[str] = []
passes: list[str] = []


def check_sounds_json() -> None:
    path = ASSETS / "sounds.json"
    if not path.exists():
        problems.append(f"缺少 {path.relative_to(REPO)}：耳鸣声不可能响")
        return
    try:
        data = json.loads(path.read_text(encoding="utf-8"))
    except json.JSONDecodeError as e:
        problems.append(f"{path.name} 不是合法 JSON：{e}")
        return

    # 顶层必须是「音效 id -> 对象」的 map
    for key, value in data.items():
        if not isinstance(value, dict):
            problems.append(
                f"{path.name} 顶层键 {key!r} 的值是 {type(value).__name__} 而不是对象 —— "
                f"会让整个文件反序列化失败（26.2 按 Map<String,SoundEventRegistration> 解析），"
                f"sounds.json 顶层不能放 _comment 之类的自由文本")
            continue
        sounds = value.get("sounds")
        if not isinstance(sounds, list) or not sounds:
            problems.append(f"{path.name} 的 {key!r} 没有 sounds 数组")
            continue
        for entry in sounds:
            name = entry.get("name") if isinstance(entry, dict) else None
            if not isinstance(name, str):
                problems.append(f"{path.name} 的 {key!r} 里有 sounds 项缺少 name")
                continue
            ns, _, sound_path = name.partition(":")
            ns = ns or NS
            ogg = REPO / "src/main/resources/assets" / ns / "sounds" / f"{sound_path}.ogg"
            if not ogg.exists():
                problems.append(
                    f"{path.name} 的 {key!r} 指向 {name}，但找不到 "
                    f"{ogg.relative_to(REPO)}（Minecraft 只接受 .ogg）")
            else:
                passes.append(f"{key} -> {name} ({ogg.stat().st_size} B)")


def check_effect_textures() -> None:
    mod_effects = REPO / "src/main/java/me/xjqsh/lrtactical/init/ModEffects.java"
    if not mod_effects.exists():
        problems.append(f"找不到 {mod_effects.relative_to(REPO)}")
        return
    ids = re.findall(r'register\("([a-z_]+)"', mod_effects.read_text(encoding="utf-8"))
    if not ids:
        problems.append("ModEffects 里没解析出任何 register(\"...\") 调用，检查正则")
        return
    for effect_id in ids:
        png = ASSETS / "textures/mob_effect" / f"{effect_id}.png"
        if png.exists():
            passes.append(f"mob_effect/{effect_id}.png ({png.stat().st_size} B)")
        else:
            problems.append(
                f"效果 {NS}:{effect_id} 缺贴图 {png.relative_to(REPO)} —— "
                f"效果图标会显示成紫黑块（可用 scripts/gen_effect_icons.py 生成）")


def main() -> int:
    ap = argparse.ArgumentParser(description=__doc__,
                                 formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--strict", action="store_true", help="发现问题时返回 1")
    args = ap.parse_args()

    check_sounds_json()
    check_effect_textures()

    for p in passes:
        print(f"  ok:   {p}")
    for p in problems:
        print(f"  FAIL: {p}")
    print(f"\n通过 {len(passes)} · 失败 {len(problems)}")
    if problems:
        return 1 if args.strict else 0
    print("全部通过。")
    return 0


if __name__ == "__main__":
    sys.exit(main())
