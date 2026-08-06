#!/usr/bin/env python3
"""Generate deterministic TACZ industrial platform resources.

This is deliberately an authoring-time tool. It writes ordinary resource JSON
that remains inspectable and overridable by datapacks at runtime. See
``tools/industry/README.md`` for usage.
"""
from __future__ import annotations

import argparse
import hashlib
import json
import re
import struct
import sys
import zipfile
import zlib
from collections import Counter, defaultdict
from pathlib import Path, PurePosixPath
from typing import Any

REPO = Path(__file__).resolve().parents[1]
RESOURCE_ROOT = REPO / "src/main/resources"
PLATFORM_ROOT = REPO / "tools/industry/platforms"
CARTRIDGE_MANIFEST = REPO / "tools/industry/cartridges.json"
DEFAULT_GUN_POLICY = REPO / "tools/industry/default_gun_policy.json"
MACHINE_MANIFEST = REPO / "tools/industry/machines.json"
BLUEPRINT_ACQUISITION_MANIFEST = REPO / "tools/industry/blueprint_acquisition.json"
ICON_MAPPING_MANIFEST = REPO / "tools/industry/icon_mapping.json"
FIXED_ICON_PACK = REPO / "extras/icon_packs/TACZ_icons_pack_fixed.zip"
COMPLETE_EXTRA_PACK = REPO / "extras/icon_packs/TACZ_extra_COMPLETE.zip"
INDUSTRY_BLOCK_PACK = REPO / "extras/industry_packs/TACZ_industry_blocks.zip"
ICON_RUNTIME_MAPPING = RESOURCE_ROOT / "assets/tacz/industry_icons/default.json"
COMPLETE_RUNTIME_MAPPING = RESOURCE_ROOT / "assets/tacz/industry_icons/complete.json"
ICON_CATALOG = REPO / "extras/icon_packs/TACZ_industry_icon_catalog.json"
ICON_COVERAGE_DOCUMENT = REPO / "docs/INDUSTRY_ICON_COVERAGE.md"
COMPLETE_PACK_REPORT = REPO / "extras/icon_packs/TACZ_extra_COMPLETE_compatibility_report.json"
COMPLETE_PACK_COVERAGE_DOCUMENT = REPO / "docs/TACZ_EXTRA_COMPLETE_COMPATIBILITY.md"
ICON_GEOMETRY_MANIFEST = REPO / "tools/industry/icon_geometry_overrides.json"
ICON_GEOMETRY_REPORT = REPO / "extras/icon_packs/TACZ_extra_COMPLETE_geometry_report.json"
ICON_GEOMETRY_DOCUMENT = REPO / "docs/TACZ_EXTRA_COMPLETE_GEOMETRY_AUDIT.md"
INDUSTRY_BLOCK_ASSET_REPORT = REPO / "extras/industry_packs/TACZ_industry_blocks_asset_report.json"
INDUSTRY_BLOCK_COVERAGE_DOCUMENT = REPO / "docs/INDUSTRY_BLOCK_ASSET_COVERAGE.md"
DEFAULT_AMMO_RECIPE_ROOT = RESOURCE_ROOT / "assets/tacz/custom/tacz_default_gun/data/tacz/recipe/ammo"
DEFAULT_AMMO_INDEX_ROOT = RESOURCE_ROOT / "assets/tacz/custom/tacz_default_gun/data/tacz/index/ammo"
DEFAULT_GUN_DATA_ROOT = RESOURCE_ROOT / "assets/tacz/custom/tacz_default_gun/data/tacz/data/guns"

CREATE_CONDITIONS = [{"condition": "fabric:all_mods_loaded", "values": ["create"]}]
STRUCTURAL_ORDER = ("receiver", "bolt", "barrel", "trigger", "recoil")
# 26.2 serialises minecraft:max_stack_size in the inclusive [1, 99] range.
MAX_ITEM_STACK_SIZE = 99
# (minimum neutral case/projectile blank mass, minimum industrial propellant)
# Curated from the bundled cartridge's ballistic role; the legacy material
# recipe is checked separately as a per-round regression floor.
MANUFACTURING_TIER_IDS = ("legacy", "service", "advanced", "precision")

BALANCE_TIER_MINIMUMS = {
    "rimfire": (1, 1),
    "pistol": (1, 2),
    "personal_defense": (1, 2),
    "intermediate_rifle": (1, 3),
    "full_rifle": (2, 4),
    "lever_magnum": (2, 5),
    "magnum_handgun": (2, 3),
    "magnum_rifle": (3, 6),
    "anti_material": (4, 8),
    "shotgun": (2, 3),
    "explosive": (3, 2),
    "rocket": (4, 6),
}


def read_json(path: Path) -> Any:
    return json.loads(path.read_text(encoding="utf-8"))


def write_json(path: Path, value: Any) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


def read_json5(path: Path) -> Any:
    """Small JSON5 reader for the bundled gun-pack data files (comments/trailing commas only)."""
    text = path.read_text(encoding="utf-8")
    out: list[str] = []
    i = 0
    in_string = False
    escaped = False
    while i < len(text):
        char = text[i]
        next_char = text[i + 1] if i + 1 < len(text) else ""
        if in_string:
            out.append(char)
            if escaped:
                escaped = False
            elif char == "\\":
                escaped = True
            elif char == '"':
                in_string = False
            i += 1
            continue
        if char == '"':
            in_string = True
            out.append(char)
            i += 1
        elif char == "/" and next_char == "/":
            i = text.find("\n", i)
            if i < 0:
                break
        elif char == "/" and next_char == "*":
            end = text.find("*/", i + 2)
            i = len(text) if end < 0 else end + 2
        else:
            out.append(char)
            i += 1
    return json.loads(re.sub(r",\s*([}\]])", r"\1", "".join(out)))


def partial(item: str, nbt: dict[str, Any]) -> dict[str, Any]:
    return {"fabric:type": "forge:partial_nbt", "items": [item], "nbt": nbt}


def output(item: str, nbt: dict[str, Any], max_stack_size: int | None = None) -> dict[str, Any]:
    components: dict[str, Any] = {"minecraft:custom_data": nbt}
    if max_stack_size is not None:
        components["minecraft:max_stack_size"] = max(1, min(MAX_ITEM_STACK_SIZE, max_stack_size))
    return {"id": item, "components": components}


def deploying(target: Any, held: Any, result: dict[str, Any], keep: bool = True) -> dict[str, Any]:
    recipe: dict[str, Any] = {
        "fabric:load_conditions": CREATE_CONDITIONS,
        "type": "create:deploying",
        "target": target,
        "ingredient": held,
        "results": [result],
    }
    if keep:
        recipe["keep_held_item"] = True
    return recipe


def canonical(value: Any) -> str:
    return json.dumps(value, ensure_ascii=False, sort_keys=True, separators=(",", ":"))


def require_string(data: dict[str, Any], key: str, source: Path) -> str:
    value = data.get(key)
    if not isinstance(value, str) or not value:
        raise ValueError(f"{source}: missing non-empty string '{key}'")
    return value


def load_platforms() -> list[dict[str, Any]]:
    platforms: list[dict[str, Any]] = []
    seen: dict[str, set[str]] = {"slug": set(), "platform": set(), "gun_id": set()}
    blueprint_signatures: set[str] = set()
    for path in sorted(PLATFORM_ROOT.glob("*.json")):
        data = read_json(path)
        if not isinstance(data, dict):
            raise ValueError(f"{path}: platform manifest must be an object")
        for key in seen:
            value = require_string(data, key, path)
            if value in seen[key]:
                raise ValueError(f"{path}: duplicate {key} '{value}'")
            seen[key].add(value)
        if require_string(data, "fire_mode", path) not in {"AUTO", "SEMI", "BURST"}:
            raise ValueError(f"{path}: unsupported fire_mode")
        tier = require_string(data, "manufacturing_tier", path)
        if tier not in MANUFACTURING_TIER_IDS:
            raise ValueError(f"{path}: unsupported manufacturing_tier '{tier}'")

        blueprint = data.get("blueprint")
        if not isinstance(blueprint, dict):
            raise ValueError(f"{path}: missing blueprint object")
        for key in ("display_name", "name_en", "name_zh"):
            require_string(blueprint, key, path)
        ingredients = blueprint.get("ingredients")
        if not isinstance(ingredients, list) or not all(isinstance(v, str) and v for v in ingredients):
            raise ValueError(f"{path}: blueprint.ingredients must be item ids")
        signature = canonical(sorted(ingredients))
        if signature in blueprint_signatures:
            raise ValueError(f"{path}: duplicate compacting blueprint ingredient signature")
        blueprint_signatures.add(signature)

        parts = data.get("parts")
        if not isinstance(parts, list) or len(parts) != len(STRUCTURAL_ORDER):
            raise ValueError(f"{path}: exactly five structural parts are required")
        structural = []
        for part in parts:
            if not isinstance(part, dict):
                raise ValueError(f"{path}: part must be object")
            for key in ("structural", "kind", "name_en", "name_zh"):
                require_string(part, key, path)
            structural.append(part["structural"])
        if tuple(structural) != STRUCTURAL_ORDER:
            raise ValueError(f"{path}: parts must be ordered {STRUCTURAL_ORDER}")

        materials = data.get("materials")
        if not isinstance(materials, list):
            raise ValueError(f"{path}: materials must be a list")
        for material in materials:
            if not isinstance(material, dict) or not isinstance(material.get("item"), str) or not isinstance(material.get("count"), int):
                raise ValueError(f"{path}: invalid material")
            if material["count"] < 1:
                raise ValueError(f"{path}: material count must be positive")

        incomplete = data.get("incomplete")
        if not isinstance(incomplete, dict):
            raise ValueError(f"{path}: missing incomplete object")
        require_string(incomplete, "name_en", path)
        require_string(incomplete, "name_zh", path)
        platforms.append(data)
    if not platforms:
        raise ValueError("No platform manifests found")
    return platforms


def load_blueprint_acquisition() -> dict[str, Any]:
    """Validate tiered blueprint access rather than one flat master-trade pool.

    Legacy patterns are reproducible field documentation; service schematics are
    licensed weaponsmith stock; advanced and precision dossiers are expedition
    finds. Every tier still has a deterministic industrial blueprint recipe so
    random loot enriches progression but never makes a platform unobtainable.
    """
    data = read_json(BLUEPRINT_ACQUISITION_MANIFEST)
    if not isinstance(data, dict) or not isinstance(data.get("tiers"), dict):
        raise ValueError(f"{BLUEPRINT_ACQUISITION_MANIFEST}: expected tiers object")
    tiers = data["tiers"]
    if set(tiers) != set(MANUFACTURING_TIER_IDS):
        raise ValueError(f"{BLUEPRINT_ACQUISITION_MANIFEST}: tiers must be exactly {MANUFACTURING_TIER_IDS}")
    ranks: set[int] = set()
    for tier_id in MANUFACTURING_TIER_IDS:
        tier = tiers[tier_id]
        if not isinstance(tier, dict):
            raise ValueError(f"{BLUEPRINT_ACQUISITION_MANIFEST}: {tier_id} must be an object")
        rank = tier.get("rank")
        if not isinstance(rank, int) or rank < 1 or rank in ranks:
            raise ValueError(f"{BLUEPRINT_ACQUISITION_MANIFEST}: {tier_id}.rank must be a unique positive int")
        ranks.add(rank)
        for key in ("label_en", "label_zh"):
            require_string(tier, key, BLUEPRINT_ACQUISITION_MANIFEST)
        cache = tier.get("world_cache")
        if not isinstance(cache, dict):
            raise ValueError(f"{BLUEPRINT_ACQUISITION_MANIFEST}: {tier_id}.world_cache must be an object")
        chance = cache.get("chance")
        tables = cache.get("loot_tables")
        if not isinstance(chance, (int, float)) or not 0 < float(chance) <= 1:
            raise ValueError(f"{BLUEPRINT_ACQUISITION_MANIFEST}: {tier_id}.world_cache.chance must be in (0, 1]")
        if not isinstance(tables, list) or not tables or not all(isinstance(value, str) and value for value in tables):
            raise ValueError(f"{BLUEPRINT_ACQUISITION_MANIFEST}: {tier_id}.world_cache.loot_tables must be non-empty ids")
        trade = tier.get("weaponsmith")
        if trade is not None:
            if not isinstance(trade, dict):
                raise ValueError(f"{BLUEPRINT_ACQUISITION_MANIFEST}: {tier_id}.weaponsmith must be an object or absent")
            level = trade.get("level")
            if not isinstance(level, int) or not 1 <= level <= 5:
                raise ValueError(f"{BLUEPRINT_ACQUISITION_MANIFEST}: {tier_id}.weaponsmith.level must be in [1,5]")
            for key in ("emerald_base", "emerald_per_material", "max_uses", "xp"):
                if not isinstance(trade.get(key), int) or trade[key] < 1:
                    raise ValueError(f"{BLUEPRINT_ACQUISITION_MANIFEST}: {tier_id}.weaponsmith.{key} must be a positive int")
    return data


def humanize_slug(slug: str) -> str:
    return slug.replace("_", " ").upper()


def first_fire_mode(data_path: Path) -> str:
    if not data_path.exists():
        return "SEMI"
    try:
        data = read_json5(data_path)
        modes = data.get("fire_mode", []) if isinstance(data, dict) else []
        if isinstance(modes, list) and modes and isinstance(modes[0], str):
            mode = modes[0].upper()
            return mode if mode in {"AUTO", "SEMI", "BURST"} else "SEMI"
    except (OSError, ValueError, json.JSONDecodeError):
        pass
    return "SEMI"


def discover_default_platforms(explicit_slugs: set[str]) -> list[dict[str, Any]]:
    """Create high-fidelity generic platforms for every remaining bundled gun.

    This reads the bundled default gun-pack at authoring time only. Players get
    the committed ordinary generated recipes and never run this script.
    """
    policy = read_json(DEFAULT_GUN_POLICY)
    recipe_root = RESOURCE_ROOT / "assets/tacz/custom/tacz_default_gun/data/tacz/recipe/gun"
    index_root = RESOURCE_ROOT / "assets/tacz/custom/tacz_default_gun/data/tacz/index/guns"
    data_root = RESOURCE_ROOT / "assets/tacz/custom/tacz_default_gun/data/tacz/data/guns"
    base_ingredients = policy["base_blueprint_ingredients"]
    reserved = set(policy["reserved_blueprint_seeds"])
    seeds = [seed for seed in policy["blueprint_seed_items"] if seed not in reserved]
    tier_by_type = policy.get("tier_by_gun_type")
    tier_overrides = policy.get("tier_overrides")
    if not isinstance(tier_by_type, dict) or not isinstance(tier_overrides, dict):
        raise ValueError(f"{DEFAULT_GUN_POLICY}: tier_by_gun_type and tier_overrides are required objects")
    if any(value not in MANUFACTURING_TIER_IDS for value in tier_by_type.values()) \
            or any(value not in MANUFACTURING_TIER_IDS for value in tier_overrides.values()):
        raise ValueError(f"{DEFAULT_GUN_POLICY}: unknown manufacturing tier")
    missing: list[tuple[str, dict[str, Any], dict[str, Any]]] = []
    for recipe_path in sorted(recipe_root.glob("*.json")):
        slug = recipe_path.stem
        if slug in explicit_slugs:
            continue
        recipe = read_json(recipe_path)
        result = recipe.get("result", {})
        if not isinstance(result, dict) or result.get("type") != "gun" or not isinstance(result.get("id"), str):
            continue
        index_path = index_root / f"{slug}.json"
        index = read_json5(index_path) if index_path.exists() else {}
        missing.append((slug, recipe, index if isinstance(index, dict) else {}))
    if len(missing) > len(seeds):
        raise ValueError(f"{DEFAULT_GUN_POLICY}: not enough unique blueprint seed items")

    platforms: list[dict[str, Any]] = []
    for (slug, recipe, index), seed in zip(missing, seeds):
        result = recipe["result"]
        gun_type = index.get("type") if isinstance(index.get("type"), str) else "default"
        data_id = index.get("data") if isinstance(index.get("data"), str) else f"tacz:{slug}_data"
        data_path = data_root / f"{data_id.split(':', 1)[-1]}.json"
        display = humanize_slug(slug)
        handgun = gun_type == "pistol"
        parts = [
            {"structural": "receiver", "kind": "frame" if handgun else "receiver",
             "name_en": f"{display} {'Frame' if handgun else 'Receiver'}",
             "name_zh": f"{display} {'枪身' if handgun else '机匣'}"},
            {"structural": "bolt", "kind": "slide" if handgun else "bolt",
             "name_en": f"{display} {'Slide' if handgun else 'Bolt Group'}",
             "name_zh": f"{display} {'套筒' if handgun else '枪机组'}"},
            {"structural": "barrel", "kind": "barrel", "name_en": f"{display} Barrel", "name_zh": f"{display} 枪管"},
            {"structural": "trigger", "kind": "trigger", "name_en": f"{display} Fire-Control Group", "name_zh": f"{display} 击发组"},
            {"structural": "recoil", "kind": "recoil", "name_en": f"{display} Recoil Assembly", "name_zh": f"{display} 复进组件"},
        ]
        materials = policy["materials_by_gun_type"].get(gun_type, policy["materials_by_gun_type"]["default"])
        tier = tier_overrides.get(slug, tier_by_type.get(gun_type, tier_by_type.get("default")))
        if tier not in MANUFACTURING_TIER_IDS:
            raise ValueError(f"{DEFAULT_GUN_POLICY}: no manufacturing tier for {slug}")
        platform = f"default_{slug}"
        platforms.append({
            "slug": slug,
            "platform": platform,
            "gun_id": result["id"],
            "manufacturing_tier": tier,
            "fire_mode": first_fire_mode(data_path),
            "blueprint": {
                "display_name": f"item.tacz.gun_blueprint.{platform}",
                "ingredients": [*base_ingredients, seed],
                "name_en": f"{display} Industrial Blueprint",
                "name_zh": f"{display} 工业装配模板",
            },
            "parts": parts,
            "materials": materials,
            "incomplete": {"name_en": f"Incomplete {display} Assembly", "name_zh": f"未完成的 {display} 总成"},
        })
    return platforms


