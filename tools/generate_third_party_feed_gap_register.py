#!/usr/bin/env python3
"""Generate the audited third-party feed material/function gap register.

This is an author/CI utility. It never runs in a player's game and never
creates a gun_feed declaration: it records the current consequences of already
curated data.  The machine-readable JSON contains every non-default carrier
identity whose artwork is neutral/family-level rather than exact, plus every
reference profile intentionally left on legacy runtime behaviour.
"""

from __future__ import annotations

import argparse
import json
from collections import defaultdict
from pathlib import Path
from typing import Any

ROOT = Path(__file__).resolve().parents[1]
RESOURCE_ROOT = ROOT / "src/main/resources"
DATA_ROOT = RESOURCE_ROOT / "data"
ICON_MAPPING = ROOT / "tools/industry/icon_mapping.json"
LANGUAGE_FILES = {
    "zh_cn": RESOURCE_ROOT / "assets/tacz/lang/zh_cn.json",
    "en_us": RESOURCE_ROOT / "assets/tacz/lang/en_us.json",
}
OUTPUT_JSON = ROOT / "tools/industry/third_party_feed_gap_registry.json"
OUTPUT_DOC = ROOT / "docs/THIRD_PARTY_FEED_GAP_REGISTER.md"


def read_json(path: Path) -> Any:
    return json.loads(path.read_text(encoding="utf-8"))


def language_entries(language: str) -> dict[str, str]:
    path = LANGUAGE_FILES[language]
    data = read_json(path)
    return data if isinstance(data, dict) else {}


def localized_display_name(key: str, language: str, entries: dict[str, str]) -> str:
    if not key:
        return "未声明名称" if language == "zh_cn" else "Unnamed"
    value = entries.get(key)
    if isinstance(value, str) and value.strip():
        return value
    # Preserve an honest author-facing fallback when a third-party sidecar has
    # a display key supplied only by the external pack. Do not invent a name
    # from family/namespace heuristics.
    return f"未载入本地化：{key}" if language == "zh_cn" else f"Unloaded translation: {key}"


def detailed_material_class(state: str, mechanism: str) -> str:
    carrier = "belt_box" if mechanism == "belt" else "detachable_magazine"
    if state == "neutral_generic_material":
        return f"neutral_{carrier}"
    if state == "family_level_material":
        return f"family_reused_{carrier}"
    return f"exact_{carrier}"


def detailed_material_class_label(material_class: str) -> str:
    labels = {
        "neutral_detachable_magazine": "中性通用可拆卸弹匣（缺专用细节图）",
        "neutral_belt_box": "中性通用弹链箱（缺专用细节图）",
        "family_reused_detachable_magazine": "复用同类可拆卸弹匣图（非精确）",
        "family_reused_belt_box": "复用同类弹链箱/弹链图（非精确）",
        "exact_detachable_magazine": "已有精确可拆卸弹匣图",
        "exact_belt_box": "已有精确弹链箱/弹链图",
    }
    return labels.get(material_class, material_class)


def family_icon_entries() -> dict[str, list[dict[str, Any]]]:
    entries = read_json(ICON_MAPPING).get("entries", [])
    by_family: dict[str, list[dict[str, Any]]] = defaultdict(list)
    for entry in entries:
        if not isinstance(entry, dict) or entry.get("item") != "tacz:magazine":
            continue
        family = entry.get("match", {}).get("magazine_family")
        if isinstance(family, str) and family:
            by_family[family].append(entry)
    return by_family


def material_state(family: str, mechanism: str, mappings: dict[str, list[dict[str, Any]]]) -> tuple[str, str]:
    entries = mappings.get(family, [])
    if entries:
        coverages = {entry.get("coverage", "exact") for entry in entries}
        texture = sorted(str(entry.get("texture", "")) for entry in entries)[0]
        if "exact" in coverages:
            return "exact_existing_material", texture
        return "family_level_material", texture
    return (
        "neutral_generic_material",
        "tacz_extra:item/mag_m249_box" if mechanism == "belt" else "tacz:item/magazine",
    )


