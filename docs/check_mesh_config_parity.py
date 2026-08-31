#!/usr/bin/env python3
"""[mesh_loader] 配置齐平自查（TOML ↔ 局内 Cloth 面板 ↔ en/zh 语言键）。

R3 那轮把 `MeshyConfig` 里所有键都接进了 Cloth「渲染」页。三边（+ 默认值 + 范围）
靠人眼对齐迟早会歪，所以给一个零依赖脚本；`docs/ci/build.yml`（待上线）里对应的那步
就是跑它，非 0 退出即 fail。

    python3 docs/check_mesh_config_parity.py

查六件事：
1. `MeshyConfig` 里每个 `builder.define*("Key", …)` 都被 Cloth 引用（不多不少，顺序无关）；
2. Cloth 那条引用的**字段名**与 toml 键是同一个选项（按 `FIELD = builder.define("Key")` 配对，
   不是按蛇形猜 —— `MeshEnable` ↔ `ENABLE_MESH` 这种命名本来就不机械）；
3. `setDefaultValue` 与 toml 默认值一致（Cloth 的「重置为默认」读的是这里）；
4. `startIntField/startDoubleField` 的 setMin/setMax 与 `defineInRange` 的区间一致；
5. 每个键都有 `config.tacz.client.render.<snake(key)>` 与 `.desc` 两个语言键；
6. en_us / zh_cn 的 `client.render.mesh_*` 键集合完全相同。
"""
import json
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
CFG = ROOT / "src/main/java/cn/sh1rocu/tacz/compat/meshloader/config/MeshyConfig.java"
CLOTH = ROOT / "src/main/java/com/tacz/guns/compat/cloth/client/RenderClothConfig.java"
LANG = {ns: ROOT / f"src/main/resources/assets/tacz/lang/{ns}.json" for ns in ("en_us", "zh_cn")}
PREFIX = "config.tacz.client.render."


def toml_options():
    """返回 {lang_key_suffix: {field, default, min, max}}，按 toml 键名蛇形化当 id。"""
    src = CFG.read_text(encoding="utf-8")
    out = {}
    # FIELD = builder.define("Key", default);
    # FIELD = builder.defineInRange("Key", default, min, max);
    pat = re.compile(
        r"(\w+)\s*=\s*builder\.(define|defineInRange|defineList)\(\s*\"(\w+)\"\s*,(.{0,220}?)\)\s*;",
        re.S,
    )
    for field, kind, key, rest in pat.findall(src):
        if kind == "defineList":
            continue
        top = re.match(r"\s*(.+?)\s*(?:,\s*(.+?)\s*,\s*(.+?)\s*)?$", rest, re.S)
        default = (top.group(1) if top else rest).strip()
        lo = hi = None
        if kind == "defineInRange":
            nums = re.findall(r"-?[\d_]+(?:\.\d+)?", rest)
            if len(nums) >= 3:
                default, lo, hi = nums[0], nums[1], nums[2]
        out[snake(key)] = {
            "field": field,
            "toml_key": key,
            "default": norm(default),
            "min": norm(lo) if lo else None,
            "max": norm(hi) if hi else None,
        }
    return out


def snake(key):
    """MeshGpuBaking -> mesh_gpu_baking（面板与语言键都用这个形式）。"""
    return re.sub(r"(?<!^)(?=[A-Z])", "_", key).lower()


def norm(v):
    """把 Java 字面量归一化，便于和 Cloth 里的值逐字比对。"""
    if v is None:
        return None
    v = str(v).strip().replace("_", "")
    v = re.sub(r"^([0-9.]+)[fFdD]$", r"\1", v)
    if re.fullmatch(r"-?\d+\.\d*", v):
        v = v.rstrip("0").rstrip(".")
        if "." not in v:
            v += ".0"
    return v