def default_ammo_recipe_ids() -> set[str]:
    """Return every real default-pack loose-ammo result id.

    The default gun pack is read only as an authoring/CI input.  Keeping this
    check here prevents a future bundled cartridge from quietly falling back to
    the generic gun-table material gate instead of receiving a dedicated
    four-slot assembly route.
    """
    ids: set[str] = set()
    for path in sorted(DEFAULT_AMMO_RECIPE_ROOT.glob("*.json")):
        recipe = read_json(path)
        result = recipe.get("result") if isinstance(recipe, dict) else None
        if not isinstance(result, dict) or result.get("type") != "ammo":
            continue
        ammo = result.get("id")
        if not isinstance(ammo, str) or not ammo:
            raise ValueError(f"{path}: default ammo result has no id")
        ids.add(ammo)
    if not ids:
        raise ValueError(f"{DEFAULT_AMMO_RECIPE_ROOT}: no default ammo recipe ids found")
    return ids


def default_ammo_recipe_stats() -> dict[str, dict[str, int]]:
    """Read legacy batch size and powder demand as a balance regression floor."""
    stats: dict[str, dict[str, int]] = {}
    for recipe_path in sorted(DEFAULT_AMMO_RECIPE_ROOT.glob("*.json")):
        recipe = read_json(recipe_path)
        result = recipe.get("result") if isinstance(recipe, dict) else None
        if not isinstance(result, dict) or result.get("type") != "ammo":
            continue
        ammo = result.get("id")
        output_count = result.get("count", 1)
        if not isinstance(ammo, str) or not ammo or not isinstance(output_count, int) or output_count < 1:
            raise ValueError(f"{recipe_path}: invalid default ammo result")
        gunpowder = 0
        materials = recipe.get("materials", [])
        if isinstance(materials, list):
            for material in materials:
                if isinstance(material, dict) and material.get("item") == "#c:gunpowders":
                    count = material.get("count", 1)
                    gunpowder += count if isinstance(count, int) and count > 0 else 1
        stats[ammo] = {"legacy_output_count": output_count, "legacy_gunpowder": gunpowder}
    return stats


def default_ammo_stack_limits() -> dict[str, int]:
    """Read the effective 26.2 stack cap for each bundled loose-ammo output.

    The ammo item's own builder clamps gun-pack ``stack_size`` to 99 before
    writing ``minecraft:max_stack_size``.  Case/projectile outputs must use the
    same effective cap, not the old generic intermediary limit of 16.
    """
    limits: dict[str, int] = {}
    for recipe_path in sorted(DEFAULT_AMMO_RECIPE_ROOT.glob("*.json")):
        recipe = read_json(recipe_path)
        result = recipe.get("result") if isinstance(recipe, dict) else None
        if not isinstance(result, dict) or result.get("type") != "ammo":
            continue
        ammo = result.get("id")
        if not isinstance(ammo, str) or not ammo:
            continue
        index_path = DEFAULT_AMMO_INDEX_ROOT / recipe_path.name
        if not index_path.exists():
            raise ValueError(f"{recipe_path}: no matching default ammo index for stack limit")
        index = read_json5(index_path)
        raw_limit = index.get("stack_size") if isinstance(index, dict) else None
        if not isinstance(raw_limit, int) or raw_limit < 1:
            raise ValueError(f"{index_path}: missing positive stack_size")
        limits[ammo] = max(1, min(MAX_ITEM_STACK_SIZE, raw_limit))
    return limits


def validate_master_gun(caliber: dict[str, Any]) -> None:
    """Ensure a gun used as a physical chamber gauge actually fires this ammo."""
    master = caliber.get("master_gun")
    if not isinstance(master, str) or not master:
        return
    slug = master.split(":", 1)[-1]
    path = DEFAULT_GUN_DATA_ROOT / f"{slug}_data.json"
    if not path.exists():
        raise ValueError(f"{CARTRIDGE_MANIFEST}: master_gun '{master}' has no bundled gun data")
    data = read_json5(path)
    if not isinstance(data, dict) or data.get("ammo") != caliber["ammo"]:
        raise ValueError(
            f"{CARTRIDGE_MANIFEST}: master_gun '{master}' is not chambered for '{caliber['ammo']}'"
        )