def carrier_records() -> list[dict[str, Any]]:
    mappings = family_icon_entries()
    zh_names = language_entries("zh_cn")
    en_names = language_entries("en_us")
    grouped: dict[tuple[str, str, str], dict[str, Any]] = {}

    def record_display_name(record: dict[str, Any], capacity: int, display_name: Any) -> None:
        if isinstance(display_name, str) and display_name:
            record["display_name_keys"].setdefault(capacity, set()).add(display_name)

    for namespace_dir in sorted(DATA_ROOT.iterdir() if DATA_ROOT.exists() else []):
        if not namespace_dir.is_dir() or namespace_dir.name == "tacz":
            continue
        feed_dir = namespace_dir / "industry/gun_feed"
        if not feed_dir.exists():
            continue
        for path in sorted(feed_dir.glob("*.json")):
            data = read_json(path)
            if not isinstance(data, dict):
                continue
            mechanism = data.get("mechanism")
            family = data.get("magazine_family")
            ammo = data.get("ammo")
            capacity = data.get("magazine_capacity")
            if mechanism not in {"detachable_magazine", "belt"} or not all(
                isinstance(value, str) and value for value in (family, ammo)
            ) or not isinstance(capacity, int) or capacity < 1:
                continue
            key = (family, ammo, mechanism)
            record = grouped.setdefault(
                key,
                {
                    "family": family,
                    "ammo": ammo,
                    "mechanism": mechanism,
                    "capacities": set(),
                    "gun_ids": [],
                    "display_name_keys": {},
                },
            )
            record["capacities"].add(capacity)
            record_display_name(record, capacity, data.get("display_name"))
            for variant in data.get("carrier_variants", []):
                if isinstance(variant, dict) and isinstance(variant.get("capacity"), int):
                    variant_capacity = variant["capacity"]
                    record["capacities"].add(variant_capacity)
                    record_display_name(record, variant_capacity, variant.get("display_name"))
            record["gun_ids"].append(f"{namespace_dir.name}:{path.stem}")

    records = []
    for key in sorted(grouped):
        record = grouped[key]
        state, texture = material_state(record["family"], record["mechanism"], mappings)
        name_variants = []
        for capacity in sorted(record["display_name_keys"]):
            for translation_key in sorted(record["display_name_keys"][capacity]):
                name_variants.append({
                    "capacity": capacity,
                    "translation_key": translation_key,
                    "zh_cn": localized_display_name(translation_key, "zh_cn", zh_names),
                    "en_us": localized_display_name(translation_key, "en_us", en_names),
                })
        records.append(
            {
                "family": record["family"],
                "ammo": record["ammo"],
                "mechanism": record["mechanism"],
                "capacities": sorted(record["capacities"]),
                "gun_ids": sorted(record["gun_ids"]),
                "display_name_variants": name_variants,
                "material_state": state,
                "material_class": detailed_material_class(state, record["mechanism"]),
                "current_texture": texture,
                "needs_detailed_material": state != "exact_existing_material",
            }
        )
    return records


def function_records() -> list[dict[str, Any]]:
    records = []
    for namespace_dir in sorted(DATA_ROOT.iterdir() if DATA_ROOT.exists() else []):
        if not namespace_dir.is_dir() or namespace_dir.name == "tacz":
            continue
        reference_dir = namespace_dir / "industry/reference/guns"
        if not reference_dir.exists():
            continue
        for path in sorted(reference_dir.glob("*.json")):
            data = read_json(path)
            if not isinstance(data, dict):
                continue
            feed = data.get("feed", {})
            if not isinstance(feed, dict) or feed.get("runtime_mechanism") != "legacy":
                continue
            evidence = data.get("evidence", [])
            reason = evidence[2] if isinstance(evidence, list) and len(evidence) >= 3 else "legacy runtime boundary"
            ammo = data.get("ammunition", {})
            records.append(
                {
                    "gun_id": f"{namespace_dir.name}:{path.stem}",
                    "device": feed.get("device", "unknown"),
                    "action": data.get("action", "unknown"),
                    "expected_ammo": ammo.get("expected_ammo", "") if isinstance(ammo, dict) else "",
                    "reason": reason,
                    "needs_detailed_function": True,
                }
            )
    return records


def registry() -> dict[str, Any]:
    carriers = carrier_records()
    functions = function_records()
    by_namespace: dict[str, dict[str, int]] = defaultdict(lambda: defaultdict(int))
    for carrier in carriers:
        namespaces = {gun_id.split(":", 1)[0] for gun_id in carrier["gun_ids"]}
        for namespace in namespaces:
            by_namespace[namespace]["carrier_families"] += 1
            if carrier["needs_detailed_material"]:
                by_namespace[namespace]["material_gaps"] += 1
    for function in functions:
        by_namespace[function["gun_id"].split(":", 1)[0]]["function_gaps"] += 1
    material_classes: dict[str, int] = defaultdict(int)
    for carrier in carriers:
        if carrier["needs_detailed_material"]:
            material_classes[carrier["material_class"]] += 1
    return {
        "schema_version": 2,
        "generated_by": "tacz_third_party_feed_gap_register",
        "warning": "Author/CI audit only. This registry is not a datapack and cannot enable feeds.",
        "summary_by_namespace": {
            namespace: dict(sorted(counts.items()))
            for namespace, counts in sorted(by_namespace.items())
        },
        "detailed_material_classes": dict(sorted(material_classes.items())),
        "carrier_material_records": carriers,
        "legacy_function_records": functions,
    }