def cloth_entries():
    """返回 {lang_key_suffix: {field, default, min, max}}。"""
    src = CLOTH.read_text(encoding="utf-8")
    pat = re.compile(
        r"start(BooleanToggle|IntField|DoubleField)\(\s*"
        r"Component\.translatable\(\"" + re.escape(PREFIX) + r"(mesh_\w+?)\"\)\s*,\s*"
        r"MeshyConfig\.(\w+)\.get\(\)(.{0,400}?)(?:\.setSaveConsumer|\.build\(\))",
        re.S,
    )
    out = {}
    for kind, suffix, field, tail in pat.findall(src):
        default = re.search(r"setDefaultValue\(\s*([^)]*?)\s*\)", tail)
        lo = re.search(r"setMin\(\s*([^)]*?)\s*\)", tail)
        hi = re.search(r"setMax\(\s*([^)]*?)\s*\)", tail)
        out[suffix] = {
            "field": field,
            "default": norm(default.group(1)) if default else None,
            "min": norm(lo.group(1)) if lo else None,
            "max": norm(hi.group(1)) if hi else None,
        }
    return out


def main():
    errors = []
    toml = toml_options()
    cloth = cloth_entries()
    if not toml:
        print("读不到 MeshyConfig 的 define 调用 —— 脚本本身需要更新（正则没命中）", file=sys.stderr)
        return 2

    only_toml = sorted(set(toml) - set(cloth))
    only_cloth = sorted(set(cloth) - set(toml))
    if only_toml:
        errors.append("toml 有、局内面板没有：%s" % ", ".join(only_toml))
    if only_cloth:
        errors.append("局内面板有、toml 没有（拼歪或已删）：%s" % ", ".join(only_cloth))

    for suffix, opt in sorted(toml.items()):
        entry = cloth.get(suffix)
        if not entry:
            continue
        if entry["field"] != opt["field"]:
            errors.append(
                "%s 绑到了 MeshyConfig.%s，应为 %s" % (suffix, entry["field"], opt["field"])
            )
        if entry["default"] != opt["default"]:
            errors.append(
                "%s 默认值不一致：toml=%s cloth=%s（Cloth 的「重置为默认」读的是后者）"
                % (suffix, opt["default"], entry["default"])
            )
        if opt["min"] is not None:
            if entry["min"] != opt["min"] or entry["max"] != opt["max"]:
                errors.append(
                    "%s 范围不一致：toml=[%s,%s] cloth=[%s,%s]"
                    % (suffix, opt["min"], opt["max"], entry["min"], entry["max"])
                )
        elif entry["min"] is not None or entry["max"] is not None:
            errors.append("%s 在 Cloth 里有 setMin/setMax 但 toml 侧不是 defineInRange" % suffix)

    langs = {}
    for ns, path in LANG.items():
        # 只看本段的键（其它 render.* 项不归 TML 管，但它们之间的 en/zh 不齐平同样值得报出来）
        langs[ns] = set(k for k in json.loads(path.read_text(encoding="utf-8"))
                        if k.startswith(PREFIX))
        for suffix in toml:
            for k in (PREFIX + suffix, PREFIX + suffix + ".desc"):
                if k not in langs[ns]:
                    errors.append("%s 缺语言键 %s" % (ns, k))
    if langs["en_us"] ^ langs["zh_cn"]:
        errors.append("en/zh 键不齐平：%s" % ", ".join(sorted(langs["en_us"] ^ langs["zh_cn"])))

    titles = sorted(k for k in langs["en_us"] if k[len(PREFIX):] in toml)
    print("toml %d 项 | 局内 %d 条 | 语言键 %d 个（en，本段 %d 标题 + %d 说明）"
          % (len(toml), len(cloth), len(titles) * 2, len(titles), len(titles)))
    if errors:
        print("\n".join("  ✗ " + e for e in errors), file=sys.stderr)
        return 1
    print("齐平 ✓（键、字段绑定、默认值、范围、语言键、en/zh）")
    return 0


if __name__ == "__main__":
    sys.exit(main())
