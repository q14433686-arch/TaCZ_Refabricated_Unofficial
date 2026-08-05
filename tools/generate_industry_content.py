#!/usr/bin/env python3
"""Generate deterministic TACZ industrial platform resources.

This is deliberately an authoring-time tool. It writes ordinary resource JSON
that remains inspectable and overridable by datapacks at runtime. See
``tools/industry/README.md`` for usage.
"""
from __future__ import annotations

import argparse
import json
import re
import struct
import sys
import zlib
from collections import Counter
from pathlib import Path
from typing import Any

REPO = Path(__file__).resolve().parents[1]
RESOURCE_ROOT = REPO / "src/main/resources"
PLATFORM_ROOT = REPO / "tools/industry/platforms"
CARTRIDGE_MANIFEST = REPO / "tools/industry/cartridges.json"
DEFAULT_GUN_POLICY = REPO / "tools/industry/default_gun_policy.json"
MACHINE_MANIFEST = REPO / "tools/industry/machines.json"
BLUEPRINT_ACQUISITION_MANIFEST = REPO / "tools/industry/blueprint_acquisition.json"
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
    """Validate independent blueprint discovery sources.

    Trade files use the new 26.2 ``villager_trade`` registry and are attached
    to the vanilla weaponsmith level-5 trade tag. Chest distribution uses the
    existing TACZ loot-injection layer, keeping default gun-pack art untouched.
    """
    data = read_json(BLUEPRINT_ACQUISITION_MANIFEST)
    if not isinstance(data, dict):
        raise ValueError(f"{BLUEPRINT_ACQUISITION_MANIFEST}: expected object")
    trade = data.get("weaponsmith")
    cache = data.get("world_cache")
    if not isinstance(trade, dict) or not isinstance(cache, dict):
        raise ValueError(f"{BLUEPRINT_ACQUISITION_MANIFEST}: missing weaponsmith/world_cache object")
    for key in ("emerald_base", "emerald_per_material", "max_uses", "xp"):
        if not isinstance(trade.get(key), int) or trade[key] < 1:
            raise ValueError(f"{BLUEPRINT_ACQUISITION_MANIFEST}: weaponsmith.{key} must be a positive int")
    chance = cache.get("chance")
    tables = cache.get("loot_tables")
    if not isinstance(chance, (int, float)) or not 0 < float(chance) <= 1:
        raise ValueError(f"{BLUEPRINT_ACQUISITION_MANIFEST}: world_cache.chance must be in (0, 1]")
    if not isinstance(tables, list) or not tables or not all(isinstance(value, str) and value for value in tables):
        raise ValueError(f"{BLUEPRINT_ACQUISITION_MANIFEST}: world_cache.loot_tables must be non-empty ids")
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
        platform = f"default_{slug}"
        platforms.append({
            "slug": slug,
            "platform": platform,
            "gun_id": result["id"],
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


def generated_platform_files(platform: dict[str, Any]) -> dict[Path, Any]:
    slug = platform["slug"]
    name = platform["platform"]
    gun_id = platform["gun_id"]
    blueprint = platform["blueprint"]
    parts = platform["parts"]
    materials = platform["materials"]
    blueprint_key = blueprint["display_name"]
    result: dict[Path, Any] = {}

    result[RESOURCE_ROOT / f"data/tacz/recipe/create/industry/blueprint_{name}.json"] = {
        "fabric:load_conditions": CREATE_CONDITIONS,
        "type": "create:compacting",
        "ingredients": blueprint["ingredients"],
        "results": [output("tacz:gun_blueprint", {
            "IndustryPlatform": name,
            "IndustryPartKind": "blueprint",
            "IndustryDisplayName": blueprint_key,
        })],
    }

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
        held_blueprint = partial("tacz:gun_blueprint", {
            "IndustryPlatform": name,
            "IndustryPartKind": "blueprint",
            "IndustryDisplayName": blueprint_key,
        })
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

    result[RESOURCE_ROOT / f"data/tacz/industry/assembly/gun/{slug}.json"] = {
        "platform": name,
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
        "ingredient": partial("tacz:gun_blueprint", {
            "IndustryPlatform": name,
            "IndustryPartKind": "blueprint",
            "IndustryDisplayName": blueprint_key,
        }),
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
    # Furniture/polymer/external materials are supplied by their own Deployer
    # stations, then one final press performs the retained final fit.
    for material in materials:
        for _ in range(material["count"]):
            sequence.append({
                "type": "create:deploying",
                "target": "$ingredient",
                "ingredient": material["item"],
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
        }),
        "sequence": sequence,
    }
    return result


def blueprint_custom_data(platform: dict[str, Any]) -> dict[str, Any]:
    return {
        "IndustryPlatform": platform["platform"],
        "IndustryPartKind": "blueprint",
        "IndustryDisplayName": platform["blueprint"]["display_name"],
    }


def snbt_compound(values: dict[str, Any]) -> str:
    """Small deterministic SNBT writer for blueprint custom-data strings."""
    parts: list[str] = []
    for key, value in values.items():
        if not isinstance(value, str):
            raise ValueError(f"SNBT blueprint custom data expects strings, got {key}")
        parts.append(f"{key}:{json.dumps(value, ensure_ascii=False)}")
    return "{" + ",".join(parts) + "}"


def generated_blueprint_acquisition_files(platforms: list[dict[str, Any]], acquisition: dict[str, Any]) -> dict[Path, Any]:
    """Generate chest-cache and new data-driven weaponsmith blueprint routes."""
    files: dict[Path, Any] = {}
    trade = acquisition["weaponsmith"]
    cache = acquisition["world_cache"]
    trade_ids: list[str] = []
    loot_entries: list[dict[str, Any]] = []
    for platform in platforms:
        name = platform["platform"]
        custom = blueprint_custom_data(platform)
        material_weight = sum(material["count"] for material in platform["materials"])
        emerald_cost = min(64, trade["emerald_base"] + material_weight * trade["emerald_per_material"])
        trade_id = f"tacz:weaponsmith/5/blueprint_{name}"
        trade_ids.append(trade_id)
        files[RESOURCE_ROOT / f"data/tacz/villager_trade/weaponsmith/5/blueprint_{name}.json"] = {
            "wants": {"id": "minecraft:emerald", "count": float(emerald_cost)},
            "additional_wants": {"id": "minecraft:book"},
            "gives": output("tacz:gun_blueprint", custom),
            "max_uses": float(trade["max_uses"]),
            "reputation_discount": 0.05,
            "xp": float(trade["xp"]),
        }
        loot_entries.append({
            "type": "minecraft:item",
            "name": "tacz:gun_blueprint",
            "functions": [{
                # The TACZ loot-injection compatibility layer maps this old
                # spelling to 26.2 set_custom_data before direct-codec parse.
                "function": "minecraft:set_nbt",
                "tag": snbt_compound(custom),
            }],
        })

    # 26.2 selects a bounded random subset from this tag using vanilla's
    # data-driven weaponsmith level-5 trade_set. Appending is deliberate: it
    # preserves all vanilla master trades instead of replacing a profession.
    files[RESOURCE_ROOT / "data/minecraft/tags/villager_trade/weaponsmith/level_5.json"] = {
        "replace": False,
        "values": trade_ids,
    }
    files[RESOURCE_ROOT / "data/tacz/tacz_loot_injectors/industrial_blueprint_cache.json"] = {
        "loot_tables": cache["loot_tables"],
        "pools": [{
            "rolls": 1,
            "conditions": [{"condition": "minecraft:random_chance", "chance": cache["chance"]}],
            "entries": loot_entries,
        }],
    }
    return files


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
    projectile_sequence: list[dict[str, Any]] = []
    # Projectile mass follows the ballistic tier too. The initial blank is the
    # sole moving workpiece; extra blanks are inserted one at a time.
    for _ in range(projectile_blank_count - 1):
        projectile_sequence.append({
            "type": "create:deploying",
            "target": "$ingredient",
            "ingredient": projectile_stock,
            "results": ["$result"],
        })
    for payload in payloads:
        for _ in range(payload["count"]):
            projectile_sequence.append({
                "type": "create:deploying",
                "target": "$ingredient",
                "ingredient": payload["item"],
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
    if caliber.get("projectile_payloads") or caliber["projectile_blank_count"] > 1:
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
    entries[f"item.tacz.gun_component.incomplete_{platform['slug']}"] = platform["incomplete"]["name_zh" if chinese else "name_en"]
    return entries


def parse_hex(value: str) -> tuple[int, int, int, int]:
    value = value.lstrip("#")
    if len(value) != 6:
        raise ValueError(f"Invalid colour #{value}")
    return tuple(int(value[index:index + 2], 16) for index in range(0, 6, 2)) + (255,)


def png_rgba(width: int, height: int, pixels: list[tuple[int, int, int, int]]) -> bytes:
    raw = bytearray()
    for y in range(height):
        raw.append(0)
        for x in range(width):
            raw.extend(pixels[y * width + x])
    def chunk(kind: bytes, data: bytes) -> bytes:
        return struct.pack(">I", len(data)) + kind + data + struct.pack(">I", zlib.crc32(kind + data) & 0xFFFFFFFF)
    return b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", width, height, 8, 6, 0, 0, 0)) + chunk(b"IDAT", zlib.compress(bytes(raw), 9)) + chunk(b"IEND", b"")


def machine_texture(machine: dict[str, Any]) -> bytes:
    palette = {key: parse_hex(value) for key, value in machine["palette"].items()}
    base = palette["base"]
    pixels = [base] * (16 * 16)
    def paint(x0: int, y0: int, x1: int, y1: int, colour: tuple[int, int, int, int]) -> None:
        for y in range(max(0, y0), min(16, y1)):
            for x in range(max(0, x0), min(16, x1)):
                pixels[y * 16 + x] = colour
    paint(0, 0, 16, 1, palette["shadow"])
    paint(0, 15, 16, 16, palette["shadow"])
    paint(0, 0, 1, 16, palette["shadow"])
    paint(15, 0, 16, 16, palette["shadow"])
    paint(2, 2, 14, 3, palette["brass"])
    paint(2, 13, 14, 14, palette["brass"])
    for x, y in ((3, 5), (8, 5), (3, 9), (8, 9)):
        paint(x, y, x + 3, y + 3, palette["slot"])
    paint(12, 7, 14, 10, palette["indicator"])
    return png_rgba(16, 16, pixels)


def generated_machine_files() -> dict[Path, bytes | Any]:
    manifest = read_json(MACHINE_MANIFEST)
    result: dict[Path, bytes | Any] = {}
    for machine in manifest.get("machines", []):
        machine_id = machine["id"]
        texture = RESOURCE_ROOT / f"assets/tacz/textures/block/{machine_id}.png"
        model = RESOURCE_ROOT / f"assets/tacz/models/block/{machine_id}.json"
        result[texture] = machine_texture(machine)
        result[model] = {
            "parent": "minecraft:block/cube_all",
            "textures": {
                "all": f"tacz:block/{machine_id}",
                "particle": f"tacz:block/{machine_id}",
            },
        }
    return result


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
    blueprint_acquisition = load_blueprint_acquisition()
    expected: dict[Path, Any] = {}
    english: dict[str, str] = {}
    chinese: dict[str, str] = {}
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
    expected.update(generated_machine_files())

    stale: list[str] = []
    for path, value in sorted(expected.items(), key=lambda pair: str(pair[0])):
        if isinstance(value, bytes):
            matches = path.exists() and path.read_bytes() == value
        else:
            matches = existing_json_matches(path, value)
        if not matches:
            stale.append(str(path.relative_to(REPO)))
            if write:
                if isinstance(value, bytes):
                    path.parent.mkdir(parents=True, exist_ok=True)
                    path.write_bytes(value)
                else:
                    write_json(path, value)

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