def render_document(data: dict[str, Any]) -> str:
    lines = [
        "# 第三方供弹细分材质 / 功能缺口登记册",
        "",
        "此文件由 `tools/generate_third_party_feed_gap_register.py` 生成，供作者和 CI 使用；",
        "普通玩家不需要运行 Python。它不会启用任何 `gun_feed`，只登记当前已经审计的数据中：",
        "",
        "- 没有精确细分材质、仍使用中性/家族级材料的实体载具；",
        "- 每个缺口载具当前可见的中文/英文名称和容量变体；",
        "- 已有事实 profile、但故意保持 `legacy` runtime 的枪械功能缺口。",
        "",
        "机器可读的完整逐 family / 逐枪记录位于：",
        "",
        "```text",
        "tools/industry/third_party_feed_gap_registry.json",
        "```",
        "",
        "## 状态定义",
        "",
        "| 状态 | 含义 |",
        "|---|---|",
        "| `exact_existing_material` | 有当前 family 的精确既有材料映射。 |",
        "| `family_level_material` | 复用同类材料（如 exposed belt），不声称精确网格。 |",
        "| `neutral_generic_material` | 使用中性通用弹匣 / belt-box 材料；功能真实，细分美术仍待补。 |",
        "| `legacy` function record | 事实已记录，但没有足够证据启用物理 carrier / 专用 reload route。 |",
        "",
        "## 按命名空间汇总",
        "",
        "| Namespace | 实体 family | 缺细分材质 family | 缺细分功能枪数 |",
        "|---|---:|---:|---:|",
    ]
    for namespace, counts in data["summary_by_namespace"].items():
        lines.append(
            f"| `{namespace}` | {counts.get('carrier_families', 0)} | "
            f"{counts.get('material_gaps', 0)} | {counts.get('function_gaps', 0)} |"
        )
    lines.extend([
        "",
        "## 细分材质分类汇总",
        "",
        "这里的分类按**当前视觉替代方式**，不是按枪种猜测。每一项都是已有 `gun_feed` 的真实实体载具；",
        "缺失的是授权/精确美术，不是库存、容量、制造或换弹功能。",
        "",
        "| 细分材质类别 | family 数 |",
        "|---|---:|",
    ])
    for material_class, count in data["detailed_material_classes"].items():
        lines.append(f"| {detailed_material_class_label(material_class)} (`{material_class}`) | {count} |")

    lines.extend([
        "",
        "## 当前需要补细分材质的 family",
        "",
        "下列条目没有 `exact_existing_material`。`gun_ids` 是受影响的已审计接收机；",
        "它们的服务器库存、容量与制造出口已经生效，缺的是细分授权美术，而不是功能。",
        "",
        "| 玩家可见名称（全部容量） | Family | Ammo | Mechanism | 细分材质分类 | 当前材料状态 |",
        "|---|---|---|---|---|---|",
    ])
    for carrier in data["carrier_material_records"]:
        if not carrier["needs_detailed_material"]:
            continue
        variants = carrier["display_name_variants"]
        if variants:
            visible_names = "<br>".join(
                f"{variant['capacity']} 发：{variant['zh_cn']}" for variant in variants
            )
        else:
            visible_names = "未载入名称（请补 display_name）"
        escaped_names = visible_names.replace("|", "\\|")
        lines.append(
            f"| {escaped_names} | `{carrier['family']}` | `{carrier['ammo']}` | "
            f"`{carrier['mechanism']}` | `{carrier['material_class']}` | "
            f"`{carrier['material_state']}` → `{carrier['current_texture']}` |"
        )
    lines.extend([
        "",
        "## 当前保持 legacy 的功能记录",
        "",
        "这些枪不是“未处理”：每条都已有明确事实 profile。只有补足真实物理 carrier、膛内/转轮状态或原包脚本 feed 点后，",
        "才会从 legacy 转为 active，不允许用一张视觉弹匣替代该功能。",
        "",
        "| GunId | 已知 device | Action | 原因 |",
        "|---|---|---|---|",
    ])
    for function in data["legacy_function_records"]:
        reason = str(function["reason"]).replace("|", "\\|")
        lines.append(
            f"| `{function['gun_id']}` | `{function['device']}` | `{function['action']}` | {reason} |"
        )
    lines.append("")
    return "\n".join(lines)


def write_or_check(path: Path, content: str, write: bool) -> bool:
    existing = path.read_text(encoding="utf-8") if path.exists() else None
    if existing == content:
        return False
    if write:
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(content, encoding="utf-8")
    return True


def main() -> int:
    parser = argparse.ArgumentParser()
    group = parser.add_mutually_exclusive_group(required=True)
    group.add_argument("--write", action="store_true")
    group.add_argument("--check", action="store_true")
    args = parser.parse_args()
    data = registry()
    json_text = json.dumps(data, ensure_ascii=False, indent=2) + "\n"
    markdown = render_document(data)
    stale = [
        path for path, content in ((OUTPUT_JSON, json_text), (OUTPUT_DOC, markdown))
        if write_or_check(path, content, args.write)
    ]
    if stale:
        mode = "wrote" if args.write else "checked"
        print(f"Third-party feed gap register {mode} {len(stale)} stale output(s):")
        for path in stale:
            print(f"  {path.relative_to(ROOT)}")
        return 0 if args.write else 1
    print(
        f"Third-party feed gap register checked: {len(data['carrier_material_records'])} carrier families, "
        f"{len(data['legacy_function_records'])} legacy function records."
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