def load_cartridges() -> list[dict[str, Any]]:
    manifest = read_json(CARTRIDGE_MANIFEST)
    entries = manifest.get("calibers") if isinstance(manifest, dict) else None
    if not isinstance(entries, list) or not entries:
        raise ValueError(f"{CARTRIDGE_MANIFEST}: 'calibers' must be a non-empty list")
    required = (
        "id", "ammo", "projectile_type", "eject_case", "balance_tier",
        "batch_count", "propellant_count", "case_blank_count", "projectile_blank_count",
        "case_name_en", "case_name_zh", "projectile_name_en", "projectile_name_zh",
        "case_die_name_en", "case_die_name_zh", "projectile_die_name_en", "projectile_die_name_zh",
    )
    seen_ids: set[str] = set()
    seen_ammo: set[str] = set()
    seen_gauge_datums: set[str] = set()
    for entry in entries:
        if not isinstance(entry, dict):
            raise ValueError(f"{CARTRIDGE_MANIFEST}: each caliber must be an object")
        for key in required:
            value = entry.get(key)
            if key == "eject_case":
                if not isinstance(value, bool):
                    raise ValueError(f"{CARTRIDGE_MANIFEST}: caliber '{entry.get('id', '?')}' must declare boolean eject_case")
            elif key in {"batch_count", "propellant_count", "case_blank_count", "projectile_blank_count"}:
                if not isinstance(value, int) or not 1 <= value <= MAX_ITEM_STACK_SIZE:
                    raise ValueError(f"{CARTRIDGE_MANIFEST}: caliber '{entry.get('id', '?')}' needs {key} in [1, {MAX_ITEM_STACK_SIZE}]")
            elif not isinstance(value, str) or not value:
                raise ValueError(f"{CARTRIDGE_MANIFEST}: caliber missing '{key}'")
        if entry["balance_tier"] not in BALANCE_TIER_MINIMUMS:
            raise ValueError(f"{CARTRIDGE_MANIFEST}: unknown balance_tier '{entry['balance_tier']}'")
        if entry["id"] in seen_ids or entry["ammo"] in seen_ammo:
            raise ValueError(f"{CARTRIDGE_MANIFEST}: duplicate caliber id or ammo id")
        seen_ids.add(entry["id"])
        seen_ammo.add(entry["ammo"])

        master_gun = entry.get("master_gun")
        gauge = entry.get("calibration_gauge")
        has_master = isinstance(master_gun, str) and bool(master_gun)
        has_gauge = isinstance(gauge, dict)
        if has_master == has_gauge:
            raise ValueError(
                f"{CARTRIDGE_MANIFEST}: caliber '{entry['id']}' needs exactly one physical calibration source "
                "(master_gun or calibration_gauge)"
            )
        if has_master:
            validate_master_gun(entry)
        else:
            assert isinstance(gauge, dict)
            for key in ("datum", "name_en", "name_zh"):
                value = gauge.get(key)
                if not isinstance(value, str) or not value:
                    raise ValueError(f"{CARTRIDGE_MANIFEST}: calibration_gauge for '{entry['id']}' missing '{key}'")
            datum = gauge["datum"]
            if datum in seen_gauge_datums:
                raise ValueError(f"{CARTRIDGE_MANIFEST}: duplicate physical calibration datum '{datum}'")
            seen_gauge_datums.add(datum)

        if entry["eject_case"]:
            for key in ("spent_case_name_en", "spent_case_name_zh"):
                value = entry.get(key)
                if not isinstance(value, str) or not value:
                    raise ValueError(f"{CARTRIDGE_MANIFEST}: ejecting caliber '{entry['id']}' missing '{key}'")

        payloads = entry.get("projectile_payloads", [])
        if not isinstance(payloads, list):
            raise ValueError(f"{CARTRIDGE_MANIFEST}: projectile_payloads for '{entry['id']}' must be a list")
        for payload in payloads:
            if not isinstance(payload, dict) or not isinstance(payload.get("item"), str) or not payload["item"] \
                    or not isinstance(payload.get("count"), int) or payload["count"] < 1:
                raise ValueError(f"{CARTRIDGE_MANIFEST}: invalid projectile payload for '{entry['id']}'")
        if payloads:
            for key in ("projectile_body_name_en", "projectile_body_name_zh", "projectile_payload_names_en", "projectile_payload_names_zh"):
                value = entry.get(key)
                if key.startswith("projectile_payload_names"):
                    expected_stages = sum(payload["count"] for payload in payloads)
                    if not isinstance(value, list) or len(value) != expected_stages \
                            or not all(isinstance(name, str) and name for name in value):
                        raise ValueError(f"{CARTRIDGE_MANIFEST}: {key} for '{entry['id']}' must name every payload stage")
                elif not isinstance(value, str) or not value:
                    raise ValueError(f"{CARTRIDGE_MANIFEST}: {key} for '{entry['id']}' must be non-empty")
    expected_default_ammo = default_ammo_recipe_ids()
    missing = expected_default_ammo - seen_ammo
    unexpected = seen_ammo - expected_default_ammo
    if missing or unexpected:
        details: list[str] = []
        if missing:
            details.append("missing default ammo: " + ", ".join(sorted(missing)))
        if unexpected:
            details.append("non-default ammo: " + ", ".join(sorted(unexpected)))
        raise ValueError(f"{CARTRIDGE_MANIFEST}: full default-pack cartridge coverage failed ({'; '.join(details)})")
    stack_limits = default_ammo_stack_limits()
    recipe_stats = default_ammo_recipe_stats()
    if set(stack_limits) != seen_ammo or set(recipe_stats) != seen_ammo:
        raise ValueError(f"{CARTRIDGE_MANIFEST}: default ammo stack-limit/legacy-stat coverage failed")
    # Derived authoring metadata: it is emitted to the actual case/projectile
    # ItemStack components, never written back into the source manifest.
    for entry in entries:
        ammo = entry["ammo"]
        batch = entry["batch_count"]
        cap = stack_limits[ammo]
        stats = recipe_stats[ammo]
        mass_minimum, tier_propellant_minimum = BALANCE_TIER_MINIMUMS[entry["balance_tier"]]
        if entry["case_blank_count"] < mass_minimum or entry["projectile_blank_count"] < mass_minimum:
            raise ValueError(f"{CARTRIDGE_MANIFEST}: {entry['id']} blank mass is below its ballistic tier")
        if entry["propellant_count"] < tier_propellant_minimum:
            raise ValueError(f"{CARTRIDGE_MANIFEST}: {entry['id']} propellant is below its ballistic tier")
        if batch > cap:
            raise ValueError(f"{CARTRIDGE_MANIFEST}: {entry['id']} batch exceeds final ammo stack cap")
        if batch > stats["legacy_output_count"]:
            raise ValueError(f"{CARTRIDGE_MANIFEST}: {entry['id']} batch exceeds its legacy material batch")
        # Retain at least the old per-round gunpowder burden. Higher tier
        # cartridges deliberately exceed this floor; they never become cheaper
        # merely because final assembly is now industrial and batched.
        minimum_propellant = max(1, -(-batch * stats["legacy_gunpowder"] // stats["legacy_output_count"]))
        if entry["propellant_count"] < minimum_propellant:
            raise ValueError(
                f"{CARTRIDGE_MANIFEST}: {entry['id']} propellant_count is below legacy per-round floor "
                f"({entry['propellant_count']} < {minimum_propellant})"
            )
        entry["_product_stack_limit"] = cap
    return entries


def furniture_blank_tag() -> dict[str, Any]:
    return {
        "IndustryPlatform": "machining",
        "IndustryPartKind": "furniture_blank",
        "IndustryDisplayName": "item.tacz.gun_component_blank.furniture",
    }


def normalized_materials(materials: list[dict[str, Any]]) -> list[tuple[str, int]]:
    counts: dict[str, int] = {}
    for material in materials:
        counts[material["item"]] = counts.get(material["item"], 0) + material["count"]
    return sorted(counts.items())


def furniture_signature(materials: list[dict[str, Any]]) -> str:
    return "__".join(f"{item.replace(':', '_').replace('/', '_')}_{count}" for item, count in normalized_materials(materials))


def furniture_kit_tag(platform: dict[str, Any]) -> dict[str, Any]:
    name = platform["platform"]
    return {
        "IndustryPlatform": name,
        "IndustryPartKind": "furniture_kit",
        "IndustryDisplayName": f"item.tacz.gun_component.{name}_furniture_kit",
    }


def platform_display_label(platform: dict[str, Any], language: str) -> str:
    name = platform["blueprint"]["name_zh" if language == "zh_cn" else "name_en"]
    suffixes = (" 工业装配模板", " 平台装配模板") if language == "zh_cn" else (
        " Industrial Blueprint", " Platform Assembly Blueprint"
    )
    for suffix in suffixes:
        if name.endswith(suffix):
            return name[:-len(suffix)]
    return humanize_slug(platform["slug"])


def generated_furniture_blank_files(platforms: list[dict[str, Any]]) -> dict[Path, Any]:
    """Material-only multi-slot fabrication of neutral exterior/furniture blanks.

    Identical raw material signatures deliberately share one neutral result.
    A separate blueprint-held Deployer calibration below chooses the actual
    firearm exterior kit, so no duplicate mechanical-crafting inputs claim
    different platform outputs.
    """
    files: dict[Path, Any] = {}
    for platform in platforms:
        materials = platform["materials"]
        if not materials:
            continue
        signature = furniture_signature(materials)
        path = RESOURCE_ROOT / f"data/tacz/recipe/create/industry/form_furniture_blank_{signature}.json"
        if path in files:
            continue
        ordered = normalized_materials(materials)
        expanded = [item for item, count in ordered for _ in range(count)]
        if len(expanded) > 9:
            raise ValueError(f"{platform['slug']}: furniture material grid exceeds mechanical-crafter 3×3 capacity")
        symbols = "ABCDEFGHIJKLMNOPQRSTUVWXYZ"
        symbol_by_item = {item: symbols[index] for index, (item, _) in enumerate(ordered)}
        slots = [symbol_by_item[item] for item in expanded]
        rows = ["".join((slots + [" "] * 9)[index:index + 3]) for index in range(0, 9, 3)]
        while rows and rows[-1].strip() == "":
            rows.pop()
        files[path] = {
            "fabric:load_conditions": CREATE_CONDITIONS,
            "type": "create:mechanical_crafting",
            "key": {symbol_by_item[item]: item for item, _ in ordered},
            "pattern": rows,
            "result": output("tacz:gun_component_blank", furniture_blank_tag()),
        }
    return files



def manufacturing_tier(platform: dict[str, Any]) -> str:
    tier = platform.get("manufacturing_tier")
    if tier not in MANUFACTURING_TIER_IDS:
        raise ValueError(f"{platform.get('slug', '?')}: missing valid manufacturing_tier")
    return tier


def blueprint_tag(platform: dict[str, Any]) -> dict[str, Any]:
    return {
        "IndustryPlatform": platform["platform"],
        "IndustryPartKind": "blueprint",
        "IndustryDisplayName": platform["blueprint"]["display_name"],
        "IndustryBlueprintTier": manufacturing_tier(platform),
    }


def blueprint_match_tag(platform: dict[str, Any]) -> dict[str, Any]:
    """Tier is provenance/display metadata; old platform blueprints remain usable."""
    tag = blueprint_tag(platform)
    tag.pop("IndustryBlueprintTier")
    return tag


def blueprint_seed(platform: dict[str, Any]) -> str:
    ingredients = platform["blueprint"]["ingredients"]
    if not ingredients:
        raise ValueError(f"{platform['slug']}: blueprint has no seed ingredient")
    return ingredients[-1]


def generated_tier_blueprint_recipe(platform: dict[str, Any]) -> dict[str, Any]:
    """Emit a materially distinct, deterministic blueprint route by tech tier."""
    tier = manufacturing_tier(platform)
    seed = blueprint_seed(platform)
    result = output("tacz:gun_blueprint", blueprint_tag(platform))
    if tier == "legacy":
        # A field manual: two sheets and a recognisable local datum. It is
        # intentionally Basin/press accessible and does not require a modern
        # tooling license.
        return {
            "fabric:load_conditions": CREATE_CONDITIONS,
            "type": "create:compacting",
            "ingredients": ["minecraft:paper", "minecraft:paper", seed],
            "results": [result],
        }

    if tier == "service":
        key = {"P": "minecraft:paper", "B": "create:brass_sheet", "S": "tacz:high_carbon_steel_plate", "K": seed}
        pattern = ["PBP", "SK "]
    elif tier == "advanced":
        key = {
            "P": "minecraft:paper",
            "B": "create:brass_sheet",
            "S": "tacz:high_carbon_steel_plate",
            "R": "minecraft:redstone",
            "K": seed,
        }
        pattern = ["PBP", "SKS", " R "]
    elif tier == "precision":
        key = {
            "P": "minecraft:paper",
            "B": "create:brass_sheet",
            "S": "tacz:high_carbon_steel_plate",
            "D": "minecraft:diamond",
            "E": "minecraft:echo_shard",
            "K": seed,
        }
        pattern = ["PDP", "SKS", "BEB"]
    else:
        raise ValueError(f"Unexpected manufacturing tier {tier}")
    return {
        "fabric:load_conditions": CREATE_CONDITIONS,
        "type": "create:mechanical_crafting",
        "key": key,
        "pattern": pattern,
        "result": result,
    }

def generated_platform_files(platform: dict[str, Any]) -> dict[Path, Any]:
    slug = platform["slug"]
    name = platform["platform"]
    gun_id = platform["gun_id"]
    blueprint = platform["blueprint"]
    parts = platform["parts"]
    materials = platform["materials"]
    blueprint_key = blueprint["display_name"]
    result: dict[Path, Any] = {}

    result[RESOURCE_ROOT / f"data/tacz/recipe/create/industry/blueprint_{name}.json"] = generated_tier_blueprint_recipe(platform)

    component_entries: list[dict[str, str]] = []
    for part in parts:
        structural = part["structural"]
        final_kind = part["kind"]
        die_key = f"item.tacz.press_die.component_{name}_{final_kind}"
        component_key = f"item.tacz.gun_component.{name}_{final_kind}"
        die_blank = partial("tacz:press_die", {
            "IndustryPlatform": "machining",
            "IndustryPartKind": "die_blank",
            "IndustryDisplayName": f"item.tacz.press_die_blank.{structural}",
            "DieTargetKind": structural,
        })
        held_blueprint = partial("tacz:gun_blueprint", blueprint_match_tag(platform))
        calibrated_die = {
            "IndustryPlatform": name,
            "IndustryPartKind": "component_die",
            "IndustryDisplayName": die_key,
            "DieTargetKind": final_kind,
        }
        result[RESOURCE_ROOT / f"data/tacz/recipe/create/industry/calibrate_component_die_{name}_{structural}.json"] = deploying(
            die_blank, held_blueprint, output("tacz:press_die", calibrated_die)
        )
        structural_blank = partial("tacz:gun_component_blank", {
            "IndustryPlatform": "machining",
            "IndustryPartKind": f"{structural}_blank",
            "IndustryDisplayName": "item.tacz.gun_component_blank",
        })
        component = {
            "IndustryPlatform": name,
            "IndustryPartKind": final_kind,
            "IndustryDisplayName": component_key,
        }
        result[RESOURCE_ROOT / f"data/tacz/recipe/create/industry/form_component_{name}_{structural}.json"] = deploying(
            structural_blank, partial("tacz:press_die", calibrated_die), output("tacz:gun_component", component)
        )
        component_entries.append({"structural": structural, "kind": final_kind, "display_name": component_key})

    if materials:
        furniture_blank = partial("tacz:gun_component_blank", furniture_blank_tag())
        held_blueprint = partial("tacz:gun_blueprint", blueprint_match_tag(platform))
        result[RESOURCE_ROOT / f"data/tacz/recipe/create/industry/calibrate_furniture_{name}.json"] = deploying(
            furniture_blank, held_blueprint, output("tacz:gun_component", furniture_kit_tag(platform))
        )

    result[RESOURCE_ROOT / f"data/tacz/industry/assembly/gun/{slug}.json"] = {
        "platform": name,
        "manufacturing_tier": manufacturing_tier(platform),
        "blueprint_display_name": blueprint_key,
        "terminal_process": f"tacz:create/industry/assemble_{slug}",
        "components": component_entries,
        "materials": materials,
    }

    initial = parts[0]
    initial_component_key = f"item.tacz.gun_component.{name}_{initial['kind']}"
    def press_fit_step() -> dict[str, Any]:
        # A real Mechanical Press station: no second workpiece is introduced,
        # but the current receiver assembly must physically pass under a press
        # before the next Deployer can continue the sequential recipe.
        return {
            "type": "create:pressing",
            "ingredient": "$ingredient",
            "results": ["$result"],
        }

    sequence: list[dict[str, Any]] = [{
        "type": "create:deploying",
        "target": "$ingredient",
        "ingredient": partial("tacz:gun_blueprint", blueprint_match_tag(platform)),
        "results": ["$result"],
        "keep_held_item": True,
    }, press_fit_step()]
    # Every major receiver/bolt/barrel/fire-control/recoil joint is both fed by
    # a Deployer and mechanically press-fit. This makes the displayed sequence
    # an actual belt line of alternating stations instead of a row of hands.
    for part in parts[1:]:
        sequence.append({
            "type": "create:deploying",
            "target": "$ingredient",
            "ingredient": partial("tacz:gun_component", {
                "IndustryPlatform": name,
                "IndustryPartKind": part["kind"],
                "IndustryDisplayName": f"item.tacz.gun_component.{name}_{part['kind']}",
            }),
            "results": ["$result"],
        })
        sequence.append(press_fit_step())
    # Raw wood/leather/glass/brass is intentionally *not* deployed into a
    # finished receiver one item at a time. It first becomes a neutral
    # furniture blank in a Mechanical Crafter, then this named kit is
    # blueprint-calibrated and installed as one meaningful subassembly.
    if materials:
        furniture = furniture_kit_tag(platform)
        sequence.append({
            "type": "create:deploying",
            "target": "$ingredient",
            "ingredient": partial("tacz:gun_component", furniture),
            "results": ["$result"],
        })
    sequence.append(press_fit_step())

    result[RESOURCE_ROOT / f"data/tacz/recipe/create/industry/assemble_{slug}.json"] = {
        "fabric:load_conditions": CREATE_CONDITIONS,
        "type": "create:sequenced_assembly",
        "ingredient": partial("tacz:gun_component", {
            "IndustryPlatform": name,
            "IndustryPartKind": initial["kind"],
            "IndustryDisplayName": initial_component_key,
        }),
        "transitional_item": output("tacz:gun_component", {
            "IndustryPlatform": name,
            "IndustryPartKind": f"incomplete_{slug}",
            "IndustryDisplayName": f"item.tacz.gun_component.incomplete_{slug}",
        }),
        "result": output("tacz:modern_kinetic_gun", {
            "GunId": gun_id,
            "GunFireMode": platform["fire_mode"],
            "GunCurrentAmmoCount": 0,
            "HasBulletInBarrel": False,
            # Salvage accepts only an actual industrial terminal output, not a
            # legacy/loot gun that happens to share a GunId.
            "IndustryAssemblyPlatform": name,
            "IndustryAssemblyRecipe": f"tacz:gun/{slug}",
            "IndustryAssemblyTier": manufacturing_tier(platform),
        }),
        "sequence": sequence,
    }
    return result


def blueprint_custom_data(platform: dict[str, Any]) -> dict[str, Any]:
    return blueprint_tag(platform)


def snbt_compound(values: dict[str, Any]) -> str:
    """Small deterministic SNBT writer for blueprint custom-data strings."""
    parts: list[str] = []
    for key, value in values.items():
        if not isinstance(value, str):
            raise ValueError(f"SNBT blueprint custom data expects strings, got {key}")
        parts.append(f"{key}:{json.dumps(value, ensure_ascii=False)}")
    return "{" + ",".join(parts) + "}"



def generated_blueprint_acquisition_files(platforms: list[dict[str, Any]], acquisition: dict[str, Any]) -> dict[Path, Any]:
    """Generate tiered blueprint sources instead of a 53-entry master trade lottery.

    Direct tier recipes remain deterministic. Villager stock now supplements
    legacy/service access at appropriate career levels, while advanced/precision
    dossiers are expedition finds rather than random master-trade clutter.
    """
    files: dict[Path, Any] = {}
    tier_platforms: dict[str, list[dict[str, Any]]] = {
        tier: [] for tier in MANUFACTURING_TIER_IDS
    }
    for platform in platforms:
        tier_platforms[manufacturing_tier(platform)].append(platform)

    tags_by_level: dict[int, list[str]] = {}
    for tier_id in MANUFACTURING_TIER_IDS:
        tier = acquisition["tiers"][tier_id]
        members = sorted(tier_platforms[tier_id], key=lambda value: value["platform"])
        if not members:
            continue
        cache = tier["world_cache"]
        loot_entries: list[dict[str, Any]] = []
        for platform in members:
            custom = blueprint_custom_data(platform)
            loot_entries.append({
                "type": "minecraft:item",
                "name": "tacz:gun_blueprint",
                "functions": [{
                    # LootTableInjection.LegacyLootCompat translates this to
                    # set_custom_data before the 26.2 direct codec parses it.
                    "function": "minecraft:set_nbt",
                    "tag": snbt_compound(custom),
                }],
            })
        files[RESOURCE_ROOT / f"data/tacz/tacz_loot_injectors/industrial_blueprint_cache_{tier_id}.json"] = {
            "loot_tables": cache["loot_tables"],
            "pools": [{
                "rolls": 1,
                "conditions": [{"condition": "minecraft:random_chance", "chance": cache["chance"]}],
                "entries": loot_entries,
            }],
        }

        trade = tier.get("weaponsmith")
        if not isinstance(trade, dict):
            continue
        level = trade["level"]
        trade_ids = tags_by_level.setdefault(level, [])
        for platform in members:
            name = platform["platform"]
            custom = blueprint_custom_data(platform)
            material_weight = sum(material["count"] for material in platform["materials"])
            emerald_cost = min(
                64,
                trade["emerald_base"] + material_weight * trade["emerald_per_material"] + tier["rank"] * 2,
            )
            trade_id = f"tacz:weaponsmith/{level}/blueprint_{name}"
            trade_ids.append(trade_id)
            files[RESOURCE_ROOT / f"data/tacz/villager_trade/weaponsmith/{level}/blueprint_{name}.json"] = {
                "wants": {"id": "minecraft:emerald", "count": float(emerald_cost)},
                "additional_wants": {"id": "minecraft:book"},
                "gives": output("tacz:gun_blueprint", custom),
                "max_uses": float(trade["max_uses"]),
                "reputation_discount": 0.05,
                "xp": float(trade["xp"]),
            }

    for level, trade_ids in sorted(tags_by_level.items()):
        files[RESOURCE_ROOT / f"data/minecraft/tags/villager_trade/weaponsmith/level_{level}.json"] = {
            "replace": False,
            "values": trade_ids,
        }
    return files


def obsolete_blueprint_acquisition_files(expected: dict[Path, Any]) -> set[Path]:
    """Remove old flat level-5 trade/cache files when tiered acquisition regenerates."""
    stale: set[Path] = set()
    trade_root = RESOURCE_ROOT / "data/tacz/villager_trade/weaponsmith"
    if trade_root.exists():
        for path in trade_root.rglob("blueprint_*.json"):
            if path not in expected:
                stale.add(path)
    loot_root = RESOURCE_ROOT / "data/tacz/tacz_loot_injectors"
    if loot_root.exists():
        for path in loot_root.glob("industrial_blueprint_cache*.json"):
            if path not in expected:
                stale.add(path)
    tag_root = RESOURCE_ROOT / "data/minecraft/tags/villager_trade/weaponsmith"
    if tag_root.exists():
        for path in tag_root.glob("level_*.json"):
            # Only clean tags managed by this blueprint system. Existing
            # vanilla tags are in the game jar, not the project resource tree.
            if path.name in {"level_2.json", "level_5.json"} and path not in expected:
                stale.add(path)
    return stale


def generated_cartridge_files(caliber: dict[str, Any]) -> dict[Path, Any]:
    caliber_id = caliber["id"]
    projectile_type = caliber["projectile_type"]
    stack_limit = caliber["_product_stack_limit"]
    ammo_id = caliber["ammo"]
    case_blank_die = partial("tacz:press_die", {
        "IndustryPlatform": "ammunition",
        "IndustryPartKind": "case_die_blank",
        "IndustryDisplayName": "item.tacz.press_die_blank.case",
    })
    projectile_blank_die = partial("tacz:press_die", {
        "IndustryPlatform": "ammunition",
        "IndustryPartKind": "projectile_die_blank",
        "IndustryDisplayName": "item.tacz.press_die_blank.projectile",
    })
    case_die = {
        "IndustryPlatform": "ammunition",
        "IndustryPartKind": "case_die",
        "IndustryDisplayName": f"item.tacz.press_die.case_{caliber_id}",
        "CartridgeCaliber": caliber_id,
    }
    projectile_die = {
        "IndustryPlatform": "ammunition",
        "IndustryPartKind": "projectile_die",
        "IndustryDisplayName": f"item.tacz.press_die.projectile_{caliber_id}_{projectile_type}",
        "CartridgeCaliber": caliber_id,
        "ProjectileType": projectile_type,
    }
    case = {
        "IndustryPlatform": "ammunition",
        "IndustryPartKind": "case",
        "IndustryDisplayName": f"item.tacz.cartridge_case.{caliber_id}",
        "CartridgeCaliber": caliber_id,
        "CartridgeAmmoId": ammo_id,
    }
    spent_case = {
        "IndustryPlatform": "ammunition",
        "IndustryPartKind": "spent_case",
        "IndustryDisplayName": f"item.tacz.cartridge_case.spent_{caliber_id}",
        "CartridgeCaliber": caliber_id,
        "CartridgeAmmoId": ammo_id,
        "SpentCartridgeCase": True,
    }
    projectile = {
        "IndustryPlatform": "ammunition",
        "IndustryPartKind": "projectile",
        "IndustryDisplayName": f"item.tacz.projectile_core.{caliber_id}_{projectile_type}",
        "CartridgeCaliber": caliber_id,
        "CartridgeAmmoId": ammo_id,
        "ProjectileType": projectile_type,
    }
    case_stock = partial("tacz:cartridge_case_blank", {
        "IndustryPlatform": "ammunition",
        "IndustryPartKind": "case_blank",
        "IndustryDisplayName": "item.tacz.cartridge_case_blank",
    })
    projectile_stock = partial("tacz:projectile_blank", {
        "IndustryPlatform": "ammunition",
        "IndustryPartKind": "projectile_blank",
        "IndustryDisplayName": "item.tacz.projectile_blank",
    })
    files: dict[Path, Any] = {}

    master_gun = caliber.get("master_gun")
    if isinstance(master_gun, str) and master_gun:
        calibration_tool = partial("tacz:modern_kinetic_gun", {"GunId": master_gun})
    else:
        gauge = caliber["calibration_gauge"]
        gauge_tag = {
            "IndustryPlatform": "ammunition",
            "IndustryPartKind": "cartridge_gauge",
            "IndustryDisplayName": f"item.tacz.press_die.gauge_{caliber_id}",
            "CartridgeCaliber": caliber_id,
        }
        calibration_tool = partial("tacz:press_die", gauge_tag)
        # Some bundled loose-ammo ids intentionally have no firearm in the
        # default pack.  A real multi-slot Mechanical Crafter forms their
        # named hardened calibre gauge; this is an explicit datum, never an
        # unrelated gun pretending to be the chamber reference.
        files[RESOURCE_ROOT / f"data/tacz/recipe/create/industry/caliber_gauge_{caliber_id}.json"] = {
            "fabric:load_conditions": CREATE_CONDITIONS,
            "type": "create:mechanical_crafting",
            "key": {
                "B": "create:brass_sheet",
                "D": gauge["datum"],
                "S": "tacz:high_carbon_steel_plate",
            },
            "pattern": [
                " S ",
                "BDB",
                " S ",
            ],
            "result": output("tacz:press_die", gauge_tag),
        }

    files[RESOURCE_ROOT / f"data/tacz/recipe/create/industry/calibrate_case_die_{caliber_id}.json"] = deploying(
        case_blank_die, calibration_tool, output("tacz:press_die", case_die)
    )
    files[RESOURCE_ROOT / f"data/tacz/recipe/create/industry/calibrate_projectile_die_{caliber_id}.json"] = deploying(
        projectile_blank_die, calibration_tool, output("tacz:press_die", projectile_die)
    )
    case_blank_count = caliber["case_blank_count"]
    projectile_blank_count = caliber["projectile_blank_count"]
    if case_blank_count == 1:
        files[RESOURCE_ROOT / f"data/tacz/recipe/create/industry/form_case_{caliber_id}.json"] = deploying(
            case_stock, partial("tacz:press_die", case_die), output("tacz:cartridge_case", case, stack_limit)
        )
    else:
        incomplete_case_key = f"item.tacz.cartridge_case_blank.incomplete_{caliber_id}"
        case_sequence: list[dict[str, Any]] = []
        # A heavy/long case physically consumes additional neutral brass
        # blanks through separate Deployer stations before final die forming.
        for _ in range(case_blank_count - 1):
            case_sequence.append({
                "type": "create:deploying",
                "target": "$ingredient",
                "ingredient": case_stock,
                "results": ["$result"],
            })
        case_sequence.append({
            "type": "create:deploying",
            "target": "$ingredient",
            "ingredient": partial("tacz:press_die", case_die),
            "results": ["$result"],
            "keep_held_item": True,
        })
        files[RESOURCE_ROOT / f"data/tacz/recipe/create/industry/form_case_{caliber_id}.json"] = {
            "fabric:load_conditions": CREATE_CONDITIONS,
            "type": "create:sequenced_assembly",
            "ingredient": case_stock,
            "transitional_item": output("tacz:cartridge_case_blank", {
                "IndustryPlatform": "ammunition",
                "IndustryPartKind": f"incomplete_case_{caliber_id}",
                "IndustryDisplayName": incomplete_case_key,
                "CartridgeCaliber": caliber_id,
                "CartridgeAmmoId": ammo_id,
            }, stack_limit),
            "result": output("tacz:cartridge_case", case, stack_limit),
            "sequence": case_sequence,
        }

    payloads = caliber.get("projectile_payloads", [])
    if payloads:
        # Explosive warheads must not hide every physical stage inside Create's
        # ephemeral transitional_item. Their body, loaded charge and shaped
        # charge are actual NBT stacks that players can see, store and feed to
        # the next station; this is particularly important for RPG-7 HEAT.
        body_key = f"item.tacz.projectile_blank.body_{caliber_id}"
        body_tag = {
            "IndustryPlatform": "ammunition",
            "IndustryPartKind": f"projectile_body_{caliber_id}",
            "IndustryDisplayName": body_key,
            "CartridgeCaliber": caliber_id,
            "CartridgeAmmoId": ammo_id,
        }
        blank_slots = ["B"] * projectile_blank_count
        body_pattern = ["".join(blank_slots[index:index + 3]).ljust(3) for index in range(0, projectile_blank_count, 3)]
        files[RESOURCE_ROOT / f"data/tacz/recipe/create/industry/form_projectile_body_{caliber_id}.json"] = {
            "fabric:load_conditions": CREATE_CONDITIONS,
            "type": "create:mechanical_crafting",
            "key": {"B": projectile_stock},
            "pattern": body_pattern,
            "result": output("tacz:projectile_blank", body_tag, stack_limit),
        }
        current_tag = body_tag
        payload_index = 0
        for payload in payloads:
            for _ in range(payload["count"]):
                payload_index += 1
                stage_key = f"item.tacz.projectile_blank.payload_{caliber_id}_{payload_index}"
                stage_tag = {
                    "IndustryPlatform": "ammunition",
                    "IndustryPartKind": f"projectile_payload_{caliber_id}_{payload_index}",
                    "IndustryDisplayName": stage_key,
                    "CartridgeCaliber": caliber_id,
                    "CartridgeAmmoId": ammo_id,
                }
                files[RESOURCE_ROOT / f"data/tacz/recipe/create/industry/load_projectile_payload_{caliber_id}_{payload_index}.json"] = deploying(
                    partial("tacz:projectile_blank", current_tag), payload["item"],
                    output("tacz:projectile_blank", stage_tag, stack_limit), keep=False
                )
                current_tag = stage_tag
        files[RESOURCE_ROOT / f"data/tacz/recipe/create/industry/form_projectile_{caliber_id}.json"] = deploying(
            partial("tacz:projectile_blank", current_tag), partial("tacz:press_die", projectile_die),
            output("tacz:projectile_core", projectile, stack_limit)
        )
    else:
        projectile_sequence: list[dict[str, Any]] = []
        # Conventional projectile mass follows the ballistic tier. The initial
        # blank is the sole moving workpiece; extra blanks are inserted one at
        # a time before the reusable die forms the final core.
        for _ in range(projectile_blank_count - 1):
            projectile_sequence.append({
                "type": "create:deploying",
                "target": "$ingredient",
                "ingredient": projectile_stock,
                "results": ["$result"],
            })
        projectile_sequence.append({
            "type": "create:deploying",
            "target": "$ingredient",
            "ingredient": partial("tacz:press_die", projectile_die),
            "results": ["$result"],
            "keep_held_item": True,
        })
        if len(projectile_sequence) == 1:
            files[RESOURCE_ROOT / f"data/tacz/recipe/create/industry/form_projectile_{caliber_id}.json"] = deploying(
                projectile_stock, partial("tacz:press_die", projectile_die), output("tacz:projectile_core", projectile, stack_limit)
            )
        else:
            incomplete_key = f"item.tacz.projectile_blank.incomplete_{caliber_id}"
            files[RESOURCE_ROOT / f"data/tacz/recipe/create/industry/form_projectile_{caliber_id}.json"] = {
                "fabric:load_conditions": CREATE_CONDITIONS,
                "type": "create:sequenced_assembly",
                "ingredient": projectile_stock,
                "transitional_item": output("tacz:projectile_blank", {
                    "IndustryPlatform": "ammunition",
                    "IndustryPartKind": f"incomplete_projectile_{caliber_id}",
                    "IndustryDisplayName": incomplete_key,
                    "CartridgeCaliber": caliber_id,
                    "CartridgeAmmoId": ammo_id,
                }, stack_limit),
                "result": output("tacz:projectile_core", projectile, stack_limit),
                "sequence": projectile_sequence,
            }

    if caliber["eject_case"]:
        # CartridgeAmmoId is emitted on newly fired cases for stack-limit
        # normalization, but it is not a mandatory reconditioning ingredient:
        # pre-update spent cases with the same exact calibre remain recoverable.
        spent_case_match = {key: value for key, value in spent_case.items() if key != "CartridgeAmmoId"}
        files[RESOURCE_ROOT / f"data/tacz/recipe/create/industry/recondition_case_{caliber_id}.json"] = deploying(
            partial("tacz:cartridge_case", spent_case_match), partial("tacz:press_die", case_die),
            output("tacz:cartridge_case", case, stack_limit)
        )

    definition: dict[str, Any] = {
        "case_item": "tacz:cartridge_case",
        "case_caliber": caliber_id,
        "case_display_name": f"item.tacz.cartridge_case.{caliber_id}",
        "projectile_item": "tacz:projectile_core",
        "projectile_caliber": caliber_id,
        "projectile_type": projectile_type,
        "projectile_display_name": f"item.tacz.projectile_core.{caliber_id}_{projectile_type}",
        "primer_item": "tacz:primer",
        "propellant_item": "tacz:industrial_propellant",
        "ammo": caliber["ammo"],
        "count": caliber["batch_count"],
        "case_count": caliber["batch_count"],
        "projectile_count": caliber["batch_count"],
        "primer_count": caliber["batch_count"],
        "propellant_count": caliber["propellant_count"],
        "eject_case": caliber["eject_case"],
    }
    if caliber["eject_case"]:
        definition["spent_case_display_name"] = f"item.tacz.cartridge_case.spent_{caliber_id}"
    files[RESOURCE_ROOT / f"data/tacz/industry/cartridge_assembly/{caliber_id}.json"] = definition
    files[RESOURCE_ROOT / f"data/tacz/industry/ammo/{caliber_id}.json"] = {
        "legacy_recipe": f"tacz:ammo/{caliber_id}"
    }
    return files


def cartridge_language_entries(caliber: dict[str, Any], language: str) -> dict[str, str]:
    suffix = "zh" if language == "zh_cn" else "en"
    caliber_id = caliber["id"]
    projectile_type = caliber["projectile_type"]
    entries = {
        f"item.tacz.cartridge_case.{caliber_id}": caliber[f"case_name_{suffix}"],
        f"item.tacz.projectile_core.{caliber_id}_{projectile_type}": caliber[f"projectile_name_{suffix}"],
        f"item.tacz.press_die.case_{caliber_id}": caliber[f"case_die_name_{suffix}"],
        f"item.tacz.press_die.projectile_{caliber_id}_{projectile_type}": caliber[f"projectile_die_name_{suffix}"],
    }
    if caliber["eject_case"]:
        entries[f"item.tacz.cartridge_case.spent_{caliber_id}"] = caliber[f"spent_case_name_{suffix}"]
    if caliber["case_blank_count"] > 1:
        name = caliber[f"case_name_{suffix}"]
        entries[f"item.tacz.cartridge_case_blank.incomplete_{caliber_id}"] = (
            f"未完成的{name}" if suffix == "zh" else f"Incomplete {name}"
        )
    if caliber.get("projectile_payloads"):
        entries[f"item.tacz.projectile_blank.body_{caliber_id}"] = caliber[f"projectile_body_name_{suffix}"]
        for index, name in enumerate(caliber[f"projectile_payload_names_{suffix}"], start=1):
            entries[f"item.tacz.projectile_blank.payload_{caliber_id}_{index}"] = name
    elif caliber["projectile_blank_count"] > 1:
        name = caliber[f"projectile_name_{suffix}"]
        entries[f"item.tacz.projectile_blank.incomplete_{caliber_id}"] = (
            f"未完成的{name}" if suffix == "zh" else f"Incomplete {name}"
        )
    gauge = caliber.get("calibration_gauge")
    if isinstance(gauge, dict):
        entries[f"item.tacz.press_die.gauge_{caliber_id}"] = gauge[f"name_{suffix}"]
    return entries


def generated_magazine_files(cartridge_ammo_ids: set[str]) -> dict[Path, Any]:
    files: dict[Path, Any] = {}
    feed_root = RESOURCE_ROOT / "data/tacz/industry/gun_feed"
    for path in sorted(feed_root.glob("*.json")):
        feed = read_json(path)
        if feed.get("mechanism") not in {"detachable_magazine", "belt"}:
            continue
        ammo = feed.get("ammo")
        if ammo not in cartridge_ammo_ids:
            raise ValueError(f"{path}: physical magazine ammo '{ammo}' has no cartridge source manifest")
        gun_id = f"tacz:{path.stem}"
        files[RESOURCE_ROOT / f"data/tacz/recipe/create/magazine/{path.stem}.json"] = {
            "fabric:load_conditions": CREATE_CONDITIONS,
            "type": "create:deploying",
            "keep_held_item": True,
            "target": "tacz:magazine_blank",
            "ingredient": partial("tacz:modern_kinetic_gun", {"GunId": gun_id}),
            "results": [output("tacz:magazine", {
                "MagazineFamily": feed["magazine_family"],
                "MagazineAmmoId": ammo,
                "MagazineCapacity": feed["magazine_capacity"],
                "MagazineAmmoCount": 0,
                "MagazineDisplayName": feed["display_name"],
            })],
        }
    return files


def language_entries(platform: dict[str, Any], language: str) -> dict[str, str]:
    name = platform["platform"]
    blueprint = platform["blueprint"]
    chinese = language == "zh_cn"
    entries = {blueprint["display_name"]: blueprint["name_zh" if chinese else "name_en"]}
    for part in platform["parts"]:
        component_key = f"item.tacz.gun_component.{name}_{part['kind']}"
        die_key = f"item.tacz.press_die.component_{name}_{part['kind']}"
        label = part["name_zh" if chinese else "name_en"]
        die_label = part.get("die_name_zh" if chinese else "die_name_en")
        entries[component_key] = label
        entries[die_key] = die_label if die_label else (f"{label}模具" if chinese else f"{label} Die")
    if platform["materials"]:
        label = platform_display_label(platform, language)
        entries[f"item.tacz.gun_component.{name}_furniture_kit"] = (
            f"{label} 外装套件" if chinese else f"{label} Exterior Kit"
        )
    entries[f"item.tacz.gun_component.incomplete_{platform['slug']}"] = platform["incomplete"]["name_zh" if chinese else "name_en"]
    return entries


def load_machine_assets() -> list[dict[str, str]]:
    """Validate the two real industry block visual bindings.

    The palette-generated 16×16 cube stand-ins were useful while the machines
    existed only as a functional vertical slice.  The supplied Blockbench pack
    now provides the authoritative 128×128 texture atlases and element models.
    Keep the registry namespace ``tacz`` as a small parent wrapper while leaving
    the artist's ``tacz_extra`` asset namespace untouched and overrideable.
    """
    manifest = read_json(MACHINE_MANIFEST)
    machines = manifest.get("machines") if isinstance(manifest, dict) else None
    if not isinstance(machines, list) or not machines:
        raise ValueError(f"{MACHINE_MANIFEST}: machines must be a non-empty list")
    result: list[dict[str, str]] = []
    seen: set[str] = set()
    pattern = re.compile(r"^[a-z0-9_.-]+:[a-z0-9_./-]+$")
    for machine in machines:
        if not isinstance(machine, dict):
            raise ValueError(f"{MACHINE_MANIFEST}: each machine must be an object")
        machine_id = machine.get("id")
        source_model = machine.get("source_model")
        source_texture = machine.get("source_texture")
        if not isinstance(machine_id, str) or not machine_id or machine_id in seen:
            raise ValueError(f"{MACHINE_MANIFEST}: each machine needs a unique id")
        if not isinstance(source_model, str) or not pattern.fullmatch(source_model):
            raise ValueError(f"{MACHINE_MANIFEST}: {machine_id}.source_model must be a resource id")
        if not isinstance(source_texture, str) or not pattern.fullmatch(source_texture):
            raise ValueError(f"{MACHINE_MANIFEST}: {machine_id}.source_texture must be a resource id")
        seen.add(machine_id)
        result.append({"id": machine_id, "source_model": source_model, "source_texture": source_texture})
    return result


def supplied_machine_item_visuals(machine_assets: list[dict[str, str]]) -> dict[str, str]:
    """Registry item id -> supplied Blockbench parent model for the two real machines."""
    return {f"tacz:{machine['id']}": machine["source_model"] for machine in machine_assets}


def facing_blockstate(model: str) -> dict[str, Any]:
    """One blockstate definition matching the horizontal FACING state in both machines."""
    return {
        "variants": {
            "facing=north": {"model": model, "y": 0},
            "facing=east": {"model": model, "y": 90},
            "facing=south": {"model": model, "y": 180},
            "facing=west": {"model": model, "y": 270},
        }
    }


def generated_machine_files(machine_assets: list[dict[str, str]]) -> dict[Path, bytes | Any]:
    """Emit lightweight ``tacz`` wrappers around the supplied tacz_extra models."""
    result: dict[Path, bytes | Any] = {}
    for machine in machine_assets:
        machine_id = machine["id"]
        result[RESOURCE_ROOT / f"assets/tacz/models/block/{machine_id}.json"] = {
            "parent": machine["source_model"]
        }
        result[RESOURCE_ROOT / f"assets/tacz/blockstates/{machine_id}.json"] = facing_blockstate(
            f"tacz:block/{machine_id}"
        )
    return result


def obsolete_machine_placeholder_files(machine_assets: list[dict[str, str]]) -> set[Path]:
    """Old generated 16×16 cube textures must not linger as misleading fallbacks."""
    return {
        RESOURCE_ROOT / f"assets/tacz/textures/block/{machine['id']}.png"
        for machine in machine_assets
    }


def forbidden_complete_authoring_runtime_files() -> set[Path]:
    """Raw complete-pack authoring maps must never be exposed to the runtime loader."""
    root = RESOURCE_ROOT / "assets/tacz_extra/industry_icons"
    return set(root.rglob("*.json")) if root.exists() else set()


# ---------------------------------------------------------------------------
# Client icon mapping and exact artwork coverage catalog
# ---------------------------------------------------------------------------

ICON_SELECTOR_KEYS = (
    "ammo_id",
    "magazine_family",
    "magazine_ammo_id",
    "magazine_capacity",
    "cartridge_caliber",
    "projectile_type",
    "industry_part_kind",
    "industry_platform",
    "die_target_kind",
)
ICON_COVERAGE_VALUES = {"exact", "family", "placeholder"}
ICON_IDENTIFIER = re.compile(r"^[a-z0-9_.-]+:[a-z0-9_./-]+$")


def load_icon_mapping() -> dict[str, Any]:
    """Load the authoring source for the client-only NBT icon resolver."""
    mapping = read_json(ICON_MAPPING_MANIFEST)
    if not isinstance(mapping, dict) or mapping.get("schema_version") != 1:
        raise ValueError(f"{ICON_MAPPING_MANIFEST}: schema_version must be 1")
    entries = mapping.get("entries")
    if not isinstance(entries, list) or not entries:
        raise ValueError(f"{ICON_MAPPING_MANIFEST}: entries must be a non-empty list")

    seen_ids: set[str] = set()
    for index, entry in enumerate(entries):
        if not isinstance(entry, dict):
            raise ValueError(f"{ICON_MAPPING_MANIFEST}: entries[{index}] must be an object")
        entry_id = entry.get("id")
        item = entry.get("item")
        texture = entry.get("texture")
        if not isinstance(entry_id, str) or not entry_id or entry_id in seen_ids:
            raise ValueError(f"{ICON_MAPPING_MANIFEST}: entries[{index}] needs a unique non-empty id")
        seen_ids.add(entry_id)
        if not isinstance(item, str) or not ICON_IDENTIFIER.fullmatch(item):
            raise ValueError(f"{ICON_MAPPING_MANIFEST}: {entry_id} has invalid item id")
        if not isinstance(texture, str) or not ICON_IDENTIFIER.fullmatch(texture):
            raise ValueError(f"{ICON_MAPPING_MANIFEST}: {entry_id} has invalid texture id")
        priority = entry.get("priority", 0)
        if not isinstance(priority, int):
            raise ValueError(f"{ICON_MAPPING_MANIFEST}: {entry_id}.priority must be an integer")
        coverage = entry.get("coverage", "exact")
        if coverage not in ICON_COVERAGE_VALUES:
            raise ValueError(
                f"{ICON_MAPPING_MANIFEST}: {entry_id}.coverage must be one of {sorted(ICON_COVERAGE_VALUES)}"
            )
        match = entry.get("match", {})
        if not isinstance(match, dict):
            raise ValueError(f"{ICON_MAPPING_MANIFEST}: {entry_id}.match must be an object")
        unknown = set(match) - set(ICON_SELECTOR_KEYS)
        if unknown:
            raise ValueError(f"{ICON_MAPPING_MANIFEST}: {entry_id}.match has unknown selector(s) {sorted(unknown)}")
        for key, value in match.items():
            if key == "magazine_capacity":
                if not isinstance(value, int) or value < 1:
                    raise ValueError(f"{ICON_MAPPING_MANIFEST}: {entry_id}.match.magazine_capacity must be a positive integer")
            elif not isinstance(value, str) or not value:
                raise ValueError(f"{ICON_MAPPING_MANIFEST}: {entry_id}.match.{key} must be a non-empty string")
    return mapping


def archive_tacz_extra_files(archive_path: Path, label: str) -> dict[Path, bytes]:
    """Read safe ``assets/tacz_extra`` files from one user-supplied archive."""
    if not archive_path.exists():
        raise ValueError(f"Missing {label} archive {archive_path}")
    files: dict[Path, bytes] = {}
    with zipfile.ZipFile(archive_path) as archive:
        for name in archive.namelist():
            if not name.startswith("assets/tacz_extra/") or name.endswith("/"):
                continue
            relative = PurePosixPath(name)
            if ".." in relative.parts:
                raise ValueError(f"Unsafe path in {archive_path}: {name}")
            files[RESOURCE_ROOT / Path(*relative.parts)] = archive.read(name)
    if not files:
        raise ValueError(f"{archive_path}: no assets/tacz_extra files found")
    return files


def embedded_icon_pack_files() -> dict[Path, bytes]:
    """Mirror the repaired first icon batch as a base layer."""
    return archive_tacz_extra_files(FIXED_ICON_PACK, "repaired icon")


def complete_extra_pack_files() -> dict[Path, bytes]:
    """Read current complete art, excluding its non-runtime authoring JSON schema.

    ``industry_icon_exact.json`` and ``industry_icon_rules.json`` are valuable
    source manifests but are not valid input to IndustryIconManager directly.
    They are adapted into a generated ``assets/tacz/industry_icons/complete.json``
    below.  Copying them verbatim under ``assets/tacz_extra/industry_icons`` would
    make the runtime loader misinterpret them as mapping files.
    """
    files = archive_tacz_extra_files(COMPLETE_EXTRA_PACK, "complete extra")
    return {
        path: value for path, value in files.items()
        if "/industry_icons/" not in path.as_posix()
    }


def embedded_industry_block_pack_files() -> dict[Path, bytes]:
    """Mirror supplied Blockbench machine models/textures into built-in assets."""
    return archive_tacz_extra_files(INDUSTRY_BLOCK_PACK, "industry block")


def overlay_assets(target: dict[Path, bytes], files: dict[Path, bytes], label: str,
                   require_equal: bool = False) -> None:
    """Merge one explicitly ordered visual source into the embedded namespace.

    Complete-pack art intentionally replaces same-named pixel/model files. Language
    files are the exception: the later pack adds raw-material names but omits the
    original icon-library keys, so JSON objects are unioned with the later source
    winning only colliding keys.
    """
    for path, value in files.items():
        old = target.get(path)
        if require_equal and old is not None and old != value:
            raise ValueError(f"Conflicting tacz_extra asset {path} while reading {label}")
        if old is not None and not require_equal and path.suffix == ".json" and "/lang/" in path.as_posix():
            try:
                merged_lang = json.loads(old.decode("utf-8"))
                merged_lang.update(json.loads(value.decode("utf-8")))
            except (UnicodeDecodeError, json.JSONDecodeError, AttributeError) as exception:
                raise ValueError(f"Invalid tacz_extra language merge source {path} while reading {label}") from exception
            target[path] = (json.dumps(merged_lang, ensure_ascii=False, indent=2) + "\n").encode("utf-8")
        else:
            target[path] = value


def merged_tacz_extra_files() -> dict[Path, bytes]:
    """Combine baseline repair art, newer complete art, then the explicit block update.

    The complete archive intentionally supersedes the earlier repaired 61-icon
    batch for overlapping item paths. The separately uploaded block archive is
    required to byte-match the complete pack's block assets; a mismatch signals
    an ambiguous user source instead of silently choosing arbitrary geometry.
    """
    merged: dict[Path, bytes] = {}
    overlay_assets(merged, embedded_icon_pack_files(), "repaired icon")
    overlay_assets(merged, complete_extra_pack_files(), "complete extra")
    overlay_assets(merged, embedded_industry_block_pack_files(), "industry block", require_equal=True)
    return merged


def read_complete_pack_json(path: str) -> Any:
    if not COMPLETE_EXTRA_PACK.exists():
        raise ValueError(f"Missing complete extra archive {COMPLETE_EXTRA_PACK}")
    with zipfile.ZipFile(COMPLETE_EXTRA_PACK) as archive:
        try:
            return json.loads(archive.read(path).decode("utf-8"))
        except KeyError as exception:
            raise ValueError(f"{COMPLETE_EXTRA_PACK}: missing {path}") from exception
        except (UnicodeDecodeError, json.JSONDecodeError) as exception:
            raise ValueError(f"{COMPLETE_EXTRA_PACK}: invalid JSON in {path}") from exception


def load_complete_exact_icons() -> dict[str, str]:
    """Load the user-facing identity -> texture-name map from the complete pack."""
    data = read_complete_pack_json("assets/tacz_extra/industry_icons/industry_icon_exact.json")
    if not isinstance(data, dict) or not data:
        raise ValueError(f"{COMPLETE_EXTRA_PACK}: industry_icon_exact.json must be a non-empty object")
    result: dict[str, str] = {}
    for identity, texture_name in data.items():
        if not isinstance(identity, str) or not identity or not isinstance(texture_name, str) or not texture_name:
            raise ValueError(f"{COMPLETE_EXTRA_PACK}: exact icon entries must be non-empty strings")
        if not re.fullmatch(r"[a-z0-9_./-]+", texture_name):
            raise ValueError(f"{COMPLETE_EXTRA_PACK}: unsafe texture name for {identity}: {texture_name}")
        result[identity] = texture_name
    return result


def resource_asset_path(identifier: str, directory: str, suffix: str) -> Path:
    namespace, path = identifier.split(":", 1)
    return RESOURCE_ROOT / "assets" / namespace / directory / f"{path}{suffix}"


def png_dimensions(data: bytes, source: Path) -> tuple[int, int]:
    if len(data) < 24 or data[:8] != b"\x89PNG\r\n\x1a\n" or data[12:16] != b"IHDR":
        raise ValueError(f"{source}: expected a PNG with IHDR")
    return int.from_bytes(data[16:20], "big"), int.from_bytes(data[20:24], "big")



def decode_rgba_png(data: bytes, source: Path) -> tuple[int, int, list[bytearray]]:
    """Decode the constrained 8-bit RGBA/non-interlaced PNGs supplied by the complete pack."""
    if len(data) < 33 or data[:8] != b"\x89PNG\r\n\x1a\n":
        raise ValueError(f"{source}: invalid PNG signature")
    position = 8
    width = height = bit_depth = color_type = interlace = None
    compressed: list[bytes] = []
    while position + 12 <= len(data):
        length = struct.unpack_from(">I", data, position)[0]
        kind = data[position + 4:position + 8]
        payload_start = position + 8
        payload_end = payload_start + length
        if payload_end + 4 > len(data):
            raise ValueError(f"{source}: truncated PNG chunk")
        payload = data[payload_start:payload_end]
        position = payload_end + 4
        if kind == b"IHDR":
            width, height, bit_depth, color_type, _, _, interlace = struct.unpack(">IIBBBBB", payload)
        elif kind == b"IDAT":
            compressed.append(payload)
        elif kind == b"IEND":
            break
    if width is None or height is None or bit_depth != 8 or color_type != 6 or interlace != 0:
        raise ValueError(f"{source}: expected non-interlaced 8-bit RGBA PNG")
    try:
        raw = zlib.decompress(b"".join(compressed))
    except zlib.error as exception:
        raise ValueError(f"{source}: invalid PNG IDAT stream") from exception
    stride = width * 4
    if len(raw) != height * (stride + 1):
        raise ValueError(f"{source}: unexpected RGBA row length")

    def paeth(left: int, above: int, upper_left: int) -> int:
        prediction = left + above - upper_left
        distance_left = abs(prediction - left)
        distance_above = abs(prediction - above)
        distance_upper_left = abs(prediction - upper_left)
        return left if distance_left <= distance_above and distance_left <= distance_upper_left \
            else above if distance_above <= distance_upper_left else upper_left

    rows: list[bytearray] = []
    previous = bytearray(stride)
    offset = 0
    for _ in range(height):
        filter_type = raw[offset]
        offset += 1
        current = bytearray(raw[offset:offset + stride])
        offset += stride
        for index in range(stride):
            left = current[index - 4] if index >= 4 else 0
            above = previous[index]
            upper_left = previous[index - 4] if index >= 4 else 0
            if filter_type == 1:
                current[index] = (current[index] + left) & 0xFF
            elif filter_type == 2:
                current[index] = (current[index] + above) & 0xFF
            elif filter_type == 3:
                current[index] = (current[index] + ((left + above) // 2)) & 0xFF
            elif filter_type == 4:
                current[index] = (current[index] + paeth(left, above, upper_left)) & 0xFF
            elif filter_type != 0:
                raise ValueError(f"{source}: unsupported PNG filter {filter_type}")
        rows.append(current)
        previous = current
    return width, height, rows


def encode_rgba_png(width: int, height: int, rows: list[bytearray]) -> bytes:
    """Write deterministic RGBA PNG bytes; geometry overrides never resample pixels."""
    raw = b"".join(b"\x00" + bytes(row) for row in rows)

    def chunk(kind: bytes, payload: bytes) -> bytes:
        return struct.pack(">I", len(payload)) + kind + payload + struct.pack(">I", zlib.crc32(kind + payload) & 0xFFFFFFFF)

    return b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", width, height, 8, 6, 0, 0, 0)) \
        + chunk(b"IDAT", zlib.compress(raw, 9)) + chunk(b"IEND", b"")


def alpha_geometry(width: int, height: int, rows: list[bytearray], name: str) -> dict[str, Any]:
    """Return non-destructive alpha-mask diagnostics for one icon."""
    pixels: list[tuple[int, int, int]] = []
    for y, row in enumerate(rows):
        for x in range(width):
            alpha = row[x * 4 + 3]
            if alpha:
                pixels.append((x, y, alpha))
    if not pixels:
        raise ValueError(f"{name}: fully transparent icon")
    min_x = min(x for x, _, _ in pixels)
    max_x = max(x for x, _, _ in pixels)
    min_y = min(y for _, y, _ in pixels)
    max_y = max(y for _, y, _ in pixels)
    total_alpha = sum(alpha for _, _, alpha in pixels)
    center_x = sum(x * alpha for x, _, alpha in pixels) / total_alpha
    center_y = sum(y * alpha for _, y, alpha in pixels) / total_alpha
    mask = {(x, y) for x, y, _ in pixels}
    components: list[int] = []
    while mask:
        seed = mask.pop()
        frontier = [seed]
        size = 1
        while frontier:
            x, y = frontier.pop()
            for neighbor in ((x - 1, y), (x + 1, y), (x, y - 1), (x, y + 1)):
                if neighbor in mask:
                    mask.remove(neighbor)
                    frontier.append(neighbor)
                    size += 1
        components.append(size)
    components.sort(reverse=True)
    opaque = sum(1 for _, _, alpha in pixels if alpha == 255)
    enclosed_holes = 0
    alpha_mask = {(x, y) for x, y, _ in pixels}
    for y in range(1, height - 1):
        for x in range(1, width - 1):
            if (x, y) not in alpha_mask and all(neighbor in alpha_mask for neighbor in (
                    (x - 1, y), (x + 1, y), (x, y - 1), (x, y + 1))):
                enclosed_holes += 1
    return {
        "name": name,
        "bbox": [min_x, min_y, max_x, max_y],
        "bbox_size": [max_x - min_x + 1, max_y - min_y + 1],
        "bbox_center_offset": [round((min_x + max_x) / 2 - (width - 1) / 2, 3), round((min_y + max_y) / 2 - (height - 1) / 2, 3)],
        "alpha_centroid": [round(center_x, 3), round(center_y, 3)],
        "centroid_offset": [round(center_x - (width - 1) / 2, 3), round(center_y - (height - 1) / 2, 3)],
        "opaque_pixel_count": opaque,
        "partial_alpha_pixel_count": len(pixels) - opaque,
        "component_count": len(components),
        "component_sizes": components[:8],
        "border_pixel_count": sum(1 for x, y, _ in pixels if x in (0, width - 1) or y in (0, height - 1)),
        "enclosed_hole_pixel_count": enclosed_holes,
    }


def load_icon_geometry_overrides() -> dict[str, Any]:
    manifest = read_json(ICON_GEOMETRY_MANIFEST)
    if not isinstance(manifest, dict) or manifest.get("schema_version") != 1:
        raise ValueError(f"{ICON_GEOMETRY_MANIFEST}: schema_version must be 1")
    canvas = manifest.get("canvas")
    overrides = manifest.get("overrides")
    if canvas != [32, 32] or not isinstance(overrides, dict):
        raise ValueError(f"{ICON_GEOMETRY_MANIFEST}: expected 32x32 canvas and overrides object")
    for name, override in overrides.items():
        if not isinstance(name, str) or not re.fullmatch(r"[a-z0-9_./-]+", name) or not isinstance(override, dict):
            raise ValueError(f"{ICON_GEOMETRY_MANIFEST}: invalid override {name!r}")
        shift = override.get("translate")
        removals = override.get("remove_pixels", [])
        if shift is not None and (not isinstance(shift, list) or len(shift) != 2
                                  or not all(isinstance(value, int) for value in shift)):
            raise ValueError(f"{ICON_GEOMETRY_MANIFEST}: invalid translate for {name!r}")
        if not isinstance(removals, list) or any(not isinstance(pixel, list) or len(pixel) != 2
                                                 or not all(isinstance(value, int) for value in pixel)
                                                 for pixel in removals):
            raise ValueError(f"{ICON_GEOMETRY_MANIFEST}: invalid remove_pixels for {name!r}")
        if shift is None and not removals:
            raise ValueError(f"{ICON_GEOMETRY_MANIFEST}: {name!r} has no geometry action")
    return manifest


def translate_icon_rows(width: int, height: int, rows: list[bytearray], dx: int, dy: int, source: str) -> list[bytearray]:
    output = [bytearray(width * 4) for _ in range(height)]
    for y, row in enumerate(rows):
        for x in range(width):
            index = x * 4
            pixel = row[index:index + 4]
            if pixel[3] == 0:
                continue
            target_x = x + dx
            target_y = y + dy
            if not 0 <= target_x < width or not 0 <= target_y < height:
                raise ValueError(f"{source}: geometry translation {dx},{dy} would clip opaque pixels")
            target = target_x * 4
            output[target_y][target:target + 4] = pixel
    return output


def apply_complete_icon_geometry_overrides(embedded: dict[Path, bytes]) -> dict[str, Any]:
    """Audit every complete item icon and apply only manifest-approved integer translations."""
    manifest = load_icon_geometry_overrides()
    threshold = manifest.get("centroid_threshold", 3.5)
    if not isinstance(threshold, (int, float)) or threshold <= 0:
        raise ValueError(f"{ICON_GEOMETRY_MANIFEST}: centroid_threshold must be positive")
    texture_root = RESOURCE_ROOT / "assets/tacz_extra/textures/item"
    texture_paths = sorted(path for path in embedded if texture_root in path.parents and path.suffix == ".png")
    pre: dict[str, dict[str, Any]] = {}
    post: dict[str, dict[str, Any]] = {}
    for path in texture_paths:
        name = path.stem
        width, height, rows = decode_rgba_png(embedded[path], path)
        if (width, height) != (32, 32):
            raise ValueError(f"{path}: complete item icon must be 32x32")
        pre[name] = alpha_geometry(width, height, rows, name)
        override = manifest["overrides"].get(name)
        if override is not None:
            if "translate" in override:
                dx, dy = override["translate"]
                rows = translate_icon_rows(width, height, rows, dx, dy, name)
            for x, y in override.get("remove_pixels", []):
                if not 0 <= x < width or not 0 <= y < height:
                    raise ValueError(f"{name}: remove_pixels coordinate {x},{y} is outside the canvas")
                index = x * 4
                if rows[y][index + 3] == 0:
                    raise ValueError(f"{name}: remove_pixels coordinate {x},{y} is already transparent")
                rows[y][index:index + 4] = b"\x00\x00\x00\x00"
            embedded[path] = encode_rgba_png(width, height, rows)
        post[name] = alpha_geometry(width, height, rows, name)

    missing_override_assets = sorted(set(manifest["overrides"]) - set(pre))
    if missing_override_assets:
        raise ValueError(f"{ICON_GEOMETRY_MANIFEST}: override targets missing textures {missing_override_assets}")
    pre_outliers = [entry for entry in pre.values()
                    if max(abs(value) for value in entry["centroid_offset"]) > threshold]
    post_outliers = [entry for entry in post.values()
                     if max(abs(value) for value in entry["centroid_offset"]) > threshold]
    pre_bbox_outliers = [entry for entry in pre.values()
                         if max(abs(value) for value in entry["bbox_center_offset"]) > threshold]
    post_bbox_outliers = [entry for entry in post.values()
                          if max(abs(value) for value in entry["bbox_center_offset"]) > threshold]
    return {
        "schema_version": 1,
        "source": {
            "archive": str(COMPLETE_EXTRA_PACK.relative_to(REPO)),
            "geometry_manifest": str(ICON_GEOMETRY_MANIFEST.relative_to(REPO)),
        },
        "canvas": [32, 32],
        "icon_count": len(texture_paths),
        "centroid_threshold": threshold,
        "pre_override_outliers": sorted(pre_outliers, key=lambda item: item["name"]),
        "post_override_outliers": sorted(post_outliers, key=lambda item: item["name"]),
        "pre_bbox_outliers": sorted(pre_bbox_outliers, key=lambda item: item["name"]),
        "post_bbox_outliers": sorted(post_bbox_outliers, key=lambda item: item["name"]),
        "applied_overrides": [
            {
                "name": name,
                "translate": manifest["overrides"][name].get("translate", [0, 0]),
                "remove_pixels": manifest["overrides"][name].get("remove_pixels", []),
                "reason": manifest["overrides"][name].get("reason", ""),
                "before": pre[name],
                "after": post[name],
            }
            for name in sorted(manifest["overrides"])
        ],
        "component_histogram": dict(sorted(Counter(item["component_count"] for item in post.values()).items())),
        "partial_alpha_icon_count": sum(1 for item in post.values() if item["partial_alpha_pixel_count"] > 0),
        "enclosed_hole_pixel_count": sum(item["enclosed_hole_pixel_count"] for item in post.values()),
        "border_touch_icon_count": sum(1 for item in post.values() if item["border_pixel_count"] > 0),
        "icons": [post[name] for name in sorted(post)],
    }


def render_complete_icon_geometry_document(report: dict[str, Any]) -> str:
    lines = [
        "# TACZ Extra Complete 图标几何与 alpha 审计",
        "",
        "审计对象是完整包的所有 32×32 RGBA 物品 PNG。它检查 canvas、alpha、连通组件、边界接触、",
        "alpha 重心，并只对清单明确批准的图做整数像素平移；不会缩放、旋转、模糊或 AI 重绘。",
        "",
        "## 结果",
        "",
        f"- 图标数：{report['icon_count']}；",
        f"- 部分 alpha 图标：{report['partial_alpha_icon_count']}（当前完整包应为 0，使用二值 cutout）；",
        f"- 单像素四邻封闭透明孔：{report['enclosed_hole_pixel_count']}（包括重复的扳机开孔等有意几何，不自动填补）；",
        f"- 连通组件分布：{report['component_histogram']}；",
        f"- 接触画布边缘的图标：{report['border_touch_icon_count']}（大模具/大板件可合理接触边缘，不自动裁切）；",
        f"- 平移前重心越界（>{report['centroid_threshold']} px）：{len(report['pre_override_outliers'])}；",
        f"- 平移后重心越界：{len(report['post_override_outliers'])}；",
        f"- 包围盒中心越界：调整前 {len(report['pre_bbox_outliers'])}，调整后 {len(report['post_bbox_outliers'])}。",
        "",
        "## 已执行的安全调整",
        "",
        "| 图标 | 平移 | 删除离散像素 | 调整前重心偏移 | 调整后重心偏移 |",
        "| --- | ---: | --- | --- | --- |",
    ]
    for override in report["applied_overrides"]:
        lines.append(
            f"| `{override['name']}` | `{override['translate']}` | `{override['remove_pixels']}` | "
            f"{override['before']['centroid_offset']} | {override['after']['centroid_offset']} |"
        )
    lines.extend([
        "",
        "## 形态/掉落物约束",
        "",
        "- NBT 工业件走 `TaczDynamicItemModel` 的 1×1 extents（X/Z ±0.5，Y 0..1），不会因完整包 PNG 变成 oversized GUI 或额外浮高的掉落物；",
        "- 静态原料/袋/装弹器模型均为 `item/generated`，使用原版 1×1 物品 bounds；完整包没有额外 `display` 变换，避免了单独图标歪放/缩放不一致；",
        "- 两台机器是方块模型，属于真实方块物品而非此处 32×32 平面图标，单独由其 Blockbench display/model 处理。",
        "",
        "完整逐图数据在 `TACZ_extra_COMPLETE_geometry_report.json`。",
        "",
    ])
    return "\n".join(lines)


def industrial_console_texture(accent: tuple[int, int, int]) -> bytes:
    """Generate a crisp 256×256 technical-console background without external art tooling."""
    width = height = 256
    rows = [bytearray(width * 4) for _ in range(height)]

    def fill(x0: int, y0: int, x1: int, y1: int, color: tuple[int, int, int, int]) -> None:
        for y in range(max(0, y0), min(height, y1)):
            row = rows[y]
            for x in range(max(0, x0), min(width, x1)):
                index = x * 4
                row[index:index + 4] = bytes(color)

    outer = (14, 18, 21, 255)
    panel = (31, 40, 47, 255)
    recess = (17, 24, 29, 255)
    line = (81, 98, 108, 255)
    accent_rgba = (*accent, 255)
    accent_dim = tuple(max(0, int(channel * 0.45)) for channel in accent) + (255,)
    fill(0, 0, width, height, outer)
    fill(2, 2, width - 2, height - 2, panel)
    fill(5, 5, width - 5, 28, recess)
    fill(5, 28, width - 5, 30, accent_rgba)
    fill(8, 34, width - 8, 121, recess)
    fill(8, 124, width - 8, height - 8, (24, 31, 36, 255))
    # Console rivets / grid; intentionally subdued so slots and icons dominate.
    for x in range(12, width - 12, 16):
        for y in range(38, 117, 16):
            fill(x, y, x + 2, y + 2, line)
    for x in range(10, width - 10, 20):
        fill(x, 119, x + 10, 120, accent_dim)
    # Inventory divider and lower ledger lines.
    fill(8, 128, width - 8, 130, line)
    for y in (166, 190, 214):
        fill(12, y, width - 12, y + 1, (49, 61, 68, 255))
    return encode_rgba_png(width, height, rows)


def generated_industrial_gui_files() -> dict[Path, bytes]:
    return {
        RESOURCE_ROOT / "assets/tacz/textures/gui/cartridge_assembly_console.png": industrial_console_texture((223, 154, 50)),
        RESOURCE_ROOT / "assets/tacz/textures/gui/industrial_salvage_console.png": industrial_console_texture((211, 109, 50)),
    }

def validate_industry_block_assets(machine_assets: list[dict[str, str]], embedded: dict[Path, bytes]) -> list[dict[str, Any]]:
    """Validate the supplied Blockbench files before binding game models to them."""
    report: list[dict[str, Any]] = []
    for machine in machine_assets:
        machine_id = machine["id"]
        source_model = machine["source_model"]
        source_texture = machine["source_texture"]
        model_path = resource_asset_path(source_model, "models", ".json")
        texture_path = resource_asset_path(source_texture, "textures", ".png")
        item_model_path = resource_asset_path(source_model.replace(":block/", ":item/"), "models", ".json")
        blockstate_path = resource_asset_path(source_model.replace(":block/", ":"), "blockstates", ".json")
        for path in (model_path, texture_path, item_model_path, blockstate_path):
            if path not in embedded:
                raise ValueError(f"{INDUSTRY_BLOCK_PACK}: missing required machine asset {path.relative_to(RESOURCE_ROOT)}")
        model = json.loads(embedded[model_path].decode("utf-8"))
        item_model = json.loads(embedded[item_model_path].decode("utf-8"))
        blockstate = json.loads(embedded[blockstate_path].decode("utf-8"))
        if not isinstance(model.get("elements"), list) or not model["elements"]:
            raise ValueError(f"{model_path}: a machine model needs non-empty elements")
        textures = model.get("textures")
        if not isinstance(textures, dict) or textures.get("tex") != source_texture:
            raise ValueError(f"{model_path}: textures.tex must bind {source_texture}")
        face_count = 0
        for element_index, element in enumerate(model["elements"]):
            if not isinstance(element, dict) or not isinstance(element.get("faces"), dict):
                raise ValueError(f"{model_path}: element {element_index} needs a faces object")
            for face in element["faces"].values():
                if not isinstance(face, dict):
                    raise ValueError(f"{model_path}: element {element_index} has invalid face")
                texture_ref = face.get("texture")
                if not isinstance(texture_ref, str) or not texture_ref:
                    raise ValueError(f"{model_path}: element {element_index} face has no texture reference")
                if texture_ref.startswith("#") and texture_ref[1:] not in textures:
                    raise ValueError(f"{model_path}: element {element_index} references undefined texture slot {texture_ref}")
                face_count += 1
        if item_model.get("parent") != source_model:
            raise ValueError(f"{item_model_path}: item parent must be {source_model}")
        variants = blockstate.get("variants")
        expected_variants = {f"facing={direction}" for direction in ("north", "east", "south", "west")}
        if not isinstance(variants, dict) or set(variants) != expected_variants:
            raise ValueError(f"{blockstate_path}: expected exactly four horizontal facing variants")
        if any(not isinstance(value, dict) or value.get("model") != source_model for value in variants.values()):
            raise ValueError(f"{blockstate_path}: every variant must use {source_model}")
        width, height = png_dimensions(embedded[texture_path], texture_path)
        report.append({
            "id": machine_id,
            "source_model": source_model,
            "source_texture": source_texture,
            "model_file": str(model_path.relative_to(RESOURCE_ROOT)),
            "texture_file": str(texture_path.relative_to(RESOURCE_ROOT)),
            "format_version": model.get("format_version"),
            "ambientocclusion": model.get("ambientocclusion", True),
            "texture_size": [width, height],
            "element_count": len(model["elements"]),
            "face_count": face_count,
            "source_blockstate_file": str(blockstate_path.relative_to(RESOURCE_ROOT)),
            "source_item_model_file": str(item_model_path.relative_to(RESOURCE_ROOT)),
        })
    return report


def texture_file_candidates(texture: str) -> list[Path]:
    namespace, path = texture.split(":", 1)
    return [
        RESOURCE_ROOT / "assets" / namespace / "textures" / f"{path}.png",
        # The bundled default gun pack is an independent, license-preserved
        # resource archive. Referencing one of its existing slot textures is
        # allowed; this generator never writes inside it.
        RESOURCE_ROOT / "assets/tacz/custom/tacz_default_gun/assets" / namespace / "textures" / f"{path}.png",
    ]


def validate_icon_texture_references(mapping: dict[str, Any], embedded: dict[Path, bytes]) -> None:
    for entry in mapping["entries"]:
        texture = entry["texture"]
        candidates = texture_file_candidates(texture)
        if not any(candidate.exists() or candidate in embedded for candidate in candidates):
            raise ValueError(
                f"{ICON_MAPPING_MANIFEST}: icon entry {entry['id']} references missing texture {texture}"
            )



def complete_texture_identifier(texture_name: str) -> str:
    return f"tacz_extra:item/{texture_name}"


def complete_texture_asset_paths(texture_name: str) -> tuple[Path, Path]:
    identifier = complete_texture_identifier(texture_name)
    return (
        resource_asset_path(identifier, "textures", ".png"),
        resource_asset_path(identifier, "models", ".json"),
    )


def validate_complete_pack_art(exact: dict[str, str], embedded: dict[Path, bytes]) -> dict[str, Any]:
    """Validate all complete-pack item models/textures before generating mappings."""
    complete_all = archive_tacz_extra_files(COMPLETE_EXTRA_PACK, "complete extra")
    texture_root = RESOURCE_ROOT / "assets/tacz_extra/textures/item"
    model_root = RESOURCE_ROOT / "assets/tacz_extra/models/item"
    item_textures = sorted(path for path in complete_all if texture_root in path.parents and path.suffix == ".png")
    item_models = sorted(path for path in complete_all if model_root in path.parents and path.suffix == ".json")
    dimensions: Counter[tuple[int, int]] = Counter()
    for texture_path in item_textures:
        width, height = png_dimensions(complete_all[texture_path], texture_path)
        if complete_all[texture_path][24] != 8 or complete_all[texture_path][25] != 6:
            raise ValueError(f"{texture_path}: complete item icon must be 8-bit RGBA PNG")
        dimensions[(width, height)] += 1

    generated_models = 0
    block_parent_models = 0
    custom_display_model_count = 0
    for model_path in item_models:
        model = json.loads(complete_all[model_path].decode("utf-8"))
        if "display" in model:
            custom_display_model_count += 1
        textures = model.get("textures")
        if isinstance(textures, dict) and isinstance(textures.get("layer0"), str):
            generated_models += 1
            layer0 = textures["layer0"]
            expected_texture = resource_asset_path(layer0, "textures", ".png")
            if expected_texture not in complete_all:
                raise ValueError(f"{model_path}: layer0 references missing {layer0}")
        elif isinstance(model.get("parent"), str) and model["parent"].startswith("tacz_extra:block/"):
            block_parent_models += 1
        else:
            raise ValueError(f"{model_path}: unsupported complete-pack item model form")

    for identity, texture_name in exact.items():
        texture_path, model_path = complete_texture_asset_paths(texture_name)
        if texture_path not in embedded or model_path not in embedded:
            raise ValueError(f"{COMPLETE_EXTRA_PACK}: {identity} -> {texture_name} has no embedded texture/model")
        model = json.loads(embedded[model_path].decode("utf-8"))
        if model.get("textures", {}).get("layer0") != complete_texture_identifier(texture_name):
            raise ValueError(f"{model_path}: layer0 does not match exact map texture {texture_name}")

    rules = read_complete_pack_json("assets/tacz_extra/industry_icons/industry_icon_rules.json")
    example = read_complete_pack_json("assets/tacz_extra/industry_icons/example_platform_family.json")
    raw_materials = load_complete_raw_material_models(embedded)
    if not isinstance(rules, dict) or rules.get("version") != 1 or not isinstance(rules.get("platform_rules"), list):
        raise ValueError(f"{COMPLETE_EXTRA_PACK}: industry_icon_rules.json has unsupported authoring schema")
    if not isinstance(example, dict):
        raise ValueError(f"{COMPLETE_EXTRA_PACK}: example_platform_family.json must be an object")

    return {
        "complete_item_texture_count": len(item_textures),
        "complete_item_model_count": len(item_models),
        "generated_item_model_count": generated_models,
        "block_parent_item_model_count": block_parent_models,
        "custom_display_item_model_count": custom_display_model_count,
        "texture_dimensions": {f"{width}x{height}": count for (width, height), count in sorted(dimensions.items())},
        "raw_exact_entry_count": len(exact),
        "raw_material_entry_count": len(raw_materials),
        "raw_rule_platform_count": len(rules["platform_rules"]),
        "raw_example_platform_count": len(example),
    }



def load_complete_raw_material_models(embedded: dict[Path, bytes]) -> dict[str, str]:
    """Adapt the complete pack's static raw-material map to vanilla parent models."""
    raw_map = read_complete_pack_json("assets/tacz_extra/industry_icons/raw_material_map.json")
    if not isinstance(raw_map, dict) or not raw_map:
        raise ValueError(f"{COMPLETE_EXTRA_PACK}: raw_material_map.json must be a non-empty object")
    result: dict[str, str] = {}
    for item_id, texture_id in raw_map.items():
        if not isinstance(item_id, str) or not re.fullmatch(r"tacz:[a-z0-9_./-]+", item_id):
            raise ValueError(f"{COMPLETE_EXTRA_PACK}: invalid raw material item id {item_id!r}")
        if not isinstance(texture_id, str) or not re.fullmatch(r"tacz_extra:item/[a-z0-9_./-]+", texture_id):
            raise ValueError(f"{COMPLETE_EXTRA_PACK}: invalid raw material texture {texture_id!r}")
        texture_path = resource_asset_path(texture_id, "textures", ".png")
        model_path = resource_asset_path(texture_id, "models", ".json")
        if texture_path not in embedded or model_path not in embedded:
            raise ValueError(f"{COMPLETE_EXTRA_PACK}: raw material {item_id} lacks embedded texture/model")
        model = json.loads(embedded[model_path].decode("utf-8"))
        if model.get("textures", {}).get("layer0") != texture_id:
            raise ValueError(f"{model_path}: raw material layer0 does not match {texture_id}")
        result[item_id] = texture_id
    return result

def complete_mapping_entry_id(identity: str) -> str:
    slug = re.sub(r"[^a-z0-9]+", "_", identity.lower()).strip("_")[:72]
    digest = hashlib.sha1(identity.encode("utf-8")).hexdigest()[:10]
    return f"complete_{slug}_{digest}"


def generated_complete_icon_mapping(platforms: list[dict[str, Any]], cartridges: list[dict[str, Any]],
                                    machine_assets: list[dict[str, str]], base_mapping: dict[str, Any],
                                    embedded: dict[Path, bytes]) -> tuple[dict[str, Any], dict[str, str], dict[str, Any]]:
    """Adapt the user exact-map schema to IndustryIconManager's item/NBT schema.

    The user map deliberately names visual identities (``component:ak:barrel``)
    instead of Java/NBT selector fields. The generated catalog is the canonical
    bridge between those identities and the actual generic TACZ ItemStacks.
    """
    exact = load_complete_exact_icons()
    base_catalog = build_icon_catalog(platforms, cartridges, machine_assets, base_mapping, embedded)
    missing = {entry["identity"]: entry for entry in base_catalog["entries"] if entry["needs_art"]}
    if set(exact) != set(missing):
        only_art = sorted(set(exact) - set(missing))
        only_runtime = sorted(set(missing) - set(exact))
        raise ValueError(
            f"{COMPLETE_EXTRA_PACK}: exact identity set does not match current visual backlog; "
            f"art-only={only_art[:5]} ({len(only_art)}), runtime-only={only_runtime[:5]} ({len(only_runtime)})"
        )

    art_validation = validate_complete_pack_art(exact, embedded)
    entries: list[dict[str, Any]] = []
    static_item_models: dict[str, str] = {}
    seen_ids: set[str] = set()
    for identity, texture_name in sorted(exact.items()):
        source = missing[identity]
        entry_id = complete_mapping_entry_id(identity)
        if entry_id in seen_ids:
            raise ValueError(f"{COMPLETE_EXTRA_PACK}: generated duplicate mapping id for {identity}")
        seen_ids.add(entry_id)
        entry = {
            "id": entry_id,
            "item": source["item"],
            "texture": complete_texture_identifier(texture_name),
            "priority": 800,
            "match": source["selectors"],
            "coverage": "exact",
            "source_identity": identity,
        }
        entries.append(entry)
        if source["category"] == "static_industrial_item":
            static_item_models[source["item"]] = complete_texture_identifier(texture_name)

    for item_id, texture_id in load_complete_raw_material_models(embedded).items():
        old = static_item_models.get(item_id)
        if old is not None and old != texture_id:
            raise ValueError(f"{COMPLETE_EXTRA_PACK}: conflicting static visual source for {item_id}")
        static_item_models[item_id] = texture_id

    return {
        "schema_version": 1,
        "description": "Generated adapter for TACZ_extra_COMPLETE exact identity art; do not edit by hand.",
        "entries": entries,
    }, static_item_models, art_validation


def generated_complete_static_item_models(static_item_models: dict[str, str]) -> dict[Path, Any]:
    """Point vanilla static item models at complete-pack art; dynamic NBT items use complete.json."""
    files: dict[Path, Any] = {}
    for item_id, texture_id in sorted(static_item_models.items()):
        namespace, path = item_id.split(":", 1)
        if namespace != "tacz":
            raise ValueError(f"Complete static art currently supports tacz registry items only: {item_id}")
        files[RESOURCE_ROOT / f"assets/tacz/models/item/{path}.json"] = {
            "parent": texture_id
        }
    return files


def build_complete_pack_compatibility_report(art_validation: dict[str, Any], complete_mapping: dict[str, Any],
                                              static_item_models: dict[str, str]) -> dict[str, Any]:
    pack_meta = read_complete_pack_json("pack.mcmeta")
    pack_format = pack_meta.get("pack", {}).get("pack_format") if isinstance(pack_meta, dict) else None
    return {
        "schema_version": 1,
        "source": {
            "archive": str(COMPLETE_EXTRA_PACK.relative_to(REPO)),
            "sha256": hashlib.sha256(COMPLETE_EXTRA_PACK.read_bytes()).hexdigest(),
            "declared_pack_format": pack_format,
        },
        "compatibility": {
            "standalone_pack_format_compatible": pack_format == 88,
            "raw_exact_schema_directly_loadable": False,
            "raw_rules_schema_directly_loadable": False,
            "adapted_runtime_mapping": str(COMPLETE_RUNTIME_MAPPING.relative_to(RESOURCE_ROOT)),
            "raw_mapping_files_embedded": False,
        },
        "art_validation": art_validation,
        "generated_mapping_entry_count": len(complete_mapping["entries"]),
        "generated_static_item_model_count": len(static_item_models),
        "static_item_models": static_item_models,
        "notes": [
            "The complete pack's bare identity->texture map is correct authoring data, but IndustryIconManager requires item/NBT selector entries; complete.json is the generated adapter.",
            "industry_icon_rules.json contains family/tint authoring rules. The current runtime does not execute that custom rule language, so it is preserved only in the source archive and is not copied into assets/tacz_extra/industry_icons.",
            "The complete pack's pack_format 15 is not standalone-compatible with Minecraft 26.2 resource format 88. Its assets are embedded under the mod's own current pack metadata instead.",
            "The original 61-icon zh_cn language keys and the complete pack's raw-material zh_cn keys are merged rather than allowing the shorter later file to erase earlier names.",
        ],
    }


def render_complete_pack_compatibility_document(report: dict[str, Any]) -> str:
    validation = report["art_validation"]
    compatibility = report["compatibility"]
    lines = [
        "# TACZ Extra Complete 包兼容性与整合结果",
        "",
        "用户提供的 `TACZ_extra_COMPLETE.zip` 已逐项核对。它是完整高保真图源，",
        "但其原始映射 JSON 不是本项目运行时直接读取的 schema，因此由生成器转换而不是原样加载。",
        "",
        "## 资源核对",
        "",
        f"- 原 ZIP SHA-256：`{report['source']['sha256']}`；",
        f"- 物品 PNG：{validation['complete_item_texture_count']}，尺寸分布：{validation['texture_dimensions']}；",
        f"- 物品模型：{validation['complete_item_model_count']}（generated：{validation['generated_item_model_count']}，方块父模型：{validation['block_parent_item_model_count']}，自定义 display：{validation['custom_display_item_model_count']}）；",
        f"- 用户 exact 身份映射：{validation['raw_exact_entry_count']}；与当前精确缺图身份一一对应；",
        f"- 原料静态物品映射：{validation['raw_material_entry_count']}；",
        f"- 生成的运行时 NBT 映射：{report['generated_mapping_entry_count']} 条；",
        f"- 额外静态物品模型包装：{report['generated_static_item_model_count']} 条。",
        "",
        "## 原写法在本环境中的结论",
        "",
        f"- `pack.mcmeta.pack_format = {report['source']['declared_pack_format']}`，不是 26.2 standalone 包格式 88：**不能直接当独立资源包安装**；",
        "- `industry_icon_exact.json`（`identity -> texture_name`）语义正确，但不是 `assets/*/industry_icons/*.json` 的 `entries[]` runtime schema；",
        "- `industry_icon_rules.json` 的 family/tint 规则是作者规则语言，当前 Java 映射器不会执行；",
        "- 因此整合方式是：嵌入模型/PNG，生成 `assets/tacz/industry_icons/complete.json`，不嵌入那三份原始 authoring JSON 以免被错误解析。",
        "",
        "## 已覆盖的静态物品",
        "",
    ]
    for item_id, texture in sorted(report["static_item_models"].items()):
        lines.append(f"- `{item_id}` → `{texture}`")
    lines.extend([
        "",
        "完整 source/report 见：",
        f"- `{report['source']['archive']}`",
        "- `extras/icon_packs/TACZ_extra_COMPLETE_compatibility_report.json`",
        "- `extras/icon_packs/TACZ_industry_icon_catalog.json`",
        "",
    ])
    return "\n".join(lines)

def mapping_matches(entry: dict[str, Any], item: str, selectors: dict[str, Any]) -> bool:
    if entry.get("item") != item:
        return False
    match = entry.get("match", {})
    return all(selectors.get(key, "") == value for key, value in match.items())


def mapping_specificity(entry: dict[str, Any]) -> int:
    match = entry.get("match", {})
    return len(match) if isinstance(match, dict) else 0


def resolve_icon_mapping(entries: list[dict[str, Any]], item: str, selectors: dict[str, Any]) -> dict[str, Any] | None:
    candidates = [entry for entry in entries if mapping_matches(entry, item, selectors)]
    if not candidates:
        return None
    # This is intentionally identical to IndustryIconManager's runtime order:
    # explicit priority, then selector specificity, then deterministic id.
    return sorted(
        candidates,
        key=lambda entry: (-entry.get("priority", 0), -mapping_specificity(entry), entry["id"]),
    )[0]


def selector_text(item: str, selectors: dict[str, Any]) -> str:
    pieces = [item]
    pieces.extend(f"{key}={selectors[key]}" for key in ICON_SELECTOR_KEYS if selectors.get(key))
    return " ; ".join(pieces)


def build_icon_identity(category: str, identity: str, item: str, selectors: dict[str, Any],
                        label: str, accepted_coverage: tuple[str, ...], asset_key: str,
                        asset_strategy: str = "individual", mapping_eligible: bool = True) -> dict[str, Any]:
    return {
        "category": category,
        "identity": identity,
        "item": item,
        "selectors": {key: value for key, value in selectors.items() if value},
        "selector": selector_text(item, selectors),
        "label": label,
        "accepted_coverage": list(accepted_coverage),
        "asset_key": asset_key,
        "asset_strategy": asset_strategy,
        "mapping_eligible": mapping_eligible,
    }


def generated_icon_identities(platforms: list[dict[str, Any]], cartridges: list[dict[str, Any]],
                              machine_assets: list[dict[str, str]]) -> list[dict[str, Any]]:
    """Enumerate every current runtime visual identity, not broad item classes.

    This deliberately emits the 53 platform blueprints, 265 components, 265
    component dies, and all calibre variants separately.  A later artwork pass
    may decide that several identities share one family texture, but the list
    never hides those concrete identities behind a vague category name.
    """
    identities: list[dict[str, Any]] = []

    # Loose finished ammo uses AmmoId, while cases/projectiles use the explicit
    # cartridge identity written by IndustryItemBuilder and recipe components.
    for cartridge in sorted(cartridges, key=lambda value: value["id"]):
        caliber = cartridge["id"]
        ammo = cartridge["ammo"]
        projectile_type = cartridge["projectile_type"]
        identities.append(build_icon_identity(
            "loose_ammo", f"ammo:{ammo}", "tacz:ammo", {"ammo_id": ammo}, ammo,
            ("exact",), f"ammo:{caliber}", "individual"
        ))
        if cartridge["eject_case"]:
            case_selectors = {"industry_part_kind": "case", "cartridge_caliber": caliber}
            identities.append(build_icon_identity(
                "fresh_cartridge_case", f"case:{caliber}", "tacz:cartridge_case", case_selectors,
                cartridge["case_name_en"], ("exact", "family"), f"fresh_case:{caliber}", "family"
            ))
            spent_selectors = {"industry_part_kind": "spent_case", "cartridge_caliber": caliber}
            identities.append(build_icon_identity(
                "spent_cartridge_case", f"spent_case:{caliber}", "tacz:cartridge_case", spent_selectors,
                cartridge["spent_case_name_en"], ("exact",), f"spent_case:{caliber}", "family_or_individual"
            ))
        identities.append(build_icon_identity(
            "projectile_core", f"projectile:{caliber}:{projectile_type}", "tacz:projectile_core",
            {
                "industry_part_kind": "projectile",
                "cartridge_caliber": caliber,
                "projectile_type": projectile_type,
            },
            cartridge["projectile_name_en"], ("exact", "family"),
            f"projectile:{caliber}:{projectile_type}", "family_or_individual"
        ))

        # The RPG/40 mm production path deliberately creates real, visible
        # intermediate stacks. They therefore need separate entries and cannot
        # be dismissed as a sequenced-assembly transient.
        payloads = cartridge.get("projectile_payloads", [])
        if payloads:
            identities.append(build_icon_identity(
                "visible_projectile_intermediate", f"projectile_body:{caliber}", "tacz:projectile_blank",
                {"industry_part_kind": f"projectile_body_{caliber}", "cartridge_caliber": caliber},
                cartridge["projectile_body_name_en"], ("exact",), f"projectile_body:{caliber}"
            ))
            payload_index = 0
            for payload in payloads:
                for _ in range(payload["count"]):
                    payload_index += 1
                    identities.append(build_icon_identity(
                        "visible_projectile_intermediate",
                        f"projectile_payload:{caliber}:{payload_index}", "tacz:projectile_blank",
                        {
                            "industry_part_kind": f"projectile_payload_{caliber}_{payload_index}",
                            "cartridge_caliber": caliber,
                        },
                        cartridge["projectile_payload_names_en"][payload_index - 1], ("exact",),
                        f"projectile_payload:{caliber}:{payload_index}"
                    ))

        identities.append(build_icon_identity(
            "cartridge_case_die", f"case_die:{caliber}", "tacz:press_die",
            {"industry_part_kind": "case_die", "cartridge_caliber": caliber},
            cartridge["case_die_name_en"], ("exact",), f"case_die:{caliber}"
        ))
        identities.append(build_icon_identity(
            "cartridge_projectile_die", f"projectile_die:{caliber}:{projectile_type}", "tacz:press_die",
            {
                "industry_part_kind": "projectile_die",
                "cartridge_caliber": caliber,
                "projectile_type": projectile_type,
            },
            cartridge["projectile_die_name_en"], ("exact",), f"projectile_die:{caliber}:{projectile_type}"
        ))
        gauge = cartridge.get("calibration_gauge")
        if isinstance(gauge, dict):
            identities.append(build_icon_identity(
                "cartridge_gauge", f"cartridge_gauge:{caliber}", "tacz:press_die",
                {"industry_part_kind": "cartridge_gauge", "cartridge_caliber": caliber},
                gauge["name_en"], ("exact",), f"cartridge_gauge:{caliber}"
            ))

    # Shared physical blanks are real stacks and need listed visual identities
    # even though their eventual calibre/platform is intentionally not known yet.
    for kind, item, label in (
        ("case_blank", "tacz:cartridge_case_blank", "Neutral Cartridge Case Blank"),
        ("projectile_blank", "tacz:projectile_blank", "Neutral Projectile Blank"),
        ("case_die_blank", "tacz:press_die", "Blank Case Die"),
        ("projectile_die_blank", "tacz:press_die", "Blank Projectile Die"),
    ):
        identities.append(build_icon_identity(
            "shared_ammunition_intermediate", f"ammunition:{kind}", item,
            {"industry_platform": "ammunition", "industry_part_kind": kind}, label,
            ("exact",), f"ammunition:{kind}"
        ))

    for kind, label in (
        ("receiver_blank", "Neutral Receiver Blank"),
        ("bolt_blank", "Neutral Bolt Blank"),
        ("barrel_blank", "Neutral Barrel Blank"),
        ("trigger_blank", "Neutral Fire-Control Blank"),
        ("recoil_blank", "Neutral Recoil Blank"),
        ("furniture_blank", "Neutral Exterior / Furniture Blank"),
    ):
        identities.append(build_icon_identity(
            "shared_gun_intermediate", f"machining:{kind}", "tacz:gun_component_blank",
            {"industry_platform": "machining", "industry_part_kind": kind}, label,
            ("exact",), f"machining:{kind}"
        ))

    for platform in sorted(platforms, key=lambda value: value["platform"]):
        platform_id = platform["platform"]
        blueprint = platform["blueprint"]
        identities.append(build_icon_identity(
            "platform_blueprint", f"blueprint:{platform_id}", "tacz:gun_blueprint",
            {"industry_platform": platform_id, "industry_part_kind": "blueprint"},
            blueprint["name_en"], ("exact",), f"blueprint:{platform_id}"
        ))
        for part in platform["parts"]:
            kind = part["kind"]
            structural = part["structural"]
            identities.append(build_icon_identity(
                "platform_component", f"component:{platform_id}:{kind}", "tacz:gun_component",
                {"industry_platform": platform_id, "industry_part_kind": kind},
                part["name_en"], ("exact",), f"component:{platform_id}:{kind}"
            ))
            identities.append(build_icon_identity(
                "platform_component_die", f"component_die:{platform_id}:{kind}", "tacz:press_die",
                {
                    "industry_platform": platform_id,
                    "industry_part_kind": "component_die",
                    "die_target_kind": kind,
                },
                part.get("die_name_en", f"{part['name_en']} Die"), ("exact",),
                f"component_die:{platform_id}:{kind}"
            ))
            # `structural` is intentionally not a selector on the final die;
            # it is included in the label to expose the actual blank route.
            identities[-1]["source_structural_blank"] = structural
        if platform["materials"]:
            label = f"{platform_display_label(platform, 'en_us')} Exterior Kit"
            identities.append(build_icon_identity(
                "platform_furniture_kit", f"furniture_kit:{platform_id}", "tacz:gun_component",
                {"industry_platform": platform_id, "industry_part_kind": "furniture_kit"},
                label, ("exact",), f"furniture_kit:{platform_id}"
            ))

    # Current physical external feed definitions. Internal/tube/revolver feeds
    # intentionally do not fabricate a tacz:magazine stack, so they are not
    # falsely counted as missing magazine icons here.
    feed_root = RESOURCE_ROOT / "data/tacz/industry/gun_feed"
    feeds_by_identity: dict[tuple[str, str, int], dict[str, Any]] = {}
    for path in sorted(feed_root.glob("*.json")):
        feed = read_json(path)
        if feed.get("mechanism") not in {"detachable_magazine", "belt"}:
            continue
        key = (feed["magazine_family"], feed["ammo"], feed["magazine_capacity"])
        # Several guns deliberately share one physical carrier identity (for
        # example a 30-round STANAG). Do not collapse different capacities:
        # AK 30-round and RPK 40-round stacks have the same family but need
        # different visual identities and atlas keys.
        feeds_by_identity.setdefault(key, {**feed, "_source": path.stem})
    for (family, ammo, capacity), feed in sorted(feeds_by_identity.items()):
        identity_suffix = f"{family}:{capacity}:{ammo}"
        identities.append(build_icon_identity(
            "physical_magazine", f"magazine:{identity_suffix}", "tacz:magazine",
            {
                "magazine_family": family,
                "magazine_ammo_id": ammo,
                "magazine_capacity": capacity,
            },
            feed["display_name"], ("exact",), f"magazine:{identity_suffix}"
        ))

    # These are static registry-item models rather than NBT-generic stacks.
    # The two machine items now have supplied high-detail Blockbench parents;
    # the remaining three stay in the audit until their final item art exists.
    supplied_models = supplied_machine_item_visuals(machine_assets)
    for item, label in (
        ("tacz:magazine_blank", "Neutral Magazine Blank"),
        ("tacz:cartridge_assembly_machine", "Cartridge Assembly Machine"),
        ("tacz:industrial_salvage_station", "Industrial Salvage Station"),
        ("tacz:magazine_pouch", "Magazine Pouch"),
        ("tacz:magazine_loader", "Magazine Loader"),
    ):
        accepted = ("exact", "supplied_block_model") if item in supplied_models else ("exact",)
        identity = build_icon_identity(
            "static_industrial_item", f"static:{item}", item, {}, label,
            accepted, f"static:{item}", "individual", False
        )
        if item in supplied_models:
            identity["supplied_block_model"] = supplied_models[item]
        identities.append(identity)

    return sorted(identities, key=lambda value: (value["category"], value["identity"]))


def embedded_icon_texture_inventory(embedded: dict[Path, bytes], mapping_entries: list[dict[str, Any]]) -> list[dict[str, Any]]:
    referenced: dict[str, list[str]] = defaultdict(list)
    for entry in mapping_entries:
        texture = entry["texture"]
        if texture.startswith("tacz_extra:"):
            referenced[texture].append(entry["id"])
    inventory: list[dict[str, Any]] = []
    # This inventory is deliberately the icon library only. Supplied block
    # atlases are audited separately in TACZ_industry_blocks_asset_report.json.
    texture_root = RESOURCE_ROOT / "assets/tacz_extra/textures"
    item_prefix = texture_root / "item"
    for path in sorted(embedded):
        if path.suffix != ".png" or item_prefix not in path.parents:
            continue
        relative = path.relative_to(texture_root).with_suffix("")
        texture = f"tacz_extra:{relative.as_posix()}"
        inventory.append({
            "texture": texture,
            "bound_by": sorted(referenced.get(texture, [])),
            "status": "bound" if texture in referenced else "available_unbound",
        })
    return inventory


def build_icon_catalog(platforms: list[dict[str, Any]], cartridges: list[dict[str, Any]],
                       machine_assets: list[dict[str, str]], mapping: dict[str, Any],
                       embedded: dict[Path, bytes]) -> dict[str, Any]:
    entries = mapping["entries"]
    identities = generated_icon_identities(platforms, cartridges, machine_assets)
    category_summary: dict[str, Counter[str]] = defaultdict(Counter)
    missing_groups: dict[str, list[dict[str, Any]]] = defaultdict(list)

    for identity in identities:
        mapping_entry = resolve_icon_mapping(entries, identity["item"], identity["selectors"])
        supplied_model = identity.get("supplied_block_model")
        if supplied_model is not None:
            coverage = "supplied_block_model"
            identity["mapping"] = "supplied_block_model"
            identity["texture"] = supplied_model
        else:
            coverage = mapping_entry.get("coverage", "exact") if mapping_entry else "runtime_fallback"
            identity["mapping"] = None if mapping_entry is None else mapping_entry["id"]
            identity["texture"] = None if mapping_entry is None else mapping_entry["texture"]
        accepted = set(identity["accepted_coverage"])
        covered = coverage in accepted
        identity["current_coverage"] = coverage
        identity["covered"] = covered
        identity["needs_art"] = not covered
        category_summary[identity["category"]]["total"] += 1
        category_summary[identity["category"]][coverage] += 1
        category_summary[identity["category"]]["covered" if covered else "needs_art"] += 1
        if not covered:
            asset_key = identity["asset_key"]
            # Existing fresh-case family art is intentionally accepted. Fired
            # cases use that same texture only as a placeholder, so group their
            # future dark/dented artwork by its current case-family texture.
            if identity["category"] == "spent_cartridge_case" and identity["texture"]:
                asset_key = "spent_variant:" + identity["texture"].rsplit("/", 1)[-1]
                identity["asset_key"] = asset_key
            missing_groups[asset_key].append(identity)

    missing_art = []
    for asset_key, members in sorted(missing_groups.items()):
        missing_art.append({
            "asset_key": asset_key,
            "identity_count": len(members),
            "categories": sorted({member["category"] for member in members}),
            "asset_strategy": sorted({member["asset_strategy"] for member in members}),
            "identities": [member["identity"] for member in members],
        })

    summary = {
        category: dict(sorted(counts.items()))
        for category, counts in sorted(category_summary.items())
    }
    return {
        "schema_version": 1,
        "description": (
            "Exact runtime visual-identity audit generated from the industrial manifests and the client icon mapping. "
            "This file lists concrete NBT selectors; it does not collapse missing work into broad classes."
        ),
        "sources": {
            "icon_mapping": str(ICON_MAPPING_MANIFEST.relative_to(REPO)),
            "fixed_icon_pack": str(FIXED_ICON_PACK.relative_to(REPO)),
            "industry_block_pack": str(INDUSTRY_BLOCK_PACK.relative_to(REPO)),
            "platform_manifest_count": len(platforms),
            "cartridge_manifest_count": len(cartridges),
        },
        "summary": summary,
        "provided_icon_textures": embedded_icon_texture_inventory(embedded, entries),
        "entries": identities,
        "missing_art": missing_art,
        "planned_not_currently_mappable": [
            {
                "asset_key": "rpg_motor_housing",
                "reason": (
                    "The current RPG route has a warhead body, explosive-charge body, shaped-charge preform, "
                    "and final HEAT core, but no separate motor-housing ItemStack/NBT stage. Add that real stage "
                    "before attempting to bind an icon."
                ),
            },
            {
                "asset_key": "internal_feed_carriers",
                "reason": (
                    "Tube, revolver, double-barrel, and internal-box guns currently store rounds in gun data, not "
                    "in a physical tacz:magazine ItemStack. Their gun/feed UI needs a separate renderer contract; "
                    "they are deliberately not mislabeled as missing MagazineFamily icons."
                ),
            },
        ],
    }


def render_icon_coverage_document(catalog: dict[str, Any]) -> str:
    """Create a human-readable companion to the canonical machine-readable catalog."""
    lines = [
        "# 工业图标覆盖清单（精确身份）",
        "",
        "此文件由 `tools/generate_industry_content.py` 生成。它不是按“弹药/弹匣/工业物品”",
        "这种宽泛类别罗列，而是逐个列出当前运行时实际可出现的 `item + NBT selector` 身份。",
        "完整可供程序处理的源数据是 `extras/icon_packs/TACZ_industry_icon_catalog.json`。",
        "",
        "## 判定规则",
        "",
        "- **exact**：已有该具体身份的图；",
        "- **family**：已有有意复用的同工艺视觉族（例如新鲜黄铜手枪壳）；",
        "- **placeholder**：暂时能画出来，但不能冒充完成品（例如已击发弹壳仍借用新壳图）；",
        "- **supplied_block_model**：已由用户提供的实体方块模型/贴图覆盖；",
        "- **runtime_fallback**：没有映射条目，运行时退回原有 TACZ 图；",
        "- `needs_art = true` 的每一行都是仍需补图的具体身份。",
        "",
        "## 汇总",
        "",
        "| 类别 | 总身份数 | 已满足 | 仍需补图 | exact | family | placeholder | supplied block model | runtime fallback |",
        "| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |",
    ]
    for category, counts in catalog["summary"].items():
        lines.append(
            f"| {category} | {counts.get('total', 0)} | {counts.get('covered', 0)} | "
            f"{counts.get('needs_art', 0)} | {counts.get('exact', 0)} | {counts.get('family', 0)} | "
            f"{counts.get('placeholder', 0)} | {counts.get('supplied_block_model', 0)} | "
            f"{counts.get('runtime_fallback', 0)} |"
        )

    lines.extend([
        "",
        "## 仍缺失的精确视觉身份",
        "",
        "下表每一行都可以直接变成 `assets/<namespace>/industry_icons/*.json` 中的一条映射；",
        "`需要的图键` 相同表示一张有意共享的视觉族图可以覆盖多个身份。",
        "",
        "| 类别 | 精确身份 | 运行时 selector | 当前状态 | 需要的图键 |",
        "| --- | --- | --- | --- | --- |",
    ])
    missing = [entry for entry in catalog["entries"] if entry["needs_art"]]
    for entry in missing:
        selector = entry["selector"].replace("|", "\\|")
        lines.append(
            f"| {entry['category']} | `{entry['identity']}` | `{selector}` | "
            f"{entry['current_coverage']} | `{entry['asset_key']}` |"
        )

    lines.extend([
        "",
        "## 已提供但尚未绑定的图",
        "",
        "这些 PNG 已嵌入运行时 `tacz_extra` 命名空间，但当前默认工业数据没有对应的实际身份。",
        "它们保留给以后新增物理供弹器或第三方映射，未被强行套到不匹配的枪械上。",
        "",
        "| 纹理 |",
        "| --- |",
    ])
    for asset in catalog["provided_icon_textures"]:
        if asset["status"] == "available_unbound":
            lines.append(f"| `{asset['texture']}` |")

    lines.extend([
        "",
        "## 尚不存在可绑定身份的设计项",
        "",
    ])
    for entry in catalog["planned_not_currently_mappable"]:
        lines.append(f"- `{entry['asset_key']}`：{entry['reason']}")
    lines.append("")
    return "\n".join(lines)


def generated_icon_mapping_files(platforms: list[dict[str, Any]], cartridges: list[dict[str, Any]],
                                 machine_assets: list[dict[str, str]]) -> dict[Path, bytes | Any]:
    base_mapping = load_icon_mapping()
    embedded = merged_tacz_extra_files()
    geometry_report = apply_complete_icon_geometry_overrides(embedded)
    validate_icon_texture_references(base_mapping, embedded)
    validate_industry_block_assets(machine_assets, embedded)

    complete_mapping, static_item_models, art_validation = generated_complete_icon_mapping(
        platforms, cartridges, machine_assets, base_mapping, embedded
    )
    validate_icon_texture_references(complete_mapping, embedded)
    combined_mapping = {
        "schema_version": 1,
        "entries": [*base_mapping["entries"], *complete_mapping["entries"]],
    }
    catalog = build_icon_catalog(platforms, cartridges, machine_assets, combined_mapping, embedded)
    compatibility = build_complete_pack_compatibility_report(
        art_validation, complete_mapping, static_item_models
    )

    files: dict[Path, bytes | Any] = dict(embedded)
    files[ICON_RUNTIME_MAPPING] = base_mapping
    files[COMPLETE_RUNTIME_MAPPING] = complete_mapping
    files.update(generated_complete_static_item_models(static_item_models))
    files[ICON_CATALOG] = catalog
    files[ICON_COVERAGE_DOCUMENT] = render_icon_coverage_document(catalog)
    files[COMPLETE_PACK_REPORT] = compatibility
    files[COMPLETE_PACK_COVERAGE_DOCUMENT] = render_complete_pack_compatibility_document(compatibility)
    files[ICON_GEOMETRY_REPORT] = geometry_report
    files[ICON_GEOMETRY_DOCUMENT] = render_complete_icon_geometry_document(geometry_report)
    return files


def build_industry_block_asset_report(machine_assets: list[dict[str, str]], embedded: dict[Path, bytes],
                                      catalog: dict[str, Any]) -> dict[str, Any]:
    """Report the supplied models and the remaining visual-art backlog separately."""
    machines = validate_industry_block_assets(machine_assets, embedded)
    for machine in machines:
        machine_id = machine["id"]
        machine["tacz_wrapper_model"] = f"assets/tacz/models/block/{machine_id}.json"
        machine["tacz_registry_blockstate"] = f"assets/tacz/blockstates/{machine_id}.json"
        machine["tacz_item_model"] = f"assets/tacz/models/item/{machine_id}.json"
        machine["status"] = "bound"

    missing = [entry for entry in catalog["entries"] if entry["needs_art"]]
    category_counts: dict[str, int] = Counter(entry["category"] for entry in missing)
    static_remaining = [
        {
            "identity": entry["identity"],
            "item": entry["item"],
            "asset_key": entry["asset_key"],
        }
        for entry in missing if entry["category"] == "static_industrial_item"
    ]
    return {
        "schema_version": 1,
        "source": {
            "archive": str(INDUSTRY_BLOCK_PACK.relative_to(REPO)),
            "sha256": hashlib.sha256(INDUSTRY_BLOCK_PACK.read_bytes()).hexdigest(),
            "machine_manifest": str(MACHINE_MANIFEST.relative_to(REPO)),
            "icon_catalog": str(ICON_CATALOG.relative_to(REPO)),
        },
        "applied_machine_models": machines,
        "registered_machine_count": len(machines),
        "remaining_visual_backlog": {
            "exact_runtime_identity_count": len(missing),
            "art_group_count": len(catalog["missing_art"]),
            "by_category": dict(sorted(category_counts.items())),
            "remaining_static_industrial_items": static_remaining,
        },
        "scope_notes": [
            "The two supplied 128x128 Blockbench atlases are bound to their real tacz registry blocks through tacz model wrappers.",
            "exact_runtime_identity_count is a high-fidelity identity backlog, not a claim that every line requires a separate PNG; shared family art may intentionally satisfy several identities.",
            "All exact selectors and suggested art keys remain in TACZ_industry_icon_catalog.json and INDUSTRY_ICON_COVERAGE.md.",
        ],
    }


def render_industry_block_asset_coverage(report: dict[str, Any]) -> str:
    backlog = report["remaining_visual_backlog"]
    lines = [
        "# 工业方块模型与材质覆盖",
        "",
        "此文件由 `tools/generate_industry_content.py` 生成。它区分已经能正确解析的实际模型/PNG",
        "和仍待补齐的高保真身份图，不把“已有基础后备图”误报成完成。",
        "",
        "## 已应用的用户方块资源",
        "",
        "| 方块 | 源模型 | 图集尺寸 | 元素数 | 面数 | AO | 绑定状态 |",
        "| --- | --- | ---: | ---: | ---: | --- | --- |",
    ]
    for machine in report["applied_machine_models"]:
        width, height = machine["texture_size"]
        lines.append(
            f"| `tacz:{machine['id']}` | `{machine['source_model']}` | {width}×{height} | "
            f"{machine['element_count']} | {machine['face_count']} | {machine['ambientocclusion']} | {machine['status']} |"
        )
    lines.extend([
        "",
        "两个模型保留在 `tacz_extra` 命名空间；实际注册方块通过 `tacz:block/...` 父模型包装引用它们。",
        "方块现在拥有水平 `facing` 状态，对应用户包提供的四个旋转 blockstate 变体；旧世界无此状态的方块会采用默认北向。",
        "",
        "## 仍待补齐的高保真视觉身份",
        "",
        f"- 仍缺 **{backlog['exact_runtime_identity_count']}** 个精确运行时视觉身份；",
        f"- 按可共享图键归并后是 **{backlog['art_group_count']}** 个待办组；",
        "- 这不是“当前会紫黑”的文件数：所有已注册工业物品/方块仍有后备模型或贴图。",
        "",
        "| 类别 | 仍缺精确身份数 |",
        "| --- | ---: |",
    ])
    for category, count in backlog["by_category"].items():
        lines.append(f"| {category} | {count} |")
    lines.extend([
        "",
        "### 仍缺的静态工业物品图",
        "",
    ])
    for item in backlog["remaining_static_industrial_items"]:
        lines.append(f"- `{item['item']}` — `{item['asset_key']}`")
    lines.extend([
        "",
        "完整到每个 `item + NBT selector` 的清单见：",
        "`extras/icon_packs/TACZ_industry_icon_catalog.json` 与 `docs/INDUSTRY_ICON_COVERAGE.md`。",
        "",
    ])
    return "\n".join(lines)


def generated_industry_block_asset_files(machine_assets: list[dict[str, str]],
                                         embedded: dict[Path, bytes], catalog: dict[str, Any]) -> dict[Path, bytes | Any]:
    report = build_industry_block_asset_report(machine_assets, embedded, catalog)
    return {
        INDUSTRY_BLOCK_ASSET_REPORT: report,
        INDUSTRY_BLOCK_COVERAGE_DOCUMENT: render_industry_block_asset_coverage(report),
    }

def existing_json_matches(path: Path, expected: Any) -> bool:
    try:
        return canonical(read_json(path)) == canonical(expected)
    except (OSError, json.JSONDecodeError):
        return False


def update_language(path: Path, entries: dict[str, str], write: bool) -> list[str]:
    current = read_json(path)
    stale = [key for key, value in entries.items() if current.get(key) != value]
    if write and stale:
        current.update(entries)
        write_json(path, current)
    return stale


def run(write: bool) -> int:
    explicit_platforms = load_platforms()
    auto_platforms = discover_default_platforms({platform["slug"] for platform in explicit_platforms})
    platforms = [*explicit_platforms, *auto_platforms]
    signatures = {canonical(sorted(platform["blueprint"]["ingredients"])) for platform in explicit_platforms}
    for platform in auto_platforms:
        signature = canonical(sorted(platform["blueprint"]["ingredients"]))
        if signature in signatures:
            raise ValueError(f"{DEFAULT_GUN_POLICY}: auto blueprint signature collision for {platform['slug']}")
        signatures.add(signature)
    cartridges = load_cartridges()
    machine_assets = load_machine_assets()
    blueprint_acquisition = load_blueprint_acquisition()
    expected: dict[Path, Any] = {}
    english: dict[str, str] = {
        "item.tacz.gun_component_blank.furniture": "Neutral Exterior / Furniture Blank",
        "tooltip.tacz.blueprint.tier.legacy": "Legacy Field Pattern — low tooling complexity",
        "tooltip.tacz.blueprint.tier.service": "Service Schematic — standardized production",
        "tooltip.tacz.blueprint.tier.advanced": "Modern Technical Package — advanced tooling required",
        "tooltip.tacz.blueprint.tier.precision": "Restricted Precision Dossier — rare tooling required",
        "gui.tacz.cartridge_assembly.bays": "MATERIAL BAYS",
        "gui.tacz.cartridge_assembly.status": "CRIMP / LOAD STATUS",
        "gui.tacz.cartridge_assembly.assemble_hint": "Validate the four bays, then assemble one declared cartridge batch.",
        "gui.tacz.industrial_salvage.inspect": "INSPECTION / CUTTER LINE",
        "gui.tacz.industrial_salvage.status": "RECOVERY STATUS",
        "gui.tacz.industrial_salvage.salvage_hint": "Inspect the input and recover only items with an industrial provenance tag.",
    }
    chinese: dict[str, str] = {
        "item.tacz.gun_component_blank.furniture": "中性外装套件毛坯",
        "tooltip.tacz.blueprint.tier.legacy": "野战图样 — 低工具复杂度",
        "tooltip.tacz.blueprint.tier.service": "制式图纸 — 标准化生产",
        "tooltip.tacz.blueprint.tier.advanced": "现代技术包 — 需要高级工装",
        "tooltip.tacz.blueprint.tier.precision": "受限精密档案 — 需要稀有工装",
        "gui.tacz.cartridge_assembly.bays": "材料工位",
        "gui.tacz.cartridge_assembly.status": "压接 / 装填状态",
        "gui.tacz.cartridge_assembly.assemble_hint": "核对四个材料工位后，装配一批已声明弹药。",
        "gui.tacz.industrial_salvage.inspect": "检验 / 切割工位",
        "gui.tacz.industrial_salvage.status": "回收状态",
        "gui.tacz.industrial_salvage.salvage_hint": "检验输入物，只回收带有工业来源标记的物品。",
    }
    expected.update(generated_furniture_blank_files(platforms))
    for platform in platforms:
        expected.update(generated_platform_files(platform))
        english.update(language_entries(platform, "en_us"))
        chinese.update(language_entries(platform, "zh_cn"))
    for cartridge in cartridges:
        expected.update(generated_cartridge_files(cartridge))
        english.update(cartridge_language_entries(cartridge, "en_us"))
        chinese.update(cartridge_language_entries(cartridge, "zh_cn"))
    expected.update(generated_blueprint_acquisition_files(platforms, blueprint_acquisition))
    expected.update(generated_magazine_files({cartridge["ammo"] for cartridge in cartridges}))
    expected.update(generated_machine_files(machine_assets))
    expected.update(generated_industrial_gui_files())
    # The repaired icon library and the supplied high-detail machine pack are
    # embedded so players do not need to install either ZIP separately. The
    # same pass emits exact icon and block-asset coverage reports.
    icon_files = generated_icon_mapping_files(platforms, cartridges, machine_assets)
    expected.update(icon_files)
    expected.update(generated_industry_block_asset_files(
        machine_assets, merged_tacz_extra_files(), icon_files[ICON_CATALOG]
    ))

    stale: list[str] = []
    for path, value in sorted(expected.items(), key=lambda pair: str(pair[0])):
        if isinstance(value, bytes):
            matches = path.exists() and path.read_bytes() == value
        elif isinstance(value, str):
            matches = path.exists() and path.read_text(encoding="utf-8") == value
        else:
            matches = existing_json_matches(path, value)
        if not matches:
            stale.append(str(path.relative_to(REPO)))
            if write:
                if isinstance(value, bytes):
                    path.parent.mkdir(parents=True, exist_ok=True)
                    path.write_bytes(value)
                elif isinstance(value, str):
                    path.parent.mkdir(parents=True, exist_ok=True)
                    path.write_text(value, encoding="utf-8")
                else:
                    write_json(path, value)

    # These basic 16×16 cube textures were generator-owned placeholders. The
    # supplied 128×128 Blockbench atlases now own the visual route, so remove
    # obsolete files during --write and make --check reject their return.
    for path in sorted(obsolete_machine_placeholder_files(machine_assets)):
        if path.exists():
            stale.append(str(path.relative_to(REPO)))
            if write:
                path.unlink()

    # Tiered acquisition supersedes the old monolithic level-5 cache/trade
    # set. Keep authored vanilla data untouched; remove only our blueprint_* /
    # industrial_blueprint_cache* generated files.
    for path in sorted(obsolete_blueprint_acquisition_files(expected)):
        stale.append(str(path.relative_to(REPO)))
        if write:
            path.unlink()

    # The complete source archive carries authoring-only bare maps under the
    # tacz_extra namespace. If they leak into the mod they are scanned as
    # malformed runtime mappings, so --write removes them and --check fails.
    for path in sorted(forbidden_complete_authoring_runtime_files()):
        stale.append(str(path.relative_to(REPO)))
        if write:
            path.unlink()

    stale += [f"{path.relative_to(REPO)}::{key}" for path, entries in (
        (RESOURCE_ROOT / "assets/tacz/lang/en_us.json", english),
        (RESOURCE_ROOT / "assets/tacz/lang/zh_cn.json", chinese),
    ) for key in update_language(path, entries, write)]

    mode = "wrote" if write else "checked"
    if stale:
        print(f"Industry generator {mode} {len(stale)} stale output(s):")
        for entry in stale:
            print(f"  {entry}")
        return 0 if write else 1
    print(f"Industry generator {mode}: {len(platforms)} platform manifest(s), {len(cartridges)} cartridge manifest(s), all managed outputs current.")
    return 0


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    group = parser.add_mutually_exclusive_group(required=True)
    group.add_argument("--write", action="store_true", help="write generated resources in place")
    group.add_argument("--check", action="store_true", help="fail if generated resources are stale")
    args = parser.parse_args()
    return run(args.write)


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except (OSError, ValueError, json.JSONDecodeError) as error:
        print(f"Industry generator error: {error}", file=sys.stderr)
        raise SystemExit(2)
