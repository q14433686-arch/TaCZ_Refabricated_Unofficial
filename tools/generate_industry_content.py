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
MAGAZINE_CARRIER_MANIFEST = REPO / "tools/industry/magazine_carriers.json"
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
DEFAULT_GUN_DISPLAY_ROOT = RESOURCE_ROOT / "assets/tacz/custom/tacz_default_gun/assets/tacz/display/guns"
DEFAULT_GUN_ANIMATION_ROOT = RESOURCE_ROOT / "assets/tacz/custom/tacz_default_gun/assets/tacz/animations"

CREATE_CONDITIONS = [{"condition": "fabric:all_mods_loaded", "values": ["create"]}]
# Neutral stock classes are deliberately separate from the real action-part
# names. A break-action hinge can be machined from the same neutral bolt stock,
# but it must not be reported to the player as a fictitious "bolt group".
BLANK_CLASS_ORDER = ("receiver", "bolt", "barrel", "trigger", "recoil")
TOOLING_SCOPE_IDS = ("family_jig", "critical_gauge", "platform_tooling", "final_acceptance")
BLUEPRINT_ROLE_IDS = ("master", "production", "blank")
# Create Fly 26.2's native JEI SequencedAssemblyCategory has exactly seven
# stage labels/cells. Longer sequences crash its renderer, so each terminal
# line must fit its actual visible machine-stage contract.
MAX_SEQUENCED_ASSEMBLY_STEPS = 7
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


def _require_item_id_list(value: Any, source: Path, key: str, minimum: int = 1, maximum: int = 9) -> list[str]:
    if not isinstance(value, list) or not minimum <= len(value) <= maximum \
            or not all(isinstance(item, str) and item for item in value):
        raise ValueError(f"{source}: {key} must be a list of {minimum}..{maximum} non-empty item ids")
    return list(value)


def _validate_mechanical_layout(layout: Any, source: Path, profile_id: str) -> None:
    if not isinstance(layout, dict):
        raise ValueError(f"{source}: process_profiles.{profile_id}.jig must be an object")
    key = layout.get("key")
    pattern = layout.get("pattern")
    if not isinstance(key, dict) or not key or not all(isinstance(symbol, str) and len(symbol) == 1
                                                       and isinstance(item, str) and item
                                                       for symbol, item in key.items()):
        raise ValueError(f"{source}: process_profiles.{profile_id}.jig.key must map one-character symbols to item ids")
    if not isinstance(pattern, list) or not 1 <= len(pattern) <= 3 \
            or not all(isinstance(row, str) and 1 <= len(row) <= 3 for row in pattern):
        raise ValueError(f"{source}: process_profiles.{profile_id}.jig.pattern must be a non-empty <=3x3 grid")
    symbols = {symbol for row in pattern for symbol in row if symbol != " "}
    if not symbols or not symbols <= set(key):
        raise ValueError(f"{source}: process_profiles.{profile_id}.jig.pattern references undefined symbols")
    if len(symbols) < 2:
        raise ValueError(f"{source}: process_profiles.{profile_id}.jig must use at least two real materials")


def load_default_gun_policy() -> dict[str, Any]:
    """Load manufacturing/action-family policy used for default-pack discovery.

    The policy intentionally contains action profiles and real mechanical-crafter
    jig layouts. It does *not* contain arbitrary per-platform "seed" items:
    a platform identity now comes from a dossier or measured sample firearm.
    """
    policy = read_json(DEFAULT_GUN_POLICY)
    if not isinstance(policy, dict):
        raise ValueError(f"{DEFAULT_GUN_POLICY}: policy must be an object")
    _require_item_id_list(policy.get("template_blank_ingredients"), DEFAULT_GUN_POLICY,
                          "template_blank_ingredients", minimum=2)
    profiles = policy.get("process_profiles")
    if not isinstance(profiles, dict) or not profiles:
        raise ValueError(f"{DEFAULT_GUN_POLICY}: process_profiles must be a non-empty object")
    layouts: set[str] = set()
    for profile_id, profile in profiles.items():
        if not isinstance(profile_id, str) or not profile_id or not isinstance(profile, dict):
            raise ValueError(f"{DEFAULT_GUN_POLICY}: invalid process profile")
        for key in ("label_en", "label_zh", "default_tooling_scope"):
            require_string(profile, key, DEFAULT_GUN_POLICY)
        if profile["default_tooling_scope"] not in TOOLING_SCOPE_IDS:
            raise ValueError(f"{DEFAULT_GUN_POLICY}: {profile_id} has unknown default_tooling_scope")
        parts = profile.get("parts")
        if not isinstance(parts, list) or len(parts) != len(BLANK_CLASS_ORDER):
            raise ValueError(f"{DEFAULT_GUN_POLICY}: {profile_id}.parts must cover exactly {BLANK_CLASS_ORDER}")
        blank_classes: list[str] = []
        structural_ids: set[str] = set()
        for part in parts:
            if not isinstance(part, dict):
                raise ValueError(f"{DEFAULT_GUN_POLICY}: {profile_id}.parts entries must be objects")
            for key in ("structural", "blank_class", "kind", "name_en", "name_zh"):
                require_string(part, key, DEFAULT_GUN_POLICY)
            blank_class = part["blank_class"]
            if blank_class not in BLANK_CLASS_ORDER:
                raise ValueError(f"{DEFAULT_GUN_POLICY}: {profile_id} has unknown blank_class {blank_class}")
            if part["structural"] in structural_ids:
                raise ValueError(f"{DEFAULT_GUN_POLICY}: {profile_id} repeats structural identity {part['structural']}")
            structural_ids.add(part["structural"])
            blank_classes.append(blank_class)
        if tuple(blank_classes) != BLANK_CLASS_ORDER:
            raise ValueError(f"{DEFAULT_GUN_POLICY}: {profile_id}.parts must follow blank classes {BLANK_CLASS_ORDER}")
        _validate_mechanical_layout(profile.get("jig"), DEFAULT_GUN_POLICY, profile_id)
        jig = profile["jig"]
        signature = canonical({"key": jig["key"], "pattern": jig["pattern"]})
        if signature in layouts:
            raise ValueError(f"{DEFAULT_GUN_POLICY}: action jig recipe collision for {profile_id}")
        layouts.add(signature)

    by_type = policy.get("action_profile_by_gun_type")
    overrides = policy.get("action_profile_overrides")
    scope_overrides = policy.get("tooling_scope_overrides")
    tier_by_type = policy.get("tier_by_gun_type")
    tier_overrides = policy.get("tier_overrides")
    if not all(isinstance(value, dict) for value in (by_type, overrides, scope_overrides, tier_by_type, tier_overrides)):
        raise ValueError(f"{DEFAULT_GUN_POLICY}: action/tier mappings must be objects")
    if "default" not in by_type or "default" not in tier_by_type:
        raise ValueError(f"{DEFAULT_GUN_POLICY}: action_profile_by_gun_type and tier_by_gun_type need default entries")
    if any(value not in profiles for value in [*by_type.values(), *overrides.values()]):
        raise ValueError(f"{DEFAULT_GUN_POLICY}: action profile mapping references an unknown profile")
    if any(value not in TOOLING_SCOPE_IDS for value in scope_overrides.values()):
        raise ValueError(f"{DEFAULT_GUN_POLICY}: tooling_scope_overrides references an unknown scope")
    if any(value not in MANUFACTURING_TIER_IDS for value in [*tier_by_type.values(), *tier_overrides.values()]):
        raise ValueError(f"{DEFAULT_GUN_POLICY}: tier mapping references an unknown manufacturing tier")
    if not isinstance(policy.get("materials_by_gun_type"), dict):
        raise ValueError(f"{DEFAULT_GUN_POLICY}: materials_by_gun_type must be an object")
    clear_actions = policy.get("audited_feed_jam_clear_actions", {})
    if not isinstance(clear_actions, dict):
        raise ValueError(f"{DEFAULT_GUN_POLICY}: audited_feed_jam_clear_actions must be an object")
    for slug, action in clear_actions.items():
        if not isinstance(slug, str) or not re.fullmatch(r"[a-z0-9_]+", slug) or action != "bolt":
            raise ValueError(
                f"{DEFAULT_GUN_POLICY}: each audited feed-jam clear action must be a [a-z0-9_]+ slug mapped to 'bolt'"
            )
    return policy


def load_platforms(policy: dict[str, Any]) -> list[dict[str, Any]]:
    platforms: list[dict[str, Any]] = []
    seen: dict[str, set[str]] = {"slug": set(), "platform": set(), "gun_id": set()}
    profiles = policy["process_profiles"]
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
        action = require_string(data, "action_profile", path)
        scope = require_string(data, "tooling_scope", path)
        if action not in profiles:
            raise ValueError(f"{path}: unknown action_profile '{action}'")
        if scope not in TOOLING_SCOPE_IDS:
            raise ValueError(f"{path}: unknown tooling_scope '{scope}'")

        blueprint = data.get("blueprint")
        if not isinstance(blueprint, dict):
            raise ValueError(f"{path}: missing blueprint object")
        for key in ("display_name", "legacy_display_name", "name_en", "name_zh"):
            require_string(blueprint, key, path)
        if blueprint["display_name"] == blueprint["legacy_display_name"]:
            raise ValueError(f"{path}: production and legacy template display names must differ")

        parts = data.get("parts")
        if not isinstance(parts, list) or len(parts) != len(BLANK_CLASS_ORDER):
            raise ValueError(f"{path}: exactly five action parts are required")
        blank_classes: list[str] = []
        structural_ids: set[str] = set()
        for part in parts:
            if not isinstance(part, dict):
                raise ValueError(f"{path}: part must be object")
            for key in ("structural", "blank_class", "kind", "name_en", "name_zh"):
                require_string(part, key, path)
            if part["structural"] in structural_ids:
                raise ValueError(f"{path}: duplicate action structural identity '{part['structural']}'")
            structural_ids.add(part["structural"])
            blank_classes.append(part["blank_class"])
        if tuple(blank_classes) != BLANK_CLASS_ORDER:
            raise ValueError(f"{path}: parts must use blank classes in order {BLANK_CLASS_ORDER}")

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
    """Validate tiered master-dossier access rather than one flat trade pool.

    Legacy documents are early weaponsmith/world sources; service dossiers are
    licensed stock; advanced and precision dossiers are expedition finds. A
    dossier or actual sample gun supplies the platform identity, then a player
    transfers it to a physical production template—never a random recipe seed.
    """
    data = read_json(BLUEPRINT_ACQUISITION_MANIFEST)
    if not isinstance(data, dict) or not isinstance(data.get("tiers"), dict):
        raise ValueError(f"{BLUEPRINT_ACQUISITION_MANIFEST}: expected tiers object")
    tiers = data["tiers"]
    if set(tiers) != set(MANUFACTURING_TIER_IDS):
        raise ValueError(f"{BLUEPRINT_ACQUISITION_MANIFEST}: tiers must be exactly {MANUFACTURING_TIER_IDS}")
    ranks: set[int] = set()
    archive_kinds: set[str] = set()
    archive_recipe_signatures: set[str] = set()
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

        archive = tier.get("stable_archive")
        if not isinstance(archive, dict):
            raise ValueError(f"{BLUEPRINT_ACQUISITION_MANIFEST}: {tier_id}.stable_archive must be an object")
        for key in ("kind", "name_en", "name_zh"):
            require_string(archive, key, BLUEPRINT_ACQUISITION_MANIFEST)
        if archive["kind"] in archive_kinds:
            raise ValueError(f"{BLUEPRINT_ACQUISITION_MANIFEST}: duplicate stable archive kind {archive['kind']}")
        archive_kinds.add(archive["kind"])
        archive_recipe = archive.get("recipe")
        if not isinstance(archive_recipe, dict):
            raise ValueError(f"{BLUEPRINT_ACQUISITION_MANIFEST}: {tier_id}.stable_archive.recipe must be an object")
        archive_type = archive_recipe.get("type")
        if archive_type == "create:compacting":
            _require_item_id_list(archive_recipe.get("ingredients"), BLUEPRINT_ACQUISITION_MANIFEST,
                                  f"{tier_id}.stable_archive.recipe.ingredients", minimum=2)
            signature = canonical((archive_type, sorted(archive_recipe["ingredients"])))
        elif archive_type == "create:mechanical_crafting":
            _validate_mechanical_layout(archive_recipe, BLUEPRINT_ACQUISITION_MANIFEST, f"stable_archive.{tier_id}")
            signature = canonical((archive_type, archive_recipe["key"], archive_recipe["pattern"]))
        else:
            raise ValueError(f"{BLUEPRINT_ACQUISITION_MANIFEST}: {tier_id}.stable_archive.recipe has unsupported type")
        if signature in archive_recipe_signatures:
            raise ValueError(f"{BLUEPRINT_ACQUISITION_MANIFEST}: stable archive recipe collision for {tier_id}")
        archive_recipe_signatures.add(signature)
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


def action_profile_for(policy: dict[str, Any], slug: str, gun_type: str) -> str:
    return policy["action_profile_overrides"].get(
        slug, policy["action_profile_by_gun_type"].get(gun_type, policy["action_profile_by_gun_type"]["default"])
    )


def tooling_scope_for(policy: dict[str, Any], slug: str, tier: str, action_profile: str) -> str:
    override = policy["tooling_scope_overrides"].get(slug)
    if isinstance(override, str):
        return override
    scope = policy["process_profiles"][action_profile]["default_tooling_scope"]
    # Advanced and precision systems must pass an acceptance station even where
    # their basic action family would otherwise stop at production tooling.
    if tier in {"advanced", "precision"} and scope != "family_jig":
        return "final_acceptance"
    return scope


def profile_parts(policy: dict[str, Any], action_profile: str, gun_label: str) -> list[dict[str, str]]:
    result: list[dict[str, str]] = []
    for part in policy["process_profiles"][action_profile]["parts"]:
        result.append({
            "structural": part["structural"],
            "blank_class": part["blank_class"],
            "kind": part["kind"],
            "name_en": part["name_en"].replace("{gun}", gun_label),
            "name_zh": part["name_zh"].replace("{gun}", gun_label),
        })
    return result


def discover_default_platforms(explicit_slugs: set[str], policy: dict[str, Any]) -> list[dict[str, Any]]:
    """Create data-driven industrial platforms for every remaining bundled gun.

    The player never runs this authoring helper. Generated production templates
    are copied from a world dossier or measured sample firearm; there is no
    unrelated fruit/mineral "seed" that magically names a weapon platform.
    """
    recipe_root = RESOURCE_ROOT / "assets/tacz/custom/tacz_default_gun/data/tacz/recipe/gun"
    index_root = RESOURCE_ROOT / "assets/tacz/custom/tacz_default_gun/data/tacz/index/guns"
    data_root = RESOURCE_ROOT / "assets/tacz/custom/tacz_default_gun/data/tacz/data/guns"
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

    platforms: list[dict[str, Any]] = []
    for slug, recipe, index in missing:
        result = recipe["result"]
        gun_type = index.get("type") if isinstance(index.get("type"), str) else "default"
        data_id = index.get("data") if isinstance(index.get("data"), str) else f"tacz:{slug}_data"
        data_path = data_root / f"{data_id.split(':', 1)[-1]}.json"
        display = humanize_slug(slug)
        tier = policy["tier_overrides"].get(
            slug, policy["tier_by_gun_type"].get(gun_type, policy["tier_by_gun_type"]["default"])
        )
        action_profile = action_profile_for(policy, slug, gun_type)
        scope = tooling_scope_for(policy, slug, tier, action_profile)
        platform = f"default_{slug}"
        materials = policy["materials_by_gun_type"].get(gun_type, policy["materials_by_gun_type"]["default"])
        platforms.append({
            "slug": slug,
            "platform": platform,
            "gun_id": result["id"],
            "manufacturing_tier": tier,
            "action_profile": action_profile,
            "tooling_scope": scope,
            "fire_mode": first_fire_mode(data_path),
            "blueprint": {
                "display_name": f"item.tacz.gun_template.{platform}",
                "legacy_display_name": f"item.tacz.gun_blueprint.{platform}",
                "name_en": f"{display} Platform Tooling Template",
                "name_zh": f"{display} 平台工装模板",
            },
            "parts": profile_parts(policy, action_profile, display),
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
        "gauge_name_en", "gauge_name_zh", "case_gauge_name_en", "case_gauge_name_zh",
        "projectile_gauge_name_en", "projectile_gauge_name_zh",
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

        motor_name_keys = ("motor_housing_name_en", "motor_housing_name_zh")
        if any(key in entry for key in motor_name_keys) and not all(
                isinstance(entry.get(key), str) and entry.get(key) for key in motor_name_keys
        ):
            raise ValueError(f"{CARTRIDGE_MANIFEST}: {entry['id']} motor housing names must be declared as an en/zh pair")

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


def load_magazine_carriers(cartridge_ammo_ids: set[str]) -> list[dict[str, Any]]:
    """Load every removable carrier as an explicit physical specification.

    ``gun_feed`` owns the runtime compatibility contract; this manifest owns
    manufacturing mass, feed complexity and human-readable tooling names.  The
    two sources are checked bidirectionally so a new default detachable feed
    can never silently keep the former "blank + finished gun" shortcut.
    Internal/tube/revolver/single-shot feeds are deliberately excluded: they
    are gun-integrated assemblies, not removable ``tacz:magazine`` stacks.
    """
    manifest = read_json(MAGAZINE_CARRIER_MANIFEST)
    if not isinstance(manifest, dict) or manifest.get("schema_version") != 1:
        raise ValueError(f"{MAGAZINE_CARRIER_MANIFEST}: expected schema_version 1 object")
    profiles = manifest.get("profiles")
    carriers = manifest.get("carriers")
    if not isinstance(profiles, dict) or not profiles:
        raise ValueError(f"{MAGAZINE_CARRIER_MANIFEST}: profiles must be a non-empty object")
    if not isinstance(carriers, list) or not carriers:
        raise ValueError(f"{MAGAZINE_CARRIER_MANIFEST}: carriers must be a non-empty list")

    for profile_id, profile in profiles.items():
        if not isinstance(profile_id, str) or not re.fullmatch(r"[a-z0-9_]+", profile_id) \
                or not isinstance(profile, dict):
            raise ValueError(f"{MAGAZINE_CARRIER_MANIFEST}: invalid carrier profile {profile_id!r}")
        for key in ("body_blank_count", "feed_kit_blank_count"):
            count = profile.get(key)
            if not isinstance(count, int) or not 1 <= count <= MAX_SEQUENCED_ASSEMBLY_STEPS:
                raise ValueError(
                    f"{MAGAZINE_CARRIER_MANIFEST}: {profile_id}.{key} must fit Create Fly's "
                    f"1..{MAX_SEQUENCED_ASSEMBLY_STEPS}-stage viewer limit"
                )
        for key in ("feed_name_en", "feed_name_zh"):
            require_string(profile, key, MAGAZINE_CARRIER_MANIFEST)

    feed_root = RESOURCE_ROOT / "data/tacz/industry/gun_feed"
    feeds_by_identity: dict[tuple[str, str, int], list[tuple[str, dict[str, Any]]]] = defaultdict(list)
    for path in sorted(feed_root.glob("*.json")):
        feed = read_json(path)
        mechanism = feed.get("mechanism")
        if mechanism not in {"detachable_magazine", "belt"}:
            continue
        family = feed.get("magazine_family")
        ammo = feed.get("ammo")
        capacity = feed.get("magazine_capacity")
        display_name = feed.get("display_name")
        if not isinstance(family, str) or not family or not isinstance(ammo, str) or ammo not in cartridge_ammo_ids \
                or not isinstance(capacity, int) or not 1 <= capacity <= 512 \
                or not isinstance(display_name, str) or not display_name:
            raise ValueError(f"{path}: invalid removable carrier declaration")
        feeds_by_identity[(family, ammo, capacity)].append((path.stem, feed))

    seen_ids: set[str] = set()
    seen_identities: set[tuple[str, str, int]] = set()
    result: list[dict[str, Any]] = []
    for carrier in carriers:
        if not isinstance(carrier, dict):
            raise ValueError(f"{MAGAZINE_CARRIER_MANIFEST}: carrier entries must be objects")
        for key in ("id", "family", "ammo", "mechanism", "profile", "name_en", "name_zh"):
            require_string(carrier, key, MAGAZINE_CARRIER_MANIFEST)
        carrier_id = carrier["id"]
        if not re.fullmatch(r"[a-z0-9_]+", carrier_id) or carrier_id in seen_ids:
            raise ValueError(f"{MAGAZINE_CARRIER_MANIFEST}: carrier id must be unique [a-z0-9_]+: {carrier_id!r}")
        seen_ids.add(carrier_id)
        capacity = carrier.get("capacity")
        if not isinstance(capacity, int) or not 1 <= capacity <= 512:
            raise ValueError(f"{MAGAZINE_CARRIER_MANIFEST}: {carrier_id}.capacity must be in [1, 512]")
        if carrier["ammo"] not in cartridge_ammo_ids:
            raise ValueError(f"{MAGAZINE_CARRIER_MANIFEST}: {carrier_id} references undeclared cartridge {carrier['ammo']}")
        if carrier["mechanism"] not in {"detachable_magazine", "belt"}:
            raise ValueError(f"{MAGAZINE_CARRIER_MANIFEST}: {carrier_id}.mechanism must be detachable_magazine or belt")
        profile_id = carrier["profile"]
        if profile_id not in profiles:
            raise ValueError(f"{MAGAZINE_CARRIER_MANIFEST}: {carrier_id} references unknown profile {profile_id}")
        source_guns = carrier.get("source_guns")
        if not isinstance(source_guns, list) or not source_guns \
                or not all(isinstance(value, str) and re.fullmatch(r"[a-z0-9_]+", value) for value in source_guns) \
                or len(set(source_guns)) != len(source_guns):
            raise ValueError(f"{MAGAZINE_CARRIER_MANIFEST}: {carrier_id}.source_guns must be unique gun-feed file stems")

        identity = (carrier["family"], carrier["ammo"], capacity)
        if identity in seen_identities:
            raise ValueError(f"{MAGAZINE_CARRIER_MANIFEST}: duplicate physical carrier identity {identity}")
        seen_identities.add(identity)
        feeds = feeds_by_identity.get(identity)
        if not feeds:
            raise ValueError(f"{MAGAZINE_CARRIER_MANIFEST}: {carrier_id} has no matching gun_feed identity {identity}")
        mechanisms = {feed["mechanism"] for _, feed in feeds}
        display_names = {feed["display_name"] for _, feed in feeds}
        actual_sources = {source for source, _ in feeds}
        if mechanisms != {carrier["mechanism"]}:
            raise ValueError(f"{MAGAZINE_CARRIER_MANIFEST}: {carrier_id} mechanism disagrees with gun_feed")
        if actual_sources != set(source_guns):
            raise ValueError(
                f"{MAGAZINE_CARRIER_MANIFEST}: {carrier_id}.source_guns must exactly match gun_feed "
                f"({sorted(actual_sources)} != {sorted(source_guns)})"
            )
        if len(display_names) != 1:
            raise ValueError(f"{MAGAZINE_CARRIER_MANIFEST}: {carrier_id} sources disagree on MagazineDisplayName")
        result.append({
            **carrier,
            "_profile": profiles[profile_id],
            "_display_name": next(iter(display_names)),
        })

    missing = set(feeds_by_identity) - seen_identities
    if missing:
        raise ValueError(
            f"{MAGAZINE_CARRIER_MANIFEST}: missing removable gun_feed identity declarations: {sorted(missing)}"
        )
    return sorted(result, key=lambda value: value["id"])


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
    suffixes = (" 工业工装模板", " 平台工装模板", " 工业装配模板", " 平台装配模板") if language == "zh_cn" else (
        " Industrial Tooling Template", " Platform Tooling Template", " Industrial Blueprint", " Platform Assembly Blueprint"
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


def action_profile(platform: dict[str, Any]) -> str:
    profile = platform.get("action_profile")
    if not isinstance(profile, str) or not profile:
        raise ValueError(f"{platform.get('slug', '?')}: missing action_profile")
    return profile


def tooling_scope(platform: dict[str, Any]) -> str:
    scope = platform.get("tooling_scope")
    if scope not in TOOLING_SCOPE_IDS:
        raise ValueError(f"{platform.get('slug', '?')}: missing valid tooling_scope")
    return scope


def template_blank_tag() -> dict[str, Any]:
    return {
        "IndustryPlatform": "tooling",
        "IndustryPartKind": "template_blank",
        "IndustryDisplayName": "item.tacz.gun_blueprint.blank",
        "IndustryBlueprintRole": "blank",
    }



def survey_archive_tag() -> dict[str, Any]:
    """Neutral archive packet consumed by a runtime-generated surveyed dossier commission."""
    return {
        "IndustryPlatform": "surveying",
        "IndustryPartKind": "survey_archive",
        "IndustryDisplayName": "item.tacz.survey_archive",
    }


def survey_fixture_tag() -> dict[str, Any]:
    """Reusable neutral fixture; it proves tooling setup without claiming a gun geometry."""
    return {
        "IndustryPlatform": "surveying",
        "IndustryPartKind": "survey_fixture",
        "IndustryDisplayName": "item.tacz.press_die.survey_fixture",
        "DieTargetKind": "surveyed",
    }


def generated_surveying_files() -> dict[Path, Any]:
    """Create sources for the runtime surveyed-platform fallback path.

    The third-party dossier/template/kit recipes are synthesized in the
    Gunsmith Table at reload because their exact GunId does not exist until the
    server reads installed packs. These two neutral industrial inputs remain
    ordinary static Create recipes: true Basin / Mechanical Crafter operations,
    not fake multi-item Depot steps.
    """
    return {
        RESOURCE_ROOT / "data/tacz/recipe/create/industry/form_survey_archive.json": {
            "fabric:load_conditions": CREATE_CONDITIONS,
            "type": "create:compacting",
            "ingredients": [
                "minecraft:paper",
                "minecraft:paper",
                "tacz:high_carbon_steel_plate",
                "create:brass_sheet",
                "minecraft:redstone",
            ],
            "results": [output("tacz:gun_component_blank", survey_archive_tag())],
        },
        RESOURCE_ROOT / "data/tacz/recipe/create/industry/form_survey_fixture.json": {
            "fabric:load_conditions": CREATE_CONDITIONS,
            "type": "create:mechanical_crafting",
            "key": {
                "S": "tacz:high_carbon_steel_plate",
                "B": "create:brass_sheet",
                "Q": "minecraft:quartz",
                "R": "minecraft:redstone",
            },
            "pattern": ["SBS", "QR ", "SBS"],
            "result": output("tacz:press_die", survey_fixture_tag()),
        },
    }


def surveying_language_entries(language: str) -> dict[str, str]:
    chinese = language == "zh_cn"
    return {
        "item.tacz.survey_archive": "测绘档案包" if chinese else "Survey Archive Packet",
        "item.tacz.press_die.survey_fixture": "测绘基准夹具" if chinese else "Survey Reference Fixture",
        "item.tacz.gun_dossier.surveyed": "测绘平台原始档案" if chinese else "Surveyed Platform Master Dossier",
        "item.tacz.gun_template.surveyed": "测绘平台生产工装" if chinese else "Surveyed Platform Production Template",
        "item.tacz.gun_component.surveyed_platform_kit": "测绘平台结构套件" if chinese else "Surveyed Platform Structural Kit",
        "item.tacz.press_die.survey_cartridge_gauge": "测绘弹药基准量规" if chinese else "Surveyed Cartridge Datum Gauge",
        "item.tacz.cartridge_case.surveyed": "测绘口径弹壳" if chinese else "Surveyed-Calibre Cartridge Case",
        "item.tacz.cartridge_case.spent_surveyed": "已击发的测绘口径弹壳" if chinese else "Fired Surveyed-Calibre Cartridge Case",
        "item.tacz.projectile_core.surveyed":  "测绘口径弹头" if chinese else "Surveyed-Calibre Projectile Core",
        "tooltip.tacz.industry.survey_archive":  "在枪械工作台中消耗，以委托一份已审计枪械的测绘档案" if chinese else "Consumed by a Gunsmith Table commission for one audited surveyed dossier",
        "tooltip.tacz.industry.survey_fixture": "测绘平台操作的可复用基准夹具（不消耗）" if chinese else "Reusable reference fixture for surveyed-platform operations (not consumed)",
        "tooltip.tacz.industry.survey_cartridge_gauge": "由原弹药材料表测绘；成型对应测绘弹壳和弹头（不消耗）" if chinese else "Surveyed from the original ammo material bill; forms its matching case and projectile (not consumed)",
        "tooltip.tacz.industry.surveyed_cartridge_part": "专用于对应测绘 AmmoId 的物理弹药部件" if chinese else "Physical cartridge part dedicated to its surveyed AmmoId",
        "tooltip.tacz.industry.surveyed_platform_kit":  "由五种中性结构毛坯和生产工装组成；用于对应测绘枪的平台终端" if chinese else "Built from five neutral structural blanks and a production template; required by its surveyed gun terminal",
        "tooltip.tacz.industry.surveyed_target": "测绘目标：%s" if chinese else "Surveyed target: %s",
        "tooltip.tacz.blueprint.tier.surveyed": "已审计测绘档案 — 未声明真实结构前使用通用工业线" if chinese else "Audited Survey Dossier — generic industrial line pending an explicit structure profile",
        "tooltip.tacz.industry.action_profile.surveyed": "测绘通用结构" if chinese else "Surveyed Generic Structure",
        "tooltip.tacz.industry.tooling_scope.surveyed": "测绘夹具和测绘平台结构套件" if chinese else "Survey fixture and surveyed platform structural kit",
        "tooltip.tacz.feed_device.kind.stripper_clip": "桥夹——将弹药转入枪内固定仓，不会替换已安装弹匣" if chinese else "Stripper clip — transfers rounds into an internal feed; never replaces an installed magazine",
        "tooltip.tacz.feed_device.kind.speedloader": "快装器——将弹药转入枪内转轮，不会替换已安装弹匣" if chinese else "Speedloader — transfers rounds into an internal cylinder; never replaces an installed magazine",
        "item.tacz.feed_device.rainforest_type56_stripper": "56式半自动步枪桥夹" if chinese else "Type 56 Stripper Clip",
    }


def production_blueprint_tag(platform: dict[str, Any]) -> dict[str, Any]:
    return {
        "IndustryPlatform": platform["platform"],
        "IndustryPartKind": "blueprint",
        "IndustryDisplayName": platform["blueprint"]["display_name"],
        "IndustryBlueprintTier": manufacturing_tier(platform),
        "IndustryBlueprintRole": "production",
        "IndustryActionProfile": action_profile(platform),
        "IndustryToolingScope": tooling_scope(platform),
    }


def master_blueprint_tag(platform: dict[str, Any]) -> dict[str, Any]:
    return {
        "IndustryPlatform": platform["platform"],
        "IndustryPartKind": "blueprint",
        "IndustryDisplayName": f"item.tacz.gun_dossier.{platform['platform']}",
        "IndustryBlueprintTier": manufacturing_tier(platform),
        "IndustryBlueprintRole": "master",
        "IndustryActionProfile": action_profile(platform),
        "IndustryToolingScope": tooling_scope(platform),
    }


def dossier_archive_tag(tier_id: str, acquisition: dict[str, Any]) -> dict[str, Any]:
    archive = acquisition["tiers"][tier_id]["stable_archive"]
    return {
        "IndustryPlatform": "archive",
        "IndustryPartKind": archive["kind"],
        "IndustryDisplayName": f"item.tacz.dossier_archive.{tier_id}",
        "IndustryBlueprintTier": tier_id,
    }


def dossier_commission_material(item: str, nbt: dict[str, Any], consume: bool) -> dict[str, Any]:
    return {
        "item": partial(item, nbt),
        "count": 1,
        "consume": consume,
    }


def generated_stable_dossier_files(platforms: list[dict[str, Any]], acquisition: dict[str, Any]) -> dict[Path, Any]:
    """Emit deterministic archive packets plus explicit Gunsmith dossier commissions.

    A commission is a real GUI-selected operation, not competing Basin/Depot
    recipes with identical physical inputs and arbitrary platform outputs. The
    archive packet is consumed, while the action fixture is verified and kept.
    """
    files: dict[Path, Any] = {}
    for tier_id in MANUFACTURING_TIER_IDS:
        archive = acquisition["tiers"][tier_id]["stable_archive"]
        archive_tag = dossier_archive_tag(tier_id, acquisition)
        archive_recipe = archive["recipe"]
        path = RESOURCE_ROOT / f"data/tacz/recipe/create/industry/form_dossier_archive_{tier_id}.json"
        if archive_recipe["type"] == "create:compacting":
            files[path] = {
                "fabric:load_conditions": CREATE_CONDITIONS,
                "type": "create:compacting",
                "ingredients": archive_recipe["ingredients"],
                "results": [output("tacz:gun_component_blank", archive_tag)],
            }
        else:
            files[path] = {
                "fabric:load_conditions": CREATE_CONDITIONS,
                "type": "create:mechanical_crafting",
                "key": archive_recipe["key"],
                "pattern": archive_recipe["pattern"],
                "result": output("tacz:gun_component_blank", archive_tag),
            }

    for platform in platforms:
        tier_id = manufacturing_tier(platform)
        files[RESOURCE_ROOT / f"data/tacz/recipe/industry/dossier_commission_{platform['slug']}.json"] = {
            "type": "tacz:gun_smith_table_crafting",
            # Existing default Gunsmith Table has a real misc tab. This is an
            # explicit commission catalogue selection, so multiple platforms
            # may legitimately share an archive/action evidence bill of goods
            # without becoming ambiguous physical machine recipes.
            "industry_dossier_commission": True,
            "materials": [
                dossier_commission_material("tacz:gun_component_blank", dossier_archive_tag(tier_id, acquisition), True),
                dossier_commission_material("tacz:gun_blueprint", template_blank_tag(), True),
                dossier_commission_material("tacz:press_die", action_jig_tag(action_profile(platform)), False),
            ],
            "result": {
                "type": "custom",
                "group": "tacz:misc",
                "item": {
                    "item": "tacz:gun_blueprint",
                    "count": 1,
                    "nbt": master_blueprint_tag(platform),
                },
            },
        }
    return files


def stable_dossier_language_entries(acquisition: dict[str, Any], language: str) -> dict[str, str]:
    chinese = language == "zh_cn"
    entries: dict[str, str] = {}
    for tier_id in MANUFACTURING_TIER_IDS:
        archive = acquisition["tiers"][tier_id]["stable_archive"]
        entries[f"item.tacz.dossier_archive.{tier_id}"] = archive["name_zh" if chinese else "name_en"]
    return entries


def legacy_blueprint_match_tag(platform: dict[str, Any]) -> dict[str, Any]:
    """Match pre-tooling-rework stacks without requiring new metadata.

    Old worlds only have the original display key plus platform/kind. A separate
    restoration route preserves those stacks while keeping a newly discovered
    master dossier distinct from a production template.
    """
    return {
        "IndustryPlatform": platform["platform"],
        "IndustryPartKind": "blueprint",
        "IndustryDisplayName": platform["blueprint"]["legacy_display_name"],
    }


def generic_component_die_stock() -> dict[str, Any]:
    return {
        "IndustryPlatform": "machining",
        "IndustryPartKind": "die_blank",
        "IndustryDisplayName": "item.tacz.press_die_blank.generic",
    }


def acceptance_gauge_stock_tag() -> dict[str, Any]:
    """A distinct precision stock prevents critical/acceptance gauge collisions."""
    return {
        "IndustryPlatform": "tooling",
        "IndustryPartKind": "acceptance_gauge_stock",
        "IndustryDisplayName": "item.tacz.press_die.acceptance_gauge_stock",
        "IndustryToolingScope": "final_acceptance",
    }


def action_jig_tag(profile: str) -> dict[str, Any]:
    return {
        "IndustryPlatform": "tooling",
        "IndustryPartKind": "action_jig",
        "IndustryDisplayName": f"item.tacz.press_die.action_jig.{profile}",
        "IndustryActionProfile": profile,
        "DieTargetKind": profile,
    }


def final_gauge_kind(scope: str) -> str:
    return {
        "critical_gauge": "critical_fit_gauge",
        "final_acceptance": "acceptance_gauge",
    }.get(scope, "")


def action_gauge_blank_tag(profile: str, scope: str) -> dict[str, Any]:
    kind = final_gauge_kind(scope)
    if not kind:
        raise ValueError(f"{scope}: no gauge blank kind")
    return {
        "IndustryPlatform": "tooling",
        "IndustryPartKind": f"{kind}_blank",
        "IndustryDisplayName": f"item.tacz.press_die.{kind}_blank.{profile}",
        "IndustryActionProfile": profile,
        "IndustryToolingScope": scope,
        "DieTargetKind": profile,
    }


def platform_gauge_tag(platform: dict[str, Any]) -> dict[str, Any]:
    scope = tooling_scope(platform)
    kind = final_gauge_kind(scope)
    if not kind:
        raise ValueError(f"{platform['slug']}: {scope} has no final gauge")
    name = platform["platform"]
    return {
        "IndustryPlatform": name,
        "IndustryPartKind": kind,
        "IndustryDisplayName": f"item.tacz.press_die.{kind}.{name}",
        "IndustryActionProfile": action_profile(platform),
        "IndustryToolingScope": scope,
        "DieTargetKind": action_profile(platform),
    }


def generated_template_blank_file(policy: dict[str, Any]) -> dict[Path, Any]:
    """A real multi-input Basin makes one physical sheet/plate workpiece.

    The blank is the only stack sent down a belt. A dossier/sample later sits in
    the held position of a deployment station, so no recipe asks a Depot to hold
    several unrelated inputs at once.
    """
    return {
        RESOURCE_ROOT / "data/tacz/recipe/create/industry/form_template_blank.json": {
            "fabric:load_conditions": CREATE_CONDITIONS,
            "type": "create:compacting",
            "ingredients": policy["template_blank_ingredients"],
            "results": [output("tacz:gun_blueprint", template_blank_tag())],
        }
    }


def generated_action_jig_files(platforms: list[dict[str, Any]], policy: dict[str, Any]) -> dict[Path, Any]:
    """Generate reusable family fixtures and their real gauge-blank stations."""
    files: dict[Path, Any] = {}
    profiles_in_use = sorted({action_profile(platform) for platform in platforms})
    for profile_id in profiles_in_use:
        profile = policy["process_profiles"][profile_id]
        jig = profile["jig"]
        files[RESOURCE_ROOT / f"data/tacz/recipe/create/industry/form_action_jig_{profile_id}.json"] = {
            "fabric:load_conditions": CREATE_CONDITIONS,
            "type": "create:mechanical_crafting",
            "key": jig["key"],
            "pattern": jig["pattern"],
            "result": output("tacz:press_die", action_jig_tag(profile_id)),
        }

    # A gauge blank is deliberately selected with the family fixture first.
    # This prevents platform gauge recipes from colliding with component-die
    # calibration (same die stock + same template) and visibly separates the
    # two physical tooling stages.
    gauge_profiles = sorted({(action_profile(platform), tooling_scope(platform)) for platform in platforms
                             if tooling_scope(platform) in {"critical_gauge", "final_acceptance"}})
    if any(scope == "final_acceptance" for _, scope in gauge_profiles):
        # A precision acceptance blank is not the same generic die stock used
        # for a field critical gauge. This is both a real high-tier material
        # gate and prevents two Create deployments with identical inputs from
        # claiming different gauge outputs for the same action profile.
        files[RESOURCE_ROOT / "data/tacz/recipe/create/industry/form_acceptance_gauge_stock.json"] = {
            "fabric:load_conditions": CREATE_CONDITIONS,
            "type": "create:mechanical_crafting",
            "key": {
                "S": "tacz:high_carbon_steel_plate",
                "B": "create:brass_sheet",
                "D": "minecraft:diamond",
                "E": "minecraft:echo_shard",
            },
            "pattern": ["SDS", "BEB", "S S"],
            "result": output("tacz:press_die", acceptance_gauge_stock_tag()),
        }
    for profile_id, scope in gauge_profiles:
        gauge_kind = final_gauge_kind(scope)
        stock = generic_component_die_stock() if scope == "critical_gauge" else acceptance_gauge_stock_tag()
        files[RESOURCE_ROOT / f"data/tacz/recipe/create/industry/select_{gauge_kind}_blank_{profile_id}.json"] = deploying(
            partial("tacz:press_die", stock),
            partial("tacz:press_die", action_jig_tag(profile_id)),
            output("tacz:press_die", action_gauge_blank_tag(profile_id, scope)),
        )
    return files


def generated_template_transfer_files(platform: dict[str, Any]) -> dict[Path, Any]:
    """Generate sample survey plus master/production-template transfer routes."""
    name = platform["platform"]
    blank = partial("tacz:gun_blueprint", template_blank_tag())
    master = master_blueprint_tag(platform)
    production = production_blueprint_tag(platform)
    legacy = legacy_blueprint_match_tag(platform)
    # Existing blueprint_<platform>.json is deliberately reused so an update
    # replaces the old nonsensical seed recipe instead of leaving it behind.
    return {
        RESOURCE_ROOT / f"data/tacz/recipe/create/industry/survey_dossier_{name}.json": deploying(
            blank, partial("tacz:modern_kinetic_gun", {"GunId": platform["gun_id"]}),
            output("tacz:gun_blueprint", master),
        ),
        RESOURCE_ROOT / f"data/tacz/recipe/create/industry/blueprint_{name}.json": deploying(
            blank, partial("tacz:gun_blueprint", master), output("tacz:gun_blueprint", production),
        ),
        RESOURCE_ROOT / f"data/tacz/recipe/create/industry/copy_template_{name}.json": deploying(
            blank, partial("tacz:gun_blueprint", production), output("tacz:gun_blueprint", production),
        ),
        # Old worlds can convert their previous platform blueprint into the new
        # explicit production template, but a master dossier cannot accidentally
        # match this route because its display key is intentionally different.
        RESOURCE_ROOT / f"data/tacz/recipe/create/industry/restore_template_{name}.json": deploying(
            blank, partial("tacz:gun_blueprint", legacy), output("tacz:gun_blueprint", production),
        ),
    }


def initial_maintenance_state() -> dict[str, Any]:
    """Initial phase-A custom data written onto every real industrial gun result.

    The random maintenance seed is intentionally not baked into a shared recipe
    result. The first server-side handling assigns one per ItemStack; conditions
    themselves are already explicit full/clean state at manufacture time.
    """
    return {
        "IndustryMaintenanceSchema": 1,
        "IndustryConditionReceiver": 10000,
        "IndustryConditionBolt": 10000,
        "IndustryConditionBarrel": 10000,
        "IndustryConditionTrigger": 10000,
        "IndustryConditionRecoil": 10000,
        "IndustryFouling": 0,
        "IndustryMaintenanceShots": 0,
    }


def generated_platform_files(platform: dict[str, Any]) -> dict[Path, Any]:
    slug = platform["slug"]
    name = platform["platform"]
    gun_id = platform["gun_id"]
    blueprint = platform["blueprint"]
    parts = platform["parts"]
    materials = platform["materials"]
    profile = action_profile(platform)
    scope = tooling_scope(platform)
    production_blueprint = partial("tacz:gun_blueprint", production_blueprint_tag(platform))
    result: dict[Path, Any] = {}
    result.update(generated_template_transfer_files(platform))

    component_entries: list[dict[str, str]] = []
    for part in parts:
        structural = part["structural"]
        blank_class = part["blank_class"]
        final_kind = part["kind"]
        die_key = f"item.tacz.press_die.component_{name}_{final_kind}"
        component_key = f"item.tacz.gun_component.{name}_{final_kind}"
        die_blank = partial("tacz:press_die", {
            "IndustryPlatform": "machining",
            "IndustryPartKind": "die_blank",
            "IndustryDisplayName": f"item.tacz.press_die_blank.{blank_class}",
            "DieTargetKind": blank_class,
        })
        # A component die's platform/kind/display/target are its stable physical
        # identity. Do not stamp action-profile/scope provenance onto this stack:
        # old-world dies intentionally match the same forming recipe, and JEI/
        # REI must see the calibration output as the exact input of that route.
        calibrated_die = {
            "IndustryPlatform": name,
            "IndustryPartKind": "component_die",
            "IndustryDisplayName": die_key,
            "DieTargetKind": final_kind,
        }
        # A production template is the normal hard gate. Saved-world blueprints
        # first pass through restore_template_<platform>, which yields this same
        # explicit production medium without letting a master dossier skip it.
        result[RESOURCE_ROOT / f"data/tacz/recipe/create/industry/calibrate_component_die_{name}_{structural}.json"] = deploying(
            die_blank, production_blueprint, output("tacz:press_die", calibrated_die)
        )
        structural_blank = partial("tacz:gun_component_blank", {
            "IndustryPlatform": "machining",
            "IndustryPartKind": f"{blank_class}_blank",
            "IndustryDisplayName": "item.tacz.gun_component_blank",
        })
        # Like dies, components keep only their stable assembly identity. The
        # action profile lives on the platform/template/assembly declaration;
        # adding it here would disconnect a form-recipe output from the terminal
        # recipe's backward-compatible partial-NBT input in JEI/REI.
        component = {
            "IndustryPlatform": name,
            "IndustryPartKind": final_kind,
            "IndustryDisplayName": component_key,
        }
        # The die tag is intentionally identical on calibration output and
        # forming input. That preserves old-world compatibility and gives both
        # recipe viewers a continuous die → component graph.
        result[RESOURCE_ROOT / f"data/tacz/recipe/create/industry/form_component_{name}_{structural}.json"] = deploying(
            structural_blank, partial("tacz:press_die", calibrated_die), output("tacz:gun_component", component)
        )
        component_entries.append({
            "structural": structural,
            "blank_class": blank_class,
            "kind": final_kind,
            "display_name": component_key,
        })

    if materials:
        furniture_blank = partial("tacz:gun_component_blank", furniture_blank_tag())
        result[RESOURCE_ROOT / f"data/tacz/recipe/create/industry/calibrate_furniture_{name}.json"] = deploying(
            furniture_blank, production_blueprint, output("tacz:gun_component", furniture_kit_tag(platform))
        )

    final_gauge: dict[str, Any] | None = None
    if scope in {"critical_gauge", "final_acceptance"}:
        final_gauge = platform_gauge_tag(platform)
        gauge_blank = partial("tacz:press_die", action_gauge_blank_tag(profile, scope))
        result[RESOURCE_ROOT / f"data/tacz/recipe/create/industry/calibrate_{scope}_{name}.json"] = deploying(
            gauge_blank, production_blueprint, output("tacz:press_die", final_gauge)
        )

    result[RESOURCE_ROOT / f"data/tacz/industry/assembly/gun/{slug}.json"] = {
        "platform": name,
        "manufacturing_tier": manufacturing_tier(platform),
        "action_profile": profile,
        "tooling_scope": scope,
        "blueprint_display_name": blueprint["display_name"],
        "legacy_blueprint_display_name": blueprint["legacy_display_name"],
        "terminal_process": f"tacz:create/industry/assemble_{slug}",
        "components": component_entries,
        "materials": materials,
    }

    initial = parts[0]
    initial_component_key = f"item.tacz.gun_component.{name}_{initial['kind']}"

    def press_fit_step() -> dict[str, Any]:
        # One moving workpiece remains on the belt. A press is an actual fit-up
        # station, not a cosmetic duplicate of the next material deployment.
        return {
            "type": "create:pressing",
            "ingredient": "$ingredient",
            "results": ["$result"],
        }

    # Templates establish dies/gauges upstream. The terminal itself begins
    # with the receiver/frame workpiece, so an industrial line can mass-produce
    # after tooling setup instead of "installing a blueprint" in every gun.
    #
    # Keep this list within Create Fly 26.2's seven actual JEI stage cells. This
    # is not a visual-only truncation: a longer sequence crashes the native
    # SequencedAssemblyCategory renderer. Critical/final action jigs remain
    # mandatory upstream when they select their gauge blank; the terminal then
    # uses the calibrated platform gauge as the real acceptance station.
    deployments: list[dict[str, Any]] = []

    def deployment_step(held: Any, keep: bool = False) -> dict[str, Any]:
        step: dict[str, Any] = {
            "type": "create:deploying",
            "target": "$ingredient",
            "ingredient": held,
            "results": ["$result"],
        }
        if keep:
            step["keep_held_item"] = True
        return step

    for part in parts[1:]:
        deployments.append(deployment_step(partial("tacz:gun_component", {
            "IndustryPlatform": name,
            "IndustryPartKind": part["kind"],
            "IndustryDisplayName": f"item.tacz.gun_component.{name}_{part['kind']}",
        })))
    if materials:
        deployments.append(deployment_step(partial("tacz:gun_component", furniture_kit_tag(platform))))

    # A simple family has no platform acceptance gauge, so its reusable jig is
    # the terminal's final fitting station. Critical/final scopes already use
    # that same jig to create the gauge blank upstream and deploy only the
    # platform-calibrated gauge here.
    if scope == "family_jig":
        deployments.append(deployment_step(partial("tacz:press_die", action_jig_tag(profile)), keep=True))
    elif final_gauge is not None:
        deployments.append(deployment_step(partial("tacz:press_die", final_gauge), keep=True))

    if len(deployments) > MAX_SEQUENCED_ASSEMBLY_STEPS:
        raise ValueError(
            f"{slug}: {len(deployments)} mandatory terminal deployments exceed Create Fly's "
            f"{MAX_SEQUENCED_ASSEMBLY_STEPS}-stage sequenced-assembly viewer limit"
        )

    # Use remaining real stage capacity for meaningful press fits. Standard
    # platform-tooling lines retain two fits (barrel join and final exterior
    # fit); jig/gauge lines retain their final fit after the acceptance station.
    press_budget = min(2, MAX_SEQUENCED_ASSEMBLY_STEPS - len(deployments))
    press_after: set[int] = set()
    if press_budget == 1:
        press_after.add(len(deployments) - 1)
    elif press_budget >= 2:
        press_after.add(min(1, len(deployments) - 1))
        press_after.add(len(deployments) - 1)

    sequence: list[dict[str, Any]] = []
    for index, deployment in enumerate(deployments):
        sequence.append(deployment)
        if index in press_after:
            sequence.append(press_fit_step())

    if len(sequence) > MAX_SEQUENCED_ASSEMBLY_STEPS:
        raise ValueError(f"{slug}: terminal sequence exceeds {MAX_SEQUENCED_ASSEMBLY_STEPS} Create Fly viewer stages")

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
            "IndustryActionProfile": profile,
        }),
        "result": output("tacz:modern_kinetic_gun", {
            "GunId": gun_id,
            "GunFireMode": platform["fire_mode"],
            "GunCurrentAmmoCount": 0,
            "HasBulletInBarrel": False,
            "IndustryAssemblyPlatform": name,
            "IndustryAssemblyRecipe": f"tacz:gun/{slug}",
            "IndustryAssemblyTier": manufacturing_tier(platform),
            "IndustryAssemblyActionProfile": profile,
            "IndustryAssemblyToolingScope": scope,
            **initial_maintenance_state(),
        }),
        "sequence": sequence,
    }
    return result

def blueprint_custom_data(platform: dict[str, Any]) -> dict[str, Any]:
    """Loot/trade grants original dossiers; players transfer them to tooling templates."""
    return master_blueprint_tag(platform)


def snbt_compound(values: dict[str, Any]) -> str:
    """Small deterministic SNBT writer for blueprint custom-data strings."""
    parts: list[str] = []
    for key, value in values.items():
        if not isinstance(value, str):
            raise ValueError(f"SNBT blueprint custom data expects strings, got {key}")
        parts.append(f"{key}:{json.dumps(value, ensure_ascii=False)}")
    return "{" + ",".join(parts) + "}"



def generated_blueprint_acquisition_files(platforms: list[dict[str, Any]], acquisition: dict[str, Any]) -> dict[Path, Any]:
    """Generate tiered master-dossier sources instead of a 53-entry trade lottery.

    Villager stock supplements legacy/service access at appropriate career
    levels, while advanced/precision dossiers are expedition finds. The output
    is deliberately a master dossier; the player performs a physical transfer
    to a production template on a one-workpiece Create station.
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


def obsolete_dossier_commission_files(expected: dict[Path, Any]) -> set[Path]:
    """Remove generated archive-commission table recipes that no longer have a platform source."""
    root = RESOURCE_ROOT / "data/tacz/recipe/industry"
    if not root.exists():
        return set()
    return {path for path in root.glob("dossier_commission_*.json") if path not in expected}


def obsolete_generated_platform_files(expected: dict[Path, Any], platforms: list[dict[str, Any]]) -> set[Path]:
    """Remove renamed per-platform component recipes after action-profile migration.

    Action profiles give old mechanisms real structural names (hinge lock,
    cylinder timing, lever block, etc.). Their recipe file stems therefore no
    longer use the old receiver/bolt/recoil labels. Leaving the former files
    would register duplicate Create deployments with the same physical input.
    """
    root = RESOURCE_ROOT / "data/tacz/recipe/create/industry"
    if not root.exists():
        return set()
    platform_ids = {platform["platform"] for platform in platforms}
    stale: set[Path] = set()
    for prefix in ("form_component_", "calibrate_component_die_"):
        for path in root.glob(f"{prefix}*.json"):
            suffix = path.stem[len(prefix):]
            if any(suffix.startswith(f"{platform_id}_") for platform_id in platform_ids) and path not in expected:
                stale.add(path)
    return stale


def obsolete_template_compatibility_files(expected: dict[Path, Any]) -> set[Path]:
    """Remove an earlier per-die legacy bridge in favour of one restore step.

    Old template stacks are converted once by ``restore_template_*``. Leaving
    hundreds of duplicate calibration recipes would clutter recipe viewers and
    weaken the production-template gate without improving compatibility.
    """
    root = RESOURCE_ROOT / "data/tacz/recipe/create/industry"
    if not root.exists():
        return set()
    stale = {
        path for path in root.glob("calibrate_*_legacy_*.json")
        if path not in expected
    }
    # Naming from the draft used critical_gauge_gauge; its early acceptance
    # selector also used generic die stock. Both must disappear so precision
    # acceptance always starts from the dedicated multi-slot stock recipe.
    stale.update(root.glob("select_*_gauge_gauge_blank_*.json"))
    stale.update(root.glob("select_final_acceptance_gauge_blank_*.json"))
    return {path for path in stale if path not in expected}


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


def cartridge_gauge_blank_tag() -> dict[str, Any]:
    """Neutral precision blank that a real chamber/launch-tube datum can calibrate."""
    return {
        "IndustryPlatform": "ammunition",
        "IndustryPartKind": "cartridge_gauge_blank",
        "IndustryDisplayName": "item.tacz.press_die_blank.cartridge_gauge",
    }


def cartridge_gauge_tag(caliber: dict[str, Any]) -> dict[str, Any]:
    return {
        "IndustryPlatform": "ammunition",
        "IndustryPartKind": "cartridge_gauge",
        "IndustryDisplayName": f"item.tacz.press_die.gauge_{caliber['id']}",
        "CartridgeCaliber": caliber["id"],
    }


def case_datum_gauge_tag(caliber: dict[str, Any]) -> dict[str, Any]:
    return {
        "IndustryPlatform": "ammunition",
        "IndustryPartKind": "case_datum_gauge",
        "IndustryDisplayName": f"item.tacz.press_die.case_gauge_{caliber['id']}",
        "CartridgeCaliber": caliber["id"],
    }


def projectile_datum_gauge_tag(caliber: dict[str, Any]) -> dict[str, Any]:
    return {
        "IndustryPlatform": "ammunition",
        "IndustryPartKind": "projectile_datum_gauge",
        "IndustryDisplayName": f"item.tacz.press_die.projectile_gauge_{caliber['id']}_{caliber['projectile_type']}",
        "CartridgeCaliber": caliber["id"],
        "ProjectileType": caliber["projectile_type"],
    }


def generated_cartridge_gauge_blank_file() -> dict[Path, Any]:
    """One Basin-formed neutral gauge blank; exact calibre comes later from a datum."""
    return {
        RESOURCE_ROOT / "data/tacz/recipe/create/industry/press_die_cartridge_gauge_blank.json": {
            "fabric:load_conditions": CREATE_CONDITIONS,
            "type": "create:compacting",
            "ingredients": [
                "tacz:high_carbon_steel_plate",
                "create:brass_sheet",
                "minecraft:quartz",
            ],
            "results": [output("tacz:press_die", cartridge_gauge_blank_tag())],
        }
    }


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
    # RPG ammunition has a real launch-motor housing between the heavy brass
    # blank sequence and the final cartridge-machine case input. It must be a
    # persistent ItemStack, not just a transitional name hidden inside the
    # sequenced assembly.
    motor_housing = None
    if caliber.get("motor_housing_name_en") and caliber.get("motor_housing_name_zh"):
        motor_housing = {
            "IndustryPlatform": "ammunition",
            "IndustryPartKind": "motor_housing",
            "IndustryDisplayName": f"item.tacz.cartridge_case.{caliber_id}.motor_housing",
            "CartridgeCaliber": caliber_id,
            "CartridgeAmmoId": ammo_id,
        }
    case_form_output = motor_housing if motor_housing is not None else case
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

    gauge_tag = cartridge_gauge_tag(caliber)
    calibration_tool = partial("tacz:press_die", gauge_tag)
    master_gun = caliber.get("master_gun")
    if isinstance(master_gun, str) and master_gun:
        # A sample firearm is now measured once to make a reusable calibre
        # datum gauge. The gauge, rather than the complete gun, then calibrates
        # both case and projectile dies. This removes the duplicated gun-held
        # calibration while preserving a real physical chamber datum.
        files[RESOURCE_ROOT / f"data/tacz/recipe/create/industry/calibrate_cartridge_gauge_{caliber_id}.json"] = deploying(
            partial("tacz:press_die", cartridge_gauge_blank_tag()),
            partial("tacz:modern_kinetic_gun", {"GunId": master_gun}),
            output("tacz:press_die", gauge_tag),
        )
    else:
        gauge = caliber["calibration_gauge"]
        # Some bundled loose-ammo ids intentionally have no firearm in the
        # default pack. A real multi-slot Mechanical Crafter forms their named
        # hardened calibre gauge from an explicit datum, never from an unrelated
        # gun pretending to be a chamber reference.
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

    # Reverse-engineering routes use real evidence, not arbitrary per-calibre
    # seed items. A complete loose round describes both sides of a cartridge;
    # an empty/fired case only proves case geometry, while a projectile core
    # only proves projectile calibre/type. Evidence is consumed destructively.
    case_gauge = case_datum_gauge_tag(caliber)
    projectile_gauge = projectile_datum_gauge_tag(caliber)
    # Reverse recipes carry the complete current stack identity so JEI/REI can
    # follow case/projectile output into the evidence branch. Unlike ordinary
    # forming, this is a new optional acquisition route, so it deliberately
    # avoids overlapping "legacy" partial recipes that would duplicate the
    # same Create deployment for current stacks.
    gauge_blank = partial("tacz:press_die", cartridge_gauge_blank_tag())
    files[RESOURCE_ROOT / f"data/tacz/recipe/create/industry/reverse_case_gauge_{caliber_id}.json"] = deploying(
        gauge_blank, partial("tacz:cartridge_case", case), output("tacz:press_die", case_gauge), keep=False
    )
    if caliber["eject_case"]:
        files[RESOURCE_ROOT / f"data/tacz/recipe/create/industry/reverse_spent_case_gauge_{caliber_id}.json"] = deploying(
            gauge_blank, partial("tacz:cartridge_case", spent_case), output("tacz:press_die", case_gauge), keep=False
        )
    files[RESOURCE_ROOT / f"data/tacz/recipe/create/industry/reverse_projectile_gauge_{caliber_id}.json"] = deploying(
        gauge_blank, partial("tacz:projectile_core", projectile), output("tacz:press_die", projectile_gauge), keep=False
    )
    files[RESOURCE_ROOT / f"data/tacz/recipe/create/industry/reverse_cartridge_gauge_{caliber_id}.json"] = deploying(
        gauge_blank, partial("tacz:ammo", {"AmmoId": ammo_id}), output("tacz:press_die", gauge_tag), keep=False
    )

    # All 24 calibres share a visible datum → two-die branch. A sample gun,
    # declared no-gun datum, or a complete reverse-engineered round yields the
    # full gauge; case/projectile evidence can recover only its matching die.
    # The final four-slot cartridge machine, batch balance, casing recovery and
    # projectile construction remain unchanged.
    files[RESOURCE_ROOT / f"data/tacz/recipe/create/industry/calibrate_case_die_{caliber_id}.json"] = deploying(
        case_blank_die, calibration_tool, output("tacz:press_die", case_die)
    )
    files[RESOURCE_ROOT / f"data/tacz/recipe/create/industry/calibrate_case_die_from_case_gauge_{caliber_id}.json"] = deploying(
        case_blank_die, partial("tacz:press_die", case_gauge), output("tacz:press_die", case_die)
    )
    files[RESOURCE_ROOT / f"data/tacz/recipe/create/industry/calibrate_projectile_die_{caliber_id}.json"] = deploying(
        projectile_blank_die, calibration_tool, output("tacz:press_die", projectile_die)
    )
    files[RESOURCE_ROOT / f"data/tacz/recipe/create/industry/calibrate_projectile_die_from_projectile_gauge_{caliber_id}.json"] = deploying(
        projectile_blank_die, partial("tacz:press_die", projectile_gauge), output("tacz:press_die", projectile_die)
    )
    case_blank_count = caliber["case_blank_count"]
    projectile_blank_count = caliber["projectile_blank_count"]
    if case_blank_count == 1:
        files[RESOURCE_ROOT / f"data/tacz/recipe/create/industry/form_case_{caliber_id}.json"] = deploying(
            case_stock, partial("tacz:press_die", case_die), output("tacz:cartridge_case", case_form_output, stack_limit)
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
            "result": output("tacz:cartridge_case", case_form_output, stack_limit),
            "sequence": case_sequence,
        }

    if motor_housing is not None:
        files[RESOURCE_ROOT / f"data/tacz/recipe/create/industry/finish_case_{caliber_id}.json"] = deploying(
            partial("tacz:cartridge_case", motor_housing), partial("tacz:press_die", case_die),
            output("tacz:cartridge_case", case, stack_limit)
        )

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
        f"item.tacz.press_die.gauge_{caliber_id}": caliber[f"gauge_name_{suffix}"],
        f"item.tacz.press_die.case_gauge_{caliber_id}": caliber[f"case_gauge_name_{suffix}"],
        f"item.tacz.press_die.projectile_gauge_{caliber_id}_{projectile_type}": caliber[f"projectile_gauge_name_{suffix}"],
        f"item.tacz.press_die.case_{caliber_id}": caliber[f"case_die_name_{suffix}"],
        f"item.tacz.press_die.projectile_{caliber_id}_{projectile_type}": caliber[f"projectile_die_name_{suffix}"],
    }
    if caliber["eject_case"]:
        entries[f"item.tacz.cartridge_case.spent_{caliber_id}"] = caliber[f"spent_case_name_{suffix}"]
    if caliber["case_blank_count"] > 1:
        name = caliber.get(f"motor_housing_name_{suffix}", caliber[f"case_name_{suffix}"])
        entries[f"item.tacz.cartridge_case_blank.incomplete_{caliber_id}"] = (
            f"未完成的{name}" if suffix == "zh" else f"Incomplete {name}"
        )
    if caliber.get("motor_housing_name_en") and caliber.get("motor_housing_name_zh"):
        entries[f"item.tacz.cartridge_case.{caliber_id}.motor_housing"] = caliber[
            f"motor_housing_name_{suffix}"
        ]
    if caliber.get("projectile_payloads"):
        entries[f"item.tacz.projectile_blank.body_{caliber_id}"] = caliber[f"projectile_body_name_{suffix}"]
        for index, name in enumerate(caliber[f"projectile_payload_names_{suffix}"], start=1):
            entries[f"item.tacz.projectile_blank.payload_{caliber_id}_{index}"] = name
    elif caliber["projectile_blank_count"] > 1:
        name = caliber[f"projectile_name_{suffix}"]
        entries[f"item.tacz.projectile_blank.incomplete_{caliber_id}"] = (
            f"未完成的{name}" if suffix == "zh" else f"Incomplete {name}"
        )
    return entries


def carrier_gauge_blank_tag() -> dict[str, Any]:
    """Neutral hardened stock; it has no calibre/family identity until measured."""
    return {
        "IndustryPlatform": "feeding",
        "IndustryPartKind": "carrier_gauge_blank",
        "IndustryDisplayName": "item.tacz.press_die_blank.carrier_gauge",
        "DieTargetKind": "carrier",
    }


def carrier_feed_kit_blank_tag() -> dict[str, Any]:
    """Neutral spring/follower/link stock formed in a real multi-input Basin."""
    return {
        "IndustryPlatform": "feeding",
        "IndustryPartKind": "carrier_feed_kit_blank",
        "IndustryDisplayName": "item.tacz.gun_component_blank.carrier_feed_kit",
    }


def carrier_spec_tag(carrier: dict[str, Any], kind: str, display_key: str) -> dict[str, Any]:
    """Stable physical identity shared by gauge, body and feed subassemblies."""
    return {
        "IndustryPlatform": "feeding",
        "IndustryPartKind": kind,
        "IndustryDisplayName": display_key,
        "DieTargetKind": carrier["id"],
        "MagazineFamily": carrier["family"],
        "MagazineAmmoId": carrier["ammo"],
        "MagazineCapacity": carrier["capacity"],
    }


def carrier_gauge_tag(carrier: dict[str, Any]) -> dict[str, Any]:
    return carrier_spec_tag(carrier, "carrier_gauge", f"item.tacz.press_die.carrier_gauge.{carrier['id']}")


def carrier_body_tag(carrier: dict[str, Any]) -> dict[str, Any]:
    return carrier_spec_tag(carrier, "carrier_body", f"item.tacz.gun_component.carrier_body.{carrier['id']}")


def carrier_feed_kit_tag(carrier: dict[str, Any]) -> dict[str, Any]:
    return carrier_spec_tag(carrier, "carrier_feed_kit", f"item.tacz.gun_component.carrier_feed_kit.{carrier['id']}")


def carrier_magazine_tag(carrier: dict[str, Any]) -> dict[str, Any]:
    """The actual configured removable stack; no incomplete magazine is usable."""
    return {
        "MagazineFamily": carrier["family"],
        "MagazineAmmoId": carrier["ammo"],
        "MagazineCapacity": carrier["capacity"],
        "MagazineAmmoCount": 0,
        "MagazineDisplayName": carrier["_display_name"],
    }


def carrier_forming_recipe(stock: Any, stock_count: int, gauge: dict[str, Any], result: dict[str, Any],
                            transitional: dict[str, Any]) -> dict[str, Any]:
    """Form one named carrier subassembly while one workpiece moves on the line.

    Additional neutral blanks are supplied one by one by a Deployer; they are
    real material mass/length, never simultaneous Depot inputs.  The final
    retained gauge is the only station that assigns family/ammo/capacity.
    """
    gauge_input = partial("tacz:press_die", gauge)
    if stock_count == 1:
        return deploying(stock, gauge_input, result)
    sequence: list[dict[str, Any]] = []
    for _ in range(stock_count - 1):
        sequence.append({
            "type": "create:deploying",
            "target": "$ingredient",
            "ingredient": stock,
            "results": ["$result"],
        })
    sequence.append({
        "type": "create:deploying",
        "target": "$ingredient",
        "ingredient": gauge_input,
        "results": ["$result"],
        "keep_held_item": True,
    })
    if len(sequence) > MAX_SEQUENCED_ASSEMBLY_STEPS:
        raise ValueError("carrier forming sequence exceeds Create Fly's native seven-stage viewer limit")
    return {
        "fabric:load_conditions": CREATE_CONDITIONS,
        "type": "create:sequenced_assembly",
        "ingredient": stock,
        "transitional_item": transitional,
        "result": result,
        "sequence": sequence,
    }


def generated_magazine_files(carriers: list[dict[str, Any]], platforms: list[dict[str, Any]]) -> dict[Path, Any]:
    """Generate a real carrier-tooling chain for every removable default feed.

    The former route held a complete firearm over a generic shell and output a
    finished magazine in one deployment.  Here production templates establish
    reusable carrier gauges upstream; a shell body and a feed mechanism are
    independent industrial components and are only joined at the final single-
    workpiece station.  An empty carrier can also be consumed as reverse
    metrology evidence to recover its gauge.
    """
    files: dict[Path, Any] = {
        # Keep the existing neutral shell item/recipe identity for old-world
        # salvage and stockpiles. It remains deliberately nameless until a
        # calibrated carrier gauge forms the actual body.
        RESOURCE_ROOT / "data/tacz/recipe/create/industry/magazine_blank.json": {
            "fabric:load_conditions": CREATE_CONDITIONS,
            "type": "create:compacting",
            "ingredients": [
                "tacz:high_carbon_steel_plate",
                "tacz:high_carbon_steel_plate",
                "tacz:high_carbon_steel_plate",
                "create:brass_sheet",
                "create:brass_nugget",
            ],
            "results": [{"id": "tacz:magazine_blank"}],
        },
        # Springs/followers/belt-link stock is a real Basin operation. It is a
        # separate physical item from the carrier shell, so the final line has
        # exactly one moving body and one held supply component.
        RESOURCE_ROOT / "data/tacz/recipe/create/industry/form_carrier_feed_kit_blank.json": {
            "fabric:load_conditions": CREATE_CONDITIONS,
            "type": "create:compacting",
            "ingredients": [
                "tacz:high_carbon_steel_ingot",
                "tacz:high_carbon_steel_plate",
                "create:brass_nugget",
                "minecraft:iron_nugget",
            ],
            "results": [output("tacz:gun_component_blank", carrier_feed_kit_blank_tag())],
        },
        # This blank is intentionally separate from a cartridge datum gauge:
        # carrier latch/feed geometry is not inferred from a chamber calibre.
        RESOURCE_ROOT / "data/tacz/recipe/create/industry/press_die_carrier_gauge_blank.json": {
            "fabric:load_conditions": CREATE_CONDITIONS,
            "type": "create:compacting",
            "ingredients": [
                "tacz:high_carbon_steel_plate",
                "create:brass_sheet",
                "minecraft:quartz",
                "minecraft:iron_nugget",
            ],
            "results": [output("tacz:press_die", carrier_gauge_blank_tag())],
        },
    }
    platform_by_slug = {platform["slug"]: platform for platform in platforms}
    if len(platform_by_slug) != len(platforms):
        raise ValueError("duplicate platform slug while generating carrier tooling")

    for carrier in carriers:
        carrier_id = carrier["id"]
        profile = carrier["_profile"]
        gauge = carrier_gauge_tag(carrier)
        body = carrier_body_tag(carrier)
        feed_kit = carrier_feed_kit_tag(carrier)
        magazine = carrier_magazine_tag(carrier)

        # Any declared gun that accepts this exact physical carrier may donate
        # its production template to establish the same reusable gauge. This
        # lets STANAG/QBZ-compatible platforms share a real carrier standard
        # without choosing an arbitrary "owner" gun or consuming a complete one.
        for source_slug in carrier["source_guns"]:
            platform = platform_by_slug.get(source_slug)
            if platform is None:
                raise ValueError(
                    f"{MAGAZINE_CARRIER_MANIFEST}: {carrier_id} source {source_slug} has no generated gun platform"
                )
            files[RESOURCE_ROOT / (
                f"data/tacz/recipe/create/industry/calibrate_carrier_gauge_{carrier_id}_{source_slug}.json"
            )] = deploying(
                partial("tacz:press_die", carrier_gauge_blank_tag()),
                partial("tacz:gun_blueprint", production_blueprint_tag(platform)),
                output("tacz:press_die", gauge),
            )

        # Destructive reverse engineering is deliberately an alternative to a
        # production template, not a duplicate loose-NBT recipe. A loaded mag
        # fails the exact zero-round evidence match and must be unloaded first.
        files[RESOURCE_ROOT / f"data/tacz/recipe/create/industry/reverse_carrier_gauge_{carrier_id}.json"] = deploying(
            partial("tacz:press_die", carrier_gauge_blank_tag()),
            partial("tacz:magazine", magazine),
            output("tacz:press_die", gauge),
            keep=False,
        )

        files[RESOURCE_ROOT / f"data/tacz/recipe/create/industry/form_carrier_body_{carrier_id}.json"] = (
            carrier_forming_recipe(
                "tacz:magazine_blank",
                profile["body_blank_count"],
                gauge,
                output("tacz:gun_component", body),
                {"id": "tacz:magazine_blank"},
            )
        )
        files[RESOURCE_ROOT / f"data/tacz/recipe/create/industry/form_carrier_feed_kit_{carrier_id}.json"] = (
            carrier_forming_recipe(
                partial("tacz:gun_component_blank", carrier_feed_kit_blank_tag()),
                profile["feed_kit_blank_count"],
                gauge,
                output("tacz:gun_component", feed_kit),
                output("tacz:gun_component_blank", carrier_feed_kit_blank_tag()),
            )
        )
        # The carrier body is the sole target/workpiece. The named feed kit is
        # held and consumed by the supply/deployment station; it is never
        # represented as a second item sitting on the Depot or belt.
        files[RESOURCE_ROOT / f"data/tacz/recipe/create/industry/assemble_carrier_{carrier_id}.json"] = deploying(
            partial("tacz:gun_component", body),
            partial("tacz:gun_component", feed_kit),
            output("tacz:magazine", magazine),
            keep=False,
        )
    return files


def magazine_language_entries(carriers: list[dict[str, Any]], language: str) -> dict[str, str]:
    chinese = language == "zh_cn"
    entries = {
        "item.tacz.magazine_blank": "中性弹匣壳体毛坯" if chinese else "Neutral Magazine Body Blank",
        "item.tacz.press_die_blank.carrier_gauge": "中性供弹器规格量规毛坯" if chinese else "Neutral Carrier Specification Gauge Blank",
        "item.tacz.gun_component_blank.carrier_feed_kit": "中性供弹组件毛坯" if chinese else "Neutral Carrier Feed-Kit Blank",
    }
    for carrier in carriers:
        suffix = "zh" if chinese else "en"
        name = carrier[f"name_{suffix}"]
        profile = carrier["_profile"]
        feed_name = profile[f"feed_name_{suffix}"]
        entries[f"item.tacz.press_die.carrier_gauge.{carrier['id']}"] = (
            f"{name}规格量规" if chinese else f"{name} Specification Gauge"
        )
        entries[f"item.tacz.gun_component.carrier_body.{carrier['id']}"] = (
            f"{name}壳体" if chinese else f"{name} Body"
        )
        entries[f"item.tacz.gun_component.carrier_feed_kit.{carrier['id']}"] = (
            f"{name}{feed_name}" if chinese else f"{name} {feed_name}"
        )
    return entries


def obsolete_generated_carrier_files(expected: dict[Path, Any]) -> set[Path]:
    """Remove renamed/retired generator-owned carrier routes without touching datapack extension paths."""
    root = RESOURCE_ROOT / "data/tacz/recipe/create/industry"
    if not root.exists():
        return set()
    managed: set[Path] = set()
    for pattern in (
        "assemble_carrier_*.json",
        "calibrate_carrier_gauge_*.json",
        "reverse_carrier_gauge_*.json",
        "form_carrier_body_*.json",
        "form_carrier_feed_kit_*.json",
    ):
        managed.update(root.glob(pattern))
    managed.update({
        root / "form_carrier_feed_kit_blank.json",
        root / "press_die_carrier_gauge_blank.json",
    })
    return {path for path in managed if path.exists() and path not in expected}



def obsolete_legacy_magazine_files(carriers: list[dict[str, Any]]) -> set[Path]:
    """Remove only prior generator-owned finished-gun stamping recipes."""
    root = RESOURCE_ROOT / "data/tacz/recipe/create/magazine"
    paths = {
        root / f"{source}.json"
        for carrier in carriers for source in carrier["source_guns"]
    }
    return {path for path in paths if path.exists()}


def language_entries(platform: dict[str, Any], language: str) -> dict[str, str]:
    name = platform["platform"]
    blueprint = platform["blueprint"]
    chinese = language == "zh_cn"
    label = platform_display_label(platform, language)
    entries = {
        blueprint["display_name"]: blueprint["name_zh" if chinese else "name_en"],
        blueprint["legacy_display_name"]: (
            f"{label} 旧版平台蓝图" if chinese else f"{label} Legacy Platform Blueprint"
        ),
        f"item.tacz.gun_dossier.{name}": (
            f"{label} 原始工艺档案" if chinese else f"{label} Master Manufacturing Dossier"
        ),
    }
    for part in platform["parts"]:
        component_key = f"item.tacz.gun_component.{name}_{part['kind']}"
        die_key = f"item.tacz.press_die.component_{name}_{part['kind']}"
        label_part = part["name_zh" if chinese else "name_en"]
        die_label = part.get("die_name_zh" if chinese else "die_name_en")
        entries[component_key] = label_part
        entries[die_key] = die_label if die_label else (f"{label_part}模具" if chinese else f"{label_part} Die")
    if platform["materials"]:
        entries[f"item.tacz.gun_component.{name}_furniture_kit"] = (
            f"{label} 外装套件" if chinese else f"{label} Exterior Kit"
        )
    scope = tooling_scope(platform)
    if scope in {"critical_gauge", "final_acceptance"}:
        gauge_kind = final_gauge_kind(scope)
        entries[f"item.tacz.press_die.{gauge_kind}.{name}"] = (
            f"{label}{'关键配合量规' if scope == 'critical_gauge' else '最终验收检具'}"
            if chinese else f"{label} {'Critical Fit Gauge' if scope == 'critical_gauge' else 'Final Acceptance Gauge'}"
        )
    entries[f"item.tacz.gun_component.incomplete_{platform['slug']}"] = platform["incomplete"]["name_zh" if chinese else "name_en"]
    return entries



def default_gun_ammo_for_reference(platform: dict[str, Any]) -> str:
    """Read only bundled default-gun data for a generated curated reference row."""
    path = DEFAULT_GUN_DATA_ROOT / f"{platform['slug']}_data.json"
    if not path.exists():
        return ""
    try:
        data = read_json5(path)
    except (OSError, ValueError, json.JSONDecodeError):
        return ""
    ammo = data.get("ammo") if isinstance(data, dict) else ""
    return ammo if isinstance(ammo, str) else ""


def reference_ammunition_class(ammo: str) -> str:
    """Curated default-pack categorisation; third-party profiles must declare their own class."""
    path = ammo.split(":", 1)[-1]
    if path == "12g":
        return "shot_shell"
    if path == "40mm":
        return "grenade"
    if path == "rpg_rocket":
        return "rocket"
    return "cartridge" if path else "unknown"


def generated_reference_profile_files(platforms: list[dict[str, Any]]) -> dict[Path, Any]:
    """Emit a systematic factual baseline for every bundled default gun.

    These profiles are an independent GPL data layer, not edits to the licensed
    default gun pack. They give the runtime reference manager a complete,
    inspectable example of action/feed/ammunition facts and the same schema
    third-party compatibility packs use at resource reload.
    """
    files: dict[Path, Any] = {}
    feed_root = RESOURCE_ROOT / "data/tacz/industry/gun_feed"
    carrier_behaviour = {
        "detachable_magazine": "inserted_retained",
        "belt": "inserted_retained",
        "internal_box": "internal",
        "tube": "internal",
        "revolver": "internal",
        "single_shot": "internal",
        "stripper_clip": "reusable_loading_tool",
        "speedloader": "reusable_loading_tool",
    }
    for platform in platforms:
        gun_id = platform["gun_id"]
        namespace, gun_path = gun_id.split(":", 1)
        feed_path = feed_root / f"{platform['slug']}.json"
        feed = read_json(feed_path) if feed_path.exists() else {}
        mechanism = feed.get("mechanism") if isinstance(feed.get("mechanism"), str) else "legacy"
        supported_mechanisms = {
            "detachable_magazine", "belt", "internal_box", "tube", "revolver", "single_shot",
            "stripper_clip", "speedloader",
        }
        runtime_mechanism = mechanism if mechanism in supported_mechanisms else "legacy"
        device = mechanism if mechanism in supported_mechanisms else "unknown"
        capacity = feed.get("magazine_capacity") if isinstance(feed.get("magazine_capacity"), int) else 0
        reload_batch = feed.get("reload_batch") if isinstance(feed.get("reload_batch"), int) else 0
        ammo = feed.get("ammo") if isinstance(feed.get("ammo"), str) else default_gun_ammo_for_reference(platform)
        nominal = ammo.split(":", 1)[-1] if ammo else "unknown"
        external = device in {"detachable_magazine", "belt"}
        loading_device = device in {"stripper_clip", "speedloader"}
        profile = {
            "schema_version": 1,
            "generated_by": "tacz_industry_generator",
            "canonical_model": f"default/{platform['slug']}",
            "display_name": platform_display_label(platform, "en_us"),
            "action": action_profile(platform),
            "feed": {
                "device": device,
                "runtime_mechanism": runtime_mechanism,
                "carrier_behavior": carrier_behaviour.get(device, "unknown"),
                "family": feed.get("magazine_family", "") if external or loading_device else "",
                "capacity": capacity,
                "reload_batch": reload_batch,
            },
            "ammunition": {
                "class": reference_ammunition_class(ammo),
                "nominal": nominal,
            },
            "manufacturing": {
                "profile": "curated_default",
                "tier": manufacturing_tier(platform),
            },
            "confidence": "curated",
            "evidence": [
                "bundled_default_industry_policy",
                "bundled_gun_feed" if feed_path.exists() else "bundled_gun_data",
            ],
        }
        if ammo:
            profile["ammunition"]["expected_ammo"] = ammo
        files[RESOURCE_ROOT / f"data/{namespace}/industry/reference/guns/{gun_path}.json"] = profile
    return files


def audited_feed_jam_clear_action(platform: dict[str, Any], policy: dict[str, Any]) -> str:
    """Return an explicitly audited clear action, never an inferred one."""
    return policy["audited_feed_jam_clear_actions"].get(platform["slug"], "none")


def validate_audited_feed_jam_clear_actions(platforms: list[dict[str, Any]], policy: dict[str, Any]) -> None:
    """Audit every built-in C.2 opt-in against the untouched bundled assets.

    A generated maintenance JSON is not sufficient evidence for a random feed
    jam. Each default opt-in must still have the actual manual server bolt type,
    a display state machine, and a real named bolt animation in the default gun
    pack. Third-party packs remain opt-in through their own explicit profile and
    the runtime re-check in ``IndustryMaintenanceService``.
    """
    by_slug = {platform["slug"]: platform for platform in platforms}
    for slug, clear_action in sorted(policy["audited_feed_jam_clear_actions"].items()):
        if clear_action != "bolt" or slug not in by_slug:
            raise ValueError(f"{DEFAULT_GUN_POLICY}: invalid audited feed-jam clear target {slug!r}")
        data_path = DEFAULT_GUN_DATA_ROOT / f"{slug}_data.json"
        display_path = DEFAULT_GUN_DISPLAY_ROOT / f"{slug}_display.json"
        animation_path = DEFAULT_GUN_ANIMATION_ROOT / f"{slug}.animation.json"
        if not data_path.exists() or not display_path.exists() or not animation_path.exists():
            raise ValueError(f"{DEFAULT_GUN_POLICY}: {slug} lacks bundled data/display/animation evidence for bolt clear")
        data = read_json5(data_path)
        display = read_json5(display_path)
        if not isinstance(data, dict) or data.get("bolt") != "manual_action":
            raise ValueError(f"{DEFAULT_GUN_POLICY}: {slug} is not a bundled manual_action gun")
        if not isinstance(display, dict) or not isinstance(display.get("animation"), str) \
                or not isinstance(display.get("state_machine"), str):
            raise ValueError(f"{DEFAULT_GUN_POLICY}: {slug} lacks a bundled animated bolt state-machine contract")
        if not re.search(r'"bolt"\s*:', animation_path.read_text(encoding="utf-8")):
            raise ValueError(f"{DEFAULT_GUN_POLICY}: {slug} lacks a named bundled bolt animation")


def maintenance_baseline(platform: dict[str, Any], policy: dict[str, Any]) -> dict[str, Any]:
    """Phase-A accounting values plus explicitly audited C.2 opt-ins.

    Condition/Fouling always remains server-side bookkeeping. Random feed
    faults stay disabled unless this platform is listed in the separate audited
    clear-action manifest; the runtime still verifies the live manual-bolt type
    before a listed profile can affect shooting.
    """
    # 10,000 is a normalized internal condition scale. Structural replacement
    # is deliberately a thousands-of-round lifecycle, not a hundred-shot game
    # durability bar. Fouling can still require ordinary service sooner; the
    # grade and expected barrel interval are visible to the player and fully
    # data-overridable per GunId.
    by_tier = {
        "legacy": ({"receiver": 1, "bolt": 2, "barrel": 2, "trigger": 1, "recoil": 1}, 12, "field", 5_000),
        "service": ({"receiver": 1, "bolt": 1, "barrel": 1, "trigger": 1, "recoil": 1}, 12, "service", 9_000),
        "advanced": ({"receiver": 1, "bolt": 1, "barrel": 1, "trigger": 1, "recoil": 1}, 10, "enhanced", 10_000),
        "precision": ({"receiver": 1, "bolt": 1, "barrel": 2, "trigger": 1, "recoil": 1}, 8, "precision", 5_000),
    }
    tier = manufacturing_tier(platform)
    wear, fouling, durability_grade, expected_barrel_shots = by_tier[tier]
    action = action_profile(platform)
    if action in {"belt_fed", "rotary"}:
        wear = {"receiver": 1, "bolt": 1, "barrel": 1, "trigger": 1, "recoil": 1}
        durability_grade, expected_barrel_shots = "heavy_duty", 9_000
    elif action in {"anti_material_bolt", "bolt_action"}:
        wear = {"receiver": 1, "bolt": 1, "barrel": 2, "trigger": 1, "recoil": 1}
        durability_grade, expected_barrel_shots = "precision", 5_000
    # Broad gameplay maintenance classes, not claims about real named weapons.
    # Each tuple is dry wear/fouling, wet wear/fouling, rain wear/fouling, then
    # dirt-contact wear/fouling. Rain only applies when the shooter is actually
    # exposed; immersion/wet-contact uses the stronger wet pair.
    operation_by_action = {
        "bolt_action": (0.80, 0.75, 1.20, 1.35, 1.05, 1.15, 1.10, 1.20),
        "anti_material_bolt": (1.05, 0.80, 1.25, 1.35, 1.10, 1.18, 1.10, 1.20),
        "break_action": (0.75, 0.65, 1.15, 1.25, 1.05, 1.12, 1.05, 1.15),
        "revolver": (0.90, 0.80, 1.20, 1.30, 1.05, 1.15, 1.10, 1.20),
        "belt_fed": (1.00, 1.30, 1.15, 1.55, 1.08, 1.28, 1.10, 1.35),
        "rotary": (1.00, 1.15, 1.15, 1.45, 1.08, 1.25, 1.15, 1.25),
        "gas_operated_shotgun": (1.10, 1.20, 1.30, 1.55, 1.12, 1.30, 1.15, 1.35),
        "blowback_smg": (1.00, 1.15, 1.25, 1.45, 1.10, 1.25, 1.15, 1.30),
    }
    multipliers = operation_by_action.get(action, (1.0, 1.0, 1.35, 1.75, 1.10, 1.25, 1.15, 1.45))
    # This is a maximum at full *real* GunHeatData. Guns without native heat
    # state stay exactly at 1.0 at runtime; the generator never invents heat.
    heat_stress = {
        "rotary": 1.75,
        "belt_fed": 1.55,
        "gas_operated_shotgun": 1.35,
        "blowback_smg": 1.25,
        "anti_material_bolt": 1.20,
        "bolt_action": 1.15,
        "break_action": 1.10,
        "revolver": 1.10,
    }.get(action, 1.20)
    # C.4 uses one safe post-shot fault per actual action family. Only the
    # short clear-action audit list can use feed: every other generated default
    # gun receives a bench-only service lockout rather than a fictional rack or
    # reload animation. Values are gameplay balancing data, not claims about
    # real firearm reliability.
    warning, critical, max_chance = {
        "bolt_action": (5_800, 1_400, 0.035),
        "anti_material_bolt": (6_000, 1_500, 0.040),
        "break_action": (6_500, 1_800, 0.040),
        "revolver": (6_300, 1_700, 0.038),
        "belt_fed": (7_000, 2_000, 0.055),
        "rotary": (7_000, 2_000, 0.060),
        "gas_operated_shotgun": (6_200, 1_600, 0.050),
        "blowback_smg": (6_000, 1_500, 0.038),
    }.get(action, (6_000, 1_500, 0.040))
    clear_action = audited_feed_jam_clear_action(platform, policy)
    fault_mode = "feed" if clear_action == "bolt" else "service_lockout"
    return {
        "schema_version": 1,
        "generated_by": "tacz_industry_generator",
        "eligibility": "industrial_assembly",
        "maintenance_class": action_profile(platform),
        "durability_grade": durability_grade,
        "expected_barrel_shots": expected_barrel_shots,
        "wear_per_shot": wear,
        "fouling_per_shot": fouling,
        "heat_stress_multiplier": heat_stress,
        "operation": {
            "wear_multiplier": multipliers[0],
            "fouling_multiplier": multipliers[1],
            "submerged_wear_multiplier": multipliers[2],
            "submerged_fouling_multiplier": multipliers[3],
            "rain_wear_multiplier": multipliers[4],
            "rain_fouling_multiplier": multipliers[5],
            "contaminant_wear_multiplier": multipliers[6],
            "contaminant_fouling_multiplier": multipliers[7],
        },
        "jam": {
            "warning_condition": warning,
            "critical_condition": critical,
            "max_chance": max_chance,
            "clear_action": clear_action,
            "fault_mode": fault_mode,
        },
    }


def generated_maintenance_profile_files(platforms: list[dict[str, Any]], policy: dict[str, Any]) -> dict[Path, Any]:
    """Emit maintenance profiles; C.2 remains disabled except for audited bolt clears."""
    files: dict[Path, Any] = {}
    for platform in platforms:
        namespace, gun_path = platform["gun_id"].split(":", 1)
        files[RESOURCE_ROOT / f"data/{namespace}/industry/maintenance/guns/{gun_path}.json"] = maintenance_baseline(platform, policy)
    return files


def obsolete_generated_maintenance_profile_files(expected: dict[Path, Any]) -> set[Path]:
    """Only remove generator-stamped defaults; optional pack maintenance data stays untouched."""
    root = RESOURCE_ROOT / "data/tacz/industry/maintenance/guns"
    if not root.exists():
        return set()
    stale: set[Path] = set()
    for path in root.rglob("*.json"):
        if path in expected:
            continue
        try:
            data = read_json(path)
        except (OSError, json.JSONDecodeError):
            continue
        if isinstance(data, dict) and data.get("generated_by") == "tacz_industry_generator":
            stale.add(path)
    return stale


def validate_generated_maintenance_profiles(platforms: list[dict[str, Any]], expected: dict[Path, Any],
                                            policy: dict[str, Any]) -> None:
    """Ensure every generated profile has an explicit safe C.2 clear policy."""
    paths: set[Path] = set()
    components = {"receiver", "bolt", "barrel", "trigger", "recoil"}
    for platform in platforms:
        namespace, gun_path = platform["gun_id"].split(":", 1)
        path = RESOURCE_ROOT / f"data/{namespace}/industry/maintenance/guns/{gun_path}.json"
        profile = expected.get(path)
        if not isinstance(profile, dict):
            raise ValueError(f"{platform['slug']}: missing generated maintenance profile")
        paths.add(path)
        wear = profile.get("wear_per_shot")
        operation = profile.get("operation")
        jam = profile.get("jam")
        operation_keys = {"wear_multiplier", "fouling_multiplier", "submerged_wear_multiplier",
                          "submerged_fouling_multiplier", "rain_wear_multiplier", "rain_fouling_multiplier",
                          "contaminant_wear_multiplier", "contaminant_fouling_multiplier"}
        if profile.get("schema_version") != 1 or profile.get("eligibility") != "industrial_assembly" \
                or profile.get("maintenance_class") != action_profile(platform) \
                or profile.get("durability_grade") not in {"field", "service", "enhanced", "precision", "heavy_duty"} \
                or not isinstance(profile.get("expected_barrel_shots"), int) or not 100 <= profile["expected_barrel_shots"] <= 100_000 \
                or not isinstance(wear, dict) or set(wear) != components \
                or not all(isinstance(value, int) and 0 <= value <= 1000 for value in wear.values()) \
                or not isinstance(profile.get("fouling_per_shot"), int) \
                or not isinstance(profile.get("heat_stress_multiplier"), (int, float)) \
                or not 1 <= float(profile["heat_stress_multiplier"]) <= 16 \
                or not isinstance(operation, dict) or set(operation) != operation_keys \
                or not all(isinstance(value, (int, float)) and 0 <= float(value) <= 16 for value in operation.values()) \
                or not isinstance(jam, dict) \
                or set(jam) != {"warning_condition", "critical_condition", "max_chance", "clear_action", "fault_mode"} \
                or not isinstance(jam.get("warning_condition"), int) or not 0 <= jam["warning_condition"] <= 10_000 \
                or not isinstance(jam.get("critical_condition"), int) or not 0 <= jam["critical_condition"] <= jam["warning_condition"] \
                or not isinstance(jam.get("max_chance"), (int, float)) or not 0 <= float(jam["max_chance"]) <= 1 \
                or jam.get("clear_action") != audited_feed_jam_clear_action(platform, policy) \
                or jam.get("fault_mode") != ("feed" if audited_feed_jam_clear_action(platform, policy) == "bolt" else "service_lockout"):
            raise ValueError(f"{platform['slug']}: malformed generated maintenance profile")
    if len(paths) != len(platforms):
        raise ValueError("generated maintenance profile path collision")


def validate_initial_maintenance_assembly_outputs(platforms: list[dict[str, Any]], expected: dict[Path, Any]) -> None:
    """Every real default final-gun result must begin phase A full and clean."""
    expected_state = initial_maintenance_state()
    for platform in platforms:
        path = RESOURCE_ROOT / f"data/tacz/recipe/create/industry/assemble_{platform['slug']}.json"
        recipe = expected.get(path)
        if not isinstance(recipe, dict):
            raise ValueError(f"{platform['slug']}: missing generated final assembly for maintenance state")
        custom = recipe.get("result", {}).get("components", {}).get("minecraft:custom_data", {})
        if not isinstance(custom, dict) or any(custom.get(key) != value for key, value in expected_state.items()):
            raise ValueError(f"{platform['slug']}: final assembly lacks full/clean initial maintenance state")


def service_component_tag(platform: dict[str, Any], kind: str, condition_slot: int, condition: int = 10000) -> dict[str, Any]:
    return {
        "IndustryPlatform": platform["platform"],
        "IndustryPartKind": kind,
        "IndustryDisplayName": f"item.tacz.gun_component.service_{kind}",
        "IndustryActionProfile": action_profile(platform),
        "IndustryToolingScope": tooling_scope(platform),
        "IndustryPartCondition": condition,
        "IndustryServiceConditionSlot": condition_slot,
        "IndustryServiceGunId": platform["gun_id"],
        "IndustryServiceOrigin": platform["platform"],
        "IndustryServiceRecipe": f"tacz:gun/{platform['slug']}",
        "IndustryServiceTier": manufacturing_tier(platform),
        "IndustryServiceAction": action_profile(platform),
        "IndustryServiceToolingScope": tooling_scope(platform),
        "IndustryServiceShots": 0,
    }


def service_part_tag(platform: dict[str, Any], kind: str) -> dict[str, Any]:
    return {
        "IndustryPlatform": platform["platform"],
        "IndustryPartKind": f"service_part_{kind}",
        "IndustryDisplayName": f"item.tacz.service_part.{platform['slug']}_{kind}",
        "IndustryActionProfile": action_profile(platform),
        "IndustryToolingScope": tooling_scope(platform),
        "IndustryServiceGunId": platform["gun_id"],
        "IndustryServiceOrigin": platform["platform"],
    }


def service_fixture_tag(platform: dict[str, Any]) -> dict[str, Any]:
    scope = tooling_scope(platform)
    action = action_profile(platform)
    if scope in {"family_jig", "platform_tooling"}:
        return {
            "IndustryPlatform": "tooling",
            "IndustryPartKind": "action_jig",
            "IndustryActionProfile": action,
            "DieTargetKind": action,
        }
    kind = "critical_gauge" if scope == "critical_gauge" else "acceptance_gauge"
    return {
        "IndustryPlatform": platform["platform"],
        "IndustryPartKind": kind,
        "IndustryActionProfile": action,
        "IndustryToolingScope": scope,
        "DieTargetKind": action,
    }


def generated_service_repair_files(platforms: list[dict[str, Any]]) -> dict[Path, Any]:
    """Actual Create one-workpiece component replacement routes for B.2."""
    files: dict[Path, Any] = {}
    blank_tag = {
        "IndustryPlatform": "service",
        "IndustryPartKind": "service_part_blank",
        "IndustryDisplayName": "item.tacz.service_part_blank",
    }
    files[RESOURCE_ROOT / "data/tacz/recipe/create/industry/form_service_part_blank.json"] = {
        "fabric:load_conditions": CREATE_CONDITIONS,
        "type": "create:compacting",
        "ingredients": ["tacz:high_carbon_steel_ingot", "tacz:high_carbon_steel_plate", "create:brass_nugget"],
        "heat_requirement": "heated",
        "results": [output("tacz:service_part_blank", blank_tag)],
    }
    for platform in platforms:
        blueprint = partial("tacz:gun_blueprint", production_blueprint_tag(platform))
        fixture = partial("tacz:press_die", service_fixture_tag(platform))
        for condition_slot, part in enumerate(platform["parts"]):
            kind = part["kind"]
            part_tag = service_part_tag(platform, kind)
            part_tag["IndustryServiceConditionSlot"] = condition_slot
            # Old B.2 replacement parts predate the explicit slot field. The
            # repair match deliberately keys on GunId/platform/kind instead, so
            # updating a world never strands already-made replacement parts.
            repair_part_match = dict(part_tag)
            repair_part_match.pop("IndustryServiceConditionSlot", None)
            component_tag = service_component_tag(platform, kind, condition_slot)
            component_die = partial("tacz:press_die", {
                "IndustryPlatform": platform["platform"],
                "IndustryPartKind": "component_die",
                "DieTargetKind": kind,
            })
            # Direct stations instead of opaque sequenced assembly: each stage
            # has one visible belt/depot workpiece and one held input. This lets
            # a player diagnose an incorrect die/template/part at the exact
            # station instead of seeing a whole sequence silently refuse.
            die_formed_part = {
                "IndustryPlatform": platform["platform"],
                "IndustryPartKind": f"service_part_die_{kind}",
                "IndustryDisplayName": "item.tacz.service_part_blank",
                "IndustryServiceGunId": platform["gun_id"],
            }
            files[RESOURCE_ROOT / f"data/tacz/recipe/create/industry/form_service_part_{platform['slug']}_{kind}.json"] = deploying(
                partial("tacz:service_part_blank", blank_tag), component_die,
                output("tacz:service_part_blank", die_formed_part), keep=True
            )
            files[RESOURCE_ROOT / f"data/tacz/recipe/create/industry/calibrate_service_part_{platform['slug']}_{kind}.json"] = deploying(
                partial("tacz:service_part_blank", die_formed_part), blueprint,
                output("tacz:service_part", part_tag), keep=True
            )

            damaged_component = {
                "IndustryPlatform": platform["platform"],
                "IndustryPartKind": kind,
                "IndustryServiceGunId": platform["gun_id"],
            }
            replacement_fitted = {
                "IndustryPlatform": platform["platform"],
                "IndustryPartKind": f"service_part_fitted_{platform['slug']}_{kind}",
                "IndustryDisplayName": f"item.tacz.gun_component.service_{kind}",
                "IndustryServiceGunId": platform["gun_id"],
            }
            fixture_fitted = {
                "IndustryPlatform": platform["platform"],
                "IndustryPartKind": f"service_fixture_fitted_{platform['slug']}_{kind}",
                "IndustryDisplayName": f"item.tacz.gun_component.service_{kind}",
                "IndustryServiceGunId": platform["gun_id"],
            }
            files[RESOURCE_ROOT / f"data/tacz/recipe/create/industry/prepare_repair_component_{platform['slug']}_{kind}.json"] = deploying(
                partial("tacz:gun_component", damaged_component), partial("tacz:service_part", repair_part_match),
                output("tacz:gun_component", replacement_fitted), keep=False
            )
            files[RESOURCE_ROOT / f"data/tacz/recipe/create/industry/fixture_repair_component_{platform['slug']}_{kind}.json"] = deploying(
                partial("tacz:gun_component", replacement_fitted), fixture,
                output("tacz:gun_component", fixture_fitted), keep=True
            )
            files[RESOURCE_ROOT / f"data/tacz/recipe/create/industry/repair_component_{platform['slug']}_{kind}.json"] = {
                "fabric:load_conditions": CREATE_CONDITIONS,
                "type": "create:pressing",
                "ingredient": partial("tacz:gun_component", fixture_fitted),
                "results": [output("tacz:gun_component", component_tag)],
            }
    return files


def service_language_entries(platform: dict[str, Any], language: str) -> dict[str, str]:
    chinese = language == "zh_cn"
    label = platform_display_label(platform, language)
    entries: dict[str, str] = {}
    for part in platform["parts"]:
        kind = part["kind"]
        part_label = part["name_zh" if chinese else "name_en"]
        entries[f"item.tacz.service_part.{platform['slug']}_{kind}"] = (
            f"{part_label}维修替换件" if chinese else f"{part_label} Service Replacement Part"
        )
    return entries


def obsolete_generated_service_repair_files(expected: dict[Path, Any]) -> set[Path]:
    root = RESOURCE_ROOT / "data/tacz/recipe/create/industry"
    if not root.exists():
        return set()
    prefixes = ("calibrate_service_part_", "form_service_part_", "prepare_repair_component_",
                "fixture_repair_component_", "repair_component_")
    return {path for path in root.glob("*.json") if path not in expected and path.stem.startswith(prefixes)}


def validate_generated_service_repair_files(platforms: list[dict[str, Any]], expected: dict[Path, Any]) -> None:
    count = 0
    for platform in platforms:
        for part in platform["parts"]:
            kind = part["kind"]
            form = RESOURCE_ROOT / f"data/tacz/recipe/create/industry/form_service_part_{platform['slug']}_{kind}.json"
            calibrate = RESOURCE_ROOT / f"data/tacz/recipe/create/industry/calibrate_service_part_{platform['slug']}_{kind}.json"
            prepare = RESOURCE_ROOT / f"data/tacz/recipe/create/industry/prepare_repair_component_{platform['slug']}_{kind}.json"
            fixture = RESOURCE_ROOT / f"data/tacz/recipe/create/industry/fixture_repair_component_{platform['slug']}_{kind}.json"
            repair = RESOURCE_ROOT / f"data/tacz/recipe/create/industry/repair_component_{platform['slug']}_{kind}.json"
            if any(path not in expected for path in (form, calibrate, prepare, fixture, repair)):
                raise ValueError(f"{platform['slug']}: missing direct service repair station for {kind}")
            if expected[form].get("type") != "create:deploying" or expected[calibrate].get("type") != "create:deploying" \
                    or expected[prepare].get("type") != "create:deploying" or expected[fixture].get("type") != "create:deploying" \
                    or expected[repair].get("type") != "create:pressing":
                raise ValueError(f"{platform['slug']}: invalid direct service repair route for {kind}")
            count += 1
    if count != len(platforms) * len(BLANK_CLASS_ORDER):
        raise ValueError("service repair route count mismatch")


def obsolete_generated_reference_profile_files(expected: dict[Path, Any]) -> set[Path]:
    """Only clean generator-stamped default profiles; preserve authored compatibility data."""
    root = RESOURCE_ROOT / "data/tacz/industry/reference/guns"
    if not root.exists():
        return set()
    stale: set[Path] = set()
    for path in root.rglob("*.json"):
        if path in expected:
            continue
        try:
            data = read_json(path)
        except (OSError, json.JSONDecodeError):
            continue
        if isinstance(data, dict) and data.get("generated_by") == "tacz_industry_generator":
            stale.add(path)
    return stale



def validate_generated_reference_profiles(platforms: list[dict[str, Any]], expected: dict[Path, Any]) -> None:
    """Keep the generated default reference table complete and fact-linked to feeds/data."""
    profile_paths: set[Path] = set()
    for platform in platforms:
        namespace, gun_path = platform["gun_id"].split(":", 1)
        path = RESOURCE_ROOT / f"data/{namespace}/industry/reference/guns/{gun_path}.json"
        profile = expected.get(path)
        if not isinstance(profile, dict):
            raise ValueError(f"{platform['slug']}: missing generated industry reference profile")
        profile_paths.add(path)
        if profile.get("schema_version") != 1 or profile.get("action") != action_profile(platform) \
                or profile.get("manufacturing", {}).get("tier") != manufacturing_tier(platform):
            raise ValueError(f"{platform['slug']}: generated reference profile action/tier mismatch")
        feed = profile.get("feed", {})
        ammunition = profile.get("ammunition", {})
        if not isinstance(feed, dict) or not isinstance(ammunition, dict):
            raise ValueError(f"{platform['slug']}: generated reference profile needs feed/ammunition objects")
        feed_path = RESOURCE_ROOT / f"data/tacz/industry/gun_feed/{platform['slug']}.json"
        if feed_path.exists():
            declared = read_json(feed_path)
            if feed.get("runtime_mechanism") != declared.get("mechanism") \
                    or ammunition.get("expected_ammo") != declared.get("ammo") \
                    or feed.get("capacity") != declared.get("magazine_capacity"):
                raise ValueError(f"{platform['slug']}: generated reference profile disagrees with gun_feed")
    if len(profile_paths) != len(platforms):
        raise ValueError("generated reference profile path collision")


def action_tooling_language_entries(platforms: list[dict[str, Any]], policy: dict[str, Any], language: str) -> dict[str, str]:
    """Names for reusable family fixtures and selected gauge stocks."""
    chinese = language == "zh_cn"
    entries: dict[str, str] = {}
    profiles = sorted({action_profile(platform) for platform in platforms})
    for profile_id in profiles:
        profile = policy["process_profiles"][profile_id]
        label = profile["label_zh" if chinese else "label_en"]
        entries[f"item.tacz.press_die.action_jig.{profile_id}"] = (
            f"{label}动作夹具" if chinese else f"{label} Action Fixture"
        )
        entries[f"tooltip.tacz.industry.action_profile.{profile_id}"] = label
    for profile_id, scope in sorted({(action_profile(platform), tooling_scope(platform)) for platform in platforms
                                     if tooling_scope(platform) in {"critical_gauge", "final_acceptance"}}):
        label = policy["process_profiles"][profile_id]["label_zh" if chinese else "label_en"]
        gauge_kind = final_gauge_kind(scope)
        entries[f"item.tacz.press_die.{gauge_kind}_blank.{profile_id}"] = (
            f"{label}{'关键配合量规毛坯' if scope == 'critical_gauge' else '最终验收检具毛坯'}"
            if chinese else f"{label} {'Critical Fit Gauge Blank' if scope == 'critical_gauge' else 'Acceptance Gauge Blank'}"
        )
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
    "feed_device_kind",
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
        # Every cartridge family now has one reusable datum gauge. Five
        # no-firearm calibres retain supplied exact art; the remaining sampled
        # calibres intentionally use the declared gauge visual family until
        # dedicated artwork is supplied.
        identities.append(build_icon_identity(
            "cartridge_gauge", f"cartridge_gauge:{caliber}", "tacz:press_die",
            {"industry_part_kind": "cartridge_gauge", "cartridge_caliber": caliber},
            cartridge["gauge_name_en"], ("exact", "family"), f"cartridge_gauge:{caliber}", "family"
        ))
        identities.append(build_icon_identity(
            "cartridge_reverse_gauge", f"case_gauge:{caliber}", "tacz:press_die",
            {"industry_part_kind": "case_datum_gauge", "cartridge_caliber": caliber},
            cartridge["case_gauge_name_en"], ("family",), f"case_gauge:{caliber}", "family"
        ))
        identities.append(build_icon_identity(
            "cartridge_reverse_gauge", f"projectile_gauge:{caliber}:{projectile_type}", "tacz:press_die",
            {
                "industry_part_kind": "projectile_datum_gauge",
                "cartridge_caliber": caliber,
                "projectile_type": projectile_type,
            },
            cartridge["projectile_gauge_name_en"], ("family",),
            f"projectile_gauge:{caliber}:{projectile_type}", "family"
        ))

    # Shared physical blanks are real stacks and need listed visual identities
    # even though their eventual calibre/platform is intentionally not known yet.
    for kind, item, label in (
        ("case_blank", "tacz:cartridge_case_blank", "Neutral Cartridge Case Blank"),
        ("projectile_blank", "tacz:projectile_blank", "Neutral Projectile Blank"),
        ("case_die_blank", "tacz:press_die", "Blank Case Die"),
        ("projectile_die_blank", "tacz:press_die", "Blank Projectile Die"),
        ("cartridge_gauge_blank", "tacz:press_die", "Neutral Cartridge Datum Gauge Blank"),
    ):
        accepted = ("exact", "family") if kind == "cartridge_gauge_blank" else ("exact",)
        identities.append(build_icon_identity(
            "shared_ammunition_intermediate", f"ammunition:{kind}", item,
            {"industry_platform": "ammunition", "industry_part_kind": kind}, label,
            accepted, f"ammunition:{kind}", "family" if kind == "cartridge_gauge_blank" else "individual"
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

    for tier_id in MANUFACTURING_TIER_IDS:
        identities.append(build_icon_identity(
            "dossier_archive", f"dossier_archive:{tier_id}", "tacz:gun_component_blank",
            {"industry_platform": "archive", "industry_part_kind": f"dossier_archive_{tier_id}"},
            f"{tier_id} Dossier Archive", ("family",), f"dossier_archive:{tier_id}", "family"
        ))

    # A template blank is a real one-slot workpiece. Its visual family is
    # intentionally shared with the blueprint medium rather than pretending it
    # is a finished platform dossier.
    identities.append(build_icon_identity(
        "tooling_template_blank", "tooling:template_blank", "tacz:gun_blueprint",
        {"industry_platform": "tooling", "industry_part_kind": "template_blank"},
        "Blank Tooling Sheet", ("family",), "tooling:template_blank", "family"
    ))
    identities.append(build_icon_identity(
        "action_fixture", "tooling:acceptance_gauge_stock", "tacz:press_die",
        {"industry_platform": "tooling", "industry_part_kind": "acceptance_gauge_stock"},
        "Precision Acceptance Gauge Stock", ("family",), "tooling:acceptance_gauge_stock", "family"
    ))
    for profile_id in sorted({action_profile(platform) for platform in platforms}):
        identities.append(build_icon_identity(
            "action_fixture", f"action_jig:{profile_id}", "tacz:press_die",
            {"industry_platform": "tooling", "industry_part_kind": "action_jig", "die_target_kind": profile_id},
            f"{profile_id} Action Fixture", ("family",), f"action_jig:{profile_id}", "family"
        ))
    for profile_id, scope in sorted({(action_profile(platform), tooling_scope(platform)) for platform in platforms
                                     if tooling_scope(platform) in {"critical_gauge", "final_acceptance"}}):
        kind = final_gauge_kind(scope)
        identities.append(build_icon_identity(
            "action_fixture", f"{kind}_blank:{profile_id}", "tacz:press_die",
            {"industry_platform": "tooling", "industry_part_kind": f"{kind}_blank", "die_target_kind": profile_id},
            f"{profile_id} {kind} Blank", ("family",), f"{kind}_blank:{profile_id}", "family"
        ))

    for platform in sorted(platforms, key=lambda value: value["platform"]):
        platform_id = platform["platform"]
        blueprint = platform["blueprint"]
        identities.append(build_icon_identity(
            "platform_blueprint", f"blueprint:{platform_id}", "tacz:gun_blueprint",
            {"industry_platform": platform_id, "industry_part_kind": "blueprint"},
            blueprint["name_en"], ("exact",), f"blueprint:{platform_id}"
        ))
        scope = tooling_scope(platform)
        if scope in {"critical_gauge", "final_acceptance"}:
            kind = final_gauge_kind(scope)
            identities.append(build_icon_identity(
                "platform_acceptance_tool", f"{kind}:{platform_id}", "tacz:press_die",
                {"industry_platform": platform_id, "industry_part_kind": kind},
                f"{platform_display_label(platform, 'en_us')} {kind}", ("family",),
                f"{kind}:{platform_id}", "family"
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

    # Removable carriers now have their own industrial tooling and two named
    # subassemblies. The supplied complete pack has no exact art for these new
    # identities, so they deliberately use declared family visuals instead of
    # pretending that a generic component PNG is exact carrier-specific art.
    magazine_carriers = load_magazine_carriers({cartridge["ammo"] for cartridge in cartridges})
    identities.append(build_icon_identity(
        "carrier_tooling", "carrier:gauge_blank", "tacz:press_die",
        {"industry_platform": "feeding", "industry_part_kind": "carrier_gauge_blank"},
        "Neutral Carrier Specification Gauge Blank", ("family",), "carrier:gauge_blank", "family"
    ))
    identities.append(build_icon_identity(
        "carrier_component", "carrier:feed_kit_blank", "tacz:gun_component_blank",
        {"industry_platform": "feeding", "industry_part_kind": "carrier_feed_kit_blank"},
        "Neutral Carrier Feed-Kit Blank", ("family",), "carrier:feed_kit_blank", "family"
    ))
    for carrier in magazine_carriers:
        carrier_id = carrier["id"]
        selectors = {"industry_platform": "feeding", "die_target_kind": carrier_id}
        identities.append(build_icon_identity(
            "carrier_tooling", f"carrier_gauge:{carrier_id}", "tacz:press_die",
            {**selectors, "industry_part_kind": "carrier_gauge"},
            f"{carrier['name_en']} Specification Gauge", ("family",),
            f"carrier_gauge:{carrier_id}", "family"
        ))
        identities.append(build_icon_identity(
            "carrier_component", f"carrier_body:{carrier_id}", "tacz:gun_component",
            {**selectors, "industry_part_kind": "carrier_body"},
            f"{carrier['name_en']} Body", ("family",),
            f"carrier_body:{carrier_id}", "family"
        ))
        identities.append(build_icon_identity(
            "carrier_component", f"carrier_feed_kit:{carrier_id}", "tacz:gun_component",
            {**selectors, "industry_part_kind": "carrier_feed_kit"},
            f"{carrier['name_en']} {carrier['_profile']['feed_name_en']}", ("family",),
            f"carrier_feed_kit:{carrier_id}", "family"
        ))

    # Runtime-generated third-party surveyed operations use stable generic
    # identities. Individual GunId variants are intentionally not claimed as
    # exact art: their unique platform NBT is visible in tooltips/recipes while
    # one declared family visual represents the honest generic kit.
    identities.append(build_icon_identity(
        "surveying_tooling", "survey:archive", "tacz:gun_component_blank",
        {"industry_platform": "surveying", "industry_part_kind": "survey_archive"},
        "Survey Archive Packet", ("family",), "survey:archive", "family"
    ))
    identities.append(build_icon_identity(
        "surveying_tooling", "survey:fixture", "tacz:press_die",
        {"industry_platform": "surveying", "industry_part_kind": "survey_fixture"},
        "Survey Reference Fixture", ("family",), "survey:fixture", "family"
    ))
    identities.append(build_icon_identity(
        "surveying_component", "survey:platform_kit", "tacz:gun_component",
        {"industry_part_kind": "surveyed_platform_kit"},
        "Surveyed Platform Structural Kit", ("family",), "survey:platform_kit", "family"
    ))
    identities.append(build_icon_identity(
        "surveying_ammunition", "survey:cartridge_gauge", "tacz:press_die",
        {"industry_part_kind": "survey_cartridge_gauge"},
        "Surveyed Cartridge Datum Gauge", ("family",), "survey:cartridge_gauge", "family"
    ))
    identities.append(build_icon_identity(
        "surveying_ammunition", "survey:case", "tacz:cartridge_case",
        {"industry_platform": "ammunition", "industry_part_kind": "case"},
        "Surveyed-Calibre Cartridge Case", ("family",), "survey:case", "family"
    ))
    identities.append(build_icon_identity(
        "surveying_ammunition", "survey:spent_case", "tacz:cartridge_case",
        {"industry_platform": "ammunition", "industry_part_kind": "spent_case"},
        "Fired Surveyed-Calibre Cartridge Case", ("family",), "survey:spent_case", "family"
    ))
    identities.append(build_icon_identity(
        "surveying_ammunition", "survey:projectile", "tacz:projectile_core",
        {"industry_platform": "ammunition", "industry_part_kind": "projectile", "projectile_type": "surveyed"},
        "Surveyed-Calibre Projectile Core", ("family",), "survey:projectile", "family"
    ))
    for device_kind, label, texture_key in (
        ("stripper_clip", "Stripper Clip Feed Device", "stripper_clip"),
        ("speedloader", "Speedloader Feed Device", "speedloader"),
    ):
        identities.append(build_icon_identity(
            "feed_device", f"feed_device:{device_kind}", "tacz:magazine",
            {"feed_device_kind": device_kind}, label, ("family",),
            f"feed_device:{texture_key}", "family"
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

def _create_input_signature(recipe: dict[str, Any]) -> str | None:
    """Canonicalize the real input side of the three Create recipe kinds we emit."""
    recipe_type = recipe.get("type")
    if recipe_type == "create:compacting":
        ingredients = recipe.get("ingredients")
        if not isinstance(ingredients, list):
            return None
        return canonical((recipe_type, sorted(canonical(value) for value in ingredients)))
    if recipe_type == "create:mechanical_crafting":
        key = recipe.get("key")
        pattern = recipe.get("pattern")
        if not isinstance(key, dict) or not isinstance(pattern, list):
            return None
        grid: list[list[str]] = []
        for row in pattern:
            if not isinstance(row, str):
                return None
            grid.append([" " if symbol == " " else canonical(key.get(symbol)) for symbol in row])
        return canonical((recipe_type, grid))
    if recipe_type == "create:deploying":
        if "target" not in recipe or "ingredient" not in recipe:
            return None
        return canonical((recipe_type, recipe["target"], recipe["ingredient"]))
    return None


def _create_output_signature(recipe: dict[str, Any]) -> str | None:
    if "results" in recipe:
        return canonical(recipe["results"])
    if "result" in recipe:
        return canonical(recipe["result"])
    return None


def validate_effective_create_recipe_collisions(expected: dict[Path, Any], removed_paths: set[Path]) -> None:
    """Reject ambiguous Create processing in the final effective resource set.

    Basin inputs are compared as a multiset, Mechanical Crafter inputs as a
    physical grid, and Deployer input as its one target plus one held item. The
    validator starts with unmanaged checked-in industry recipes, overlays the
    generated output, and removes obsolete generated stems. Therefore it catches
    a collision between a new route and an older hand-authored route as well as
    a collision between two generated routes.
    """
    recipe_root = RESOURCE_ROOT / "data/tacz/recipe/create/industry"
    effective: dict[Path, Any] = {}
    if recipe_root.exists():
        for path in recipe_root.glob("*.json"):
            if path not in removed_paths:
                effective[path] = read_json(path)
    effective.update({
        path: value for path, value in expected.items()
        if recipe_root in path.parents and path not in removed_paths
    })

    seen: dict[str, tuple[Path, str]] = {}
    for path, recipe in effective.items():
        if not isinstance(recipe, dict):
            continue
        signature = _create_input_signature(recipe)
        output_signature = _create_output_signature(recipe)
        if signature is None or output_signature is None:
            continue
        old = seen.get(signature)
        if old is not None and old[1] != output_signature:
            raise ValueError(
                "Create recipe collision: "
                f"{old[0].relative_to(REPO)} and {path.relative_to(REPO)} have the same physical input but different outputs"
            )
        seen[signature] = (path, output_signature)


def validate_platform_tooling_semantics(platforms: list[dict[str, Any]], expected: dict[Path, Any]) -> None:
    """Keep template roles and terminal fixture requirements from silently regressing."""
    for platform in platforms:
        name = platform["platform"]
        scope = tooling_scope(platform)
        blueprint_path = RESOURCE_ROOT / f"data/tacz/recipe/create/industry/blueprint_{name}.json"
        blueprint_recipe = expected.get(blueprint_path)
        if not isinstance(blueprint_recipe, dict) or blueprint_recipe.get("type") != "create:deploying":
            raise ValueError(f"{name}: production template must come from a one-workpiece dossier transfer")
        target = blueprint_recipe.get("target", {})
        held = blueprint_recipe.get("ingredient", {})
        if target.get("nbt") != template_blank_tag() or held.get("nbt") != master_blueprint_tag(platform):
            raise ValueError(f"{name}: dossier transfer must be blank sheet + matching master dossier")
        for part in platform["parts"]:
            path = RESOURCE_ROOT / f"data/tacz/recipe/create/industry/calibrate_component_die_{name}_{part['structural']}.json"
            recipe = expected.get(path)
            if not isinstance(recipe, dict) or recipe.get("ingredient", {}).get("nbt") != production_blueprint_tag(platform):
                raise ValueError(f"{name}: component die {part['structural']} must require the production template")
        assembly_path = RESOURCE_ROOT / f"data/tacz/recipe/create/industry/assemble_{platform['slug']}.json"
        assembly = expected.get(assembly_path)
        if not isinstance(assembly, dict) or not isinstance(assembly.get("sequence"), list):
            raise ValueError(f"{name}: missing terminal sequence")
        held_tags = [step.get("ingredient", {}).get("nbt", {}) for step in assembly["sequence"]
                     if isinstance(step, dict) and isinstance(step.get("ingredient"), dict)]
        if len(assembly["sequence"]) > MAX_SEQUENCED_ASSEMBLY_STEPS:
            raise ValueError(f"{name}: terminal exceeds Create Fly's {MAX_SEQUENCED_ASSEMBLY_STEPS}-stage viewer limit")
        if any(tag.get("IndustryPartKind") == "blueprint" for tag in held_tags):
            raise ValueError(f"{name}: terminal assembly must not deploy a raw blueprint/template")
        kinds = {tag.get("IndustryPartKind") for tag in held_tags}
        if scope == "family_jig" and "action_jig" not in kinds:
            raise ValueError(f"{name}: family_jig terminal requires its reusable action fixture")
        expected_gauge = final_gauge_kind(scope)
        if expected_gauge:
            if expected_gauge not in kinds:
                raise ValueError(f"{name}: {scope} terminal requires a calibrated {expected_gauge}")
            selector_path = RESOURCE_ROOT / (
                f"data/tacz/recipe/create/industry/select_{expected_gauge}_blank_{action_profile(platform)}.json"
            )
            selector = expected.get(selector_path)
            if not isinstance(selector, dict) \
                    or selector.get("ingredient", {}).get("nbt") != action_jig_tag(action_profile(platform)):
                raise ValueError(f"{name}: {scope} gauge blank must be selected by its action fixture upstream")


def _recipe_result_custom_data(recipe: dict[str, Any]) -> dict[str, Any] | None:
    results = recipe.get("results")
    raw_result: Any
    if isinstance(results, list) and results and isinstance(results[0], dict):
        raw_result = results[0]
    else:
        raw_result = recipe.get("result")
    if not isinstance(raw_result, dict):
        return None
    components = raw_result.get("components")
    if not isinstance(components, dict):
        return None
    custom = components.get("minecraft:custom_data")
    return custom if isinstance(custom, dict) else None


def _viewer_identity(custom: dict[str, Any]) -> dict[str, Any]:
    """Identity shared by JEI/REI recipe lookup, excluding provenance-only fields."""
    return {
        key: value for key, value in custom.items()
        if key not in {"IndustryActionProfile", "IndustryToolingScope"}
    }


def validate_viewer_continuity(platforms: list[dict[str, Any]], expected: dict[Path, Any]) -> None:
    """Assert every visible die/component/gauge edge uses one viewer identity.

    Gameplay partial-NBT matching deliberately permits old stacks that lack
    action-profile provenance. Recipe viewers must use the same stable identity
    or a calibrated die looks like a different item from the die accepted by
    the forming recipe, breaking the visible production tree.
    """
    for platform in platforms:
        name = platform["platform"]
        assembly_path = RESOURCE_ROOT / f"data/tacz/recipe/create/industry/assemble_{platform['slug']}.json"
        assembly = expected.get(assembly_path)
        if not isinstance(assembly, dict):
            raise ValueError(f"{name}: missing assembly for viewer continuity")
        terminal_inputs: list[dict[str, Any]] = []
        outer_input = assembly.get("ingredient")
        if isinstance(outer_input, dict) and isinstance(outer_input.get("nbt"), dict):
            terminal_inputs.append(outer_input["nbt"])
        for step in assembly.get("sequence", []):
            if not isinstance(step, dict) or not isinstance(step.get("ingredient"), dict):
                continue
            nbt = step["ingredient"].get("nbt")
            if isinstance(nbt, dict):
                terminal_inputs.append(nbt)

        for part in platform["parts"]:
            structural = part["structural"]
            die_path = RESOURCE_ROOT / f"data/tacz/recipe/create/industry/calibrate_component_die_{name}_{structural}.json"
            form_path = RESOURCE_ROOT / f"data/tacz/recipe/create/industry/form_component_{name}_{structural}.json"
            die_recipe = expected.get(die_path)
            form_recipe = expected.get(form_path)
            if not isinstance(die_recipe, dict) or not isinstance(form_recipe, dict):
                raise ValueError(f"{name}: missing die/form route for {structural}")
            die_output = _recipe_result_custom_data(die_recipe)
            form_input = form_recipe.get("ingredient", {}).get("nbt")
            component_output = _recipe_result_custom_data(form_recipe)
            if not isinstance(die_output, dict) or not isinstance(form_input, dict) \
                    or _viewer_identity(die_output) != _viewer_identity(form_input):
                raise ValueError(f"{name}: viewer-disconnected component die → form edge for {structural}")
            if not isinstance(component_output, dict) or not any(
                    _viewer_identity(component_output) == _viewer_identity(input_tag)
                    for input_tag in terminal_inputs
            ):
                raise ValueError(f"{name}: viewer-disconnected component → terminal edge for {structural}")

        if platform["materials"]:
            furniture_recipe = expected.get(
                RESOURCE_ROOT / f"data/tacz/recipe/create/industry/calibrate_furniture_{name}.json"
            )
            furniture_output = _recipe_result_custom_data(furniture_recipe) if isinstance(furniture_recipe, dict) else None
            if not isinstance(furniture_output, dict) or not any(
                    _viewer_identity(furniture_output) == _viewer_identity(input_tag)
                    for input_tag in terminal_inputs
            ):
                raise ValueError(f"{name}: viewer-disconnected furniture kit → terminal edge")

        scope = tooling_scope(platform)
        if scope in {"critical_gauge", "final_acceptance"}:
            gauge_recipe = expected.get(RESOURCE_ROOT / f"data/tacz/recipe/create/industry/calibrate_{scope}_{name}.json")
            gauge_output = _recipe_result_custom_data(gauge_recipe) if isinstance(gauge_recipe, dict) else None
            if not isinstance(gauge_output, dict) or not any(
                    _viewer_identity(gauge_output) == _viewer_identity(input_tag)
                    for input_tag in terminal_inputs
            ):
                raise ValueError(f"{name}: viewer-disconnected platform gauge → terminal edge")


def _recipe_partial_nbt_values(value: Any) -> list[dict[str, Any]]:
    if isinstance(value, dict):
        found: list[dict[str, Any]] = []
        nbt = value.get("nbt")
        if isinstance(nbt, dict):
            found.append(nbt)
        for child in value.values():
            found.extend(_recipe_partial_nbt_values(child))
        return found
    if isinstance(value, list):
        return [tag for child in value for tag in _recipe_partial_nbt_values(child)]
    return []


def validate_cartridge_tooling_continuity(cartridges: list[dict[str, Any]], expected: dict[Path, Any]) -> None:
    """Keep every calibre datum → die → physical component edge viewer-continuous."""
    gauge_blank_path = RESOURCE_ROOT / "data/tacz/recipe/create/industry/press_die_cartridge_gauge_blank.json"
    gauge_blank_recipe = expected.get(gauge_blank_path)
    if not isinstance(gauge_blank_recipe, dict) \
            or _recipe_result_custom_data(gauge_blank_recipe) != cartridge_gauge_blank_tag():
        raise ValueError("Missing stable neutral cartridge gauge blank route")

    for caliber in cartridges:
        caliber_id = caliber["id"]
        gauge_tag = cartridge_gauge_tag(caliber)
        master_gun = caliber.get("master_gun")
        if isinstance(master_gun, str) and master_gun:
            gauge_recipe = expected.get(
                RESOURCE_ROOT / f"data/tacz/recipe/create/industry/calibrate_cartridge_gauge_{caliber_id}.json"
            )
            if not isinstance(gauge_recipe, dict) \
                    or gauge_recipe.get("target", {}).get("nbt") != cartridge_gauge_blank_tag() \
                    or _recipe_result_custom_data(gauge_recipe) != gauge_tag:
                raise ValueError(f"{caliber_id}: sample firearm must create its reusable datum gauge")
        else:
            gauge_recipe = expected.get(RESOURCE_ROOT / f"data/tacz/recipe/create/industry/caliber_gauge_{caliber_id}.json")
            if not isinstance(gauge_recipe, dict) or _recipe_result_custom_data(gauge_recipe) != gauge_tag:
                raise ValueError(f"{caliber_id}: declared no-gun datum must create its reusable gauge")

        calibration_outputs: dict[str, dict[str, Any]] = {}
        for die_kind, form_name in (("case", "form_case"), ("projectile", "form_projectile")):
            calibration = expected.get(
                RESOURCE_ROOT / f"data/tacz/recipe/create/industry/calibrate_{die_kind}_die_{caliber_id}.json"
            )
            form = expected.get(RESOURCE_ROOT / f"data/tacz/recipe/create/industry/{form_name}_{caliber_id}.json")
            die_output = _recipe_result_custom_data(calibration) if isinstance(calibration, dict) else None
            if not isinstance(calibration, dict) or calibration.get("ingredient", {}).get("nbt") != gauge_tag:
                raise ValueError(f"{caliber_id}: {die_kind} die must use the calibre datum gauge")
            if not isinstance(die_output, dict) or not isinstance(form, dict) or not any(
                    _viewer_identity(die_output) == _viewer_identity(tag)
                    for tag in _recipe_partial_nbt_values(form)
            ):
                raise ValueError(f"{caliber_id}: viewer-disconnected datum die → {form_name} edge")
            calibration_outputs[die_kind] = die_output

        case_gauge = case_datum_gauge_tag(caliber)
        projectile_gauge = projectile_datum_gauge_tag(caliber)
        case_form = expected.get(RESOURCE_ROOT / f"data/tacz/recipe/create/industry/form_case_{caliber_id}.json")
        projectile_form = expected.get(RESOURCE_ROOT / f"data/tacz/recipe/create/industry/form_projectile_{caliber_id}.json")
        case_output = _recipe_result_custom_data(case_form) if isinstance(case_form, dict) else None
        if caliber.get("motor_housing_name_en") and caliber.get("motor_housing_name_zh"):
            motor_housing = {
                "IndustryPlatform": "ammunition",
                "IndustryPartKind": "motor_housing",
                "IndustryDisplayName": f"item.tacz.cartridge_case.{caliber_id}.motor_housing",
                "CartridgeCaliber": caliber_id,
                "CartridgeAmmoId": caliber["ammo"],
            }
            finish_case = expected.get(RESOURCE_ROOT / f"data/tacz/recipe/create/industry/finish_case_{caliber_id}.json")
            final_case = {
                "IndustryPlatform": "ammunition",
                "IndustryPartKind": "case",
                "IndustryDisplayName": f"item.tacz.cartridge_case.{caliber_id}",
                "CartridgeCaliber": caliber_id,
                "CartridgeAmmoId": caliber["ammo"],
            }
            if not isinstance(case_form, dict) or case_output != motor_housing \
                    or not isinstance(finish_case, dict) \
                    or finish_case.get("target", {}).get("nbt") != motor_housing \
                    or _recipe_result_custom_data(finish_case) != final_case:
                raise ValueError(f"{caliber_id}: motor housing must be a persistent case → final motor route")
            case_output = final_case
        projectile_output = _recipe_result_custom_data(projectile_form) if isinstance(projectile_form, dict) else None
        case_reverse = expected.get(RESOURCE_ROOT / f"data/tacz/recipe/create/industry/reverse_case_gauge_{caliber_id}.json")
        projectile_reverse = expected.get(RESOURCE_ROOT / f"data/tacz/recipe/create/industry/reverse_projectile_gauge_{caliber_id}.json")
        ammo_reverse = expected.get(RESOURCE_ROOT / f"data/tacz/recipe/create/industry/reverse_cartridge_gauge_{caliber_id}.json")
        case_alternative = expected.get(
            RESOURCE_ROOT / f"data/tacz/recipe/create/industry/calibrate_case_die_from_case_gauge_{caliber_id}.json"
        )
        projectile_alternative = expected.get(
            RESOURCE_ROOT / f"data/tacz/recipe/create/industry/calibrate_projectile_die_from_projectile_gauge_{caliber_id}.json"
        )
        if not all(isinstance(recipe, dict) for recipe in (
                case_reverse, projectile_reverse, ammo_reverse, case_alternative, projectile_alternative
        )):
            raise ValueError(f"{caliber_id}: missing reverse-engineering gauge branch")
        if _recipe_result_custom_data(case_reverse) != case_gauge \
                or _recipe_result_custom_data(projectile_reverse) != projectile_gauge \
                or _recipe_result_custom_data(ammo_reverse) != gauge_tag:
            raise ValueError(f"{caliber_id}: reverse evidence does not produce the declared gauges")
        if case_alternative.get("ingredient", {}).get("nbt") != case_gauge \
                or _recipe_result_custom_data(case_alternative) != calibration_outputs["case"]:
            raise ValueError(f"{caliber_id}: case evidence gauge cannot recover the canonical case die")
        if projectile_alternative.get("ingredient", {}).get("nbt") != projectile_gauge \
                or _recipe_result_custom_data(projectile_alternative) != calibration_outputs["projectile"]:
            raise ValueError(f"{caliber_id}: projectile evidence gauge cannot recover the canonical projectile die")
        if not isinstance(case_output, dict) or not isinstance(projectile_output, dict):
            raise ValueError(f"{caliber_id}: reverse evidence lacks physical case/projectile source")
        if not any(_viewer_identity(case_output) == _viewer_identity(tag) for tag in _recipe_partial_nbt_values(case_reverse)):
            raise ValueError(f"{caliber_id}: case reverse branch is viewer-disconnected")
        if not any(_viewer_identity(projectile_output) == _viewer_identity(tag) for tag in _recipe_partial_nbt_values(projectile_reverse)):
            raise ValueError(f"{caliber_id}: projectile reverse branch is viewer-disconnected")
        if ammo_reverse.get("ingredient", {}).get("nbt") != {"AmmoId": caliber["ammo"]}:
            raise ValueError(f"{caliber_id}: complete-round reverse route must consume its exact loose ammo sample")
        if caliber["eject_case"]:
            spent_reverse = expected.get(
                RESOURCE_ROOT / f"data/tacz/recipe/create/industry/reverse_spent_case_gauge_{caliber_id}.json"
            )
            if not isinstance(spent_reverse, dict) or _recipe_result_custom_data(spent_reverse) != case_gauge:
                raise ValueError(f"{caliber_id}: spent-case reverse route is missing")


def validate_stable_dossier_commissions(platforms: list[dict[str, Any]], acquisition: dict[str, Any],
                                        expected: dict[Path, Any]) -> None:
    """Validate deterministic archive commissions without treating a GUI choice as a machine collision."""
    for tier_id in MANUFACTURING_TIER_IDS:
        archive_path = RESOURCE_ROOT / f"data/tacz/recipe/create/industry/form_dossier_archive_{tier_id}.json"
        archive_recipe = expected.get(archive_path)
        if not isinstance(archive_recipe, dict) \
                or _recipe_result_custom_data(archive_recipe) != dossier_archive_tag(tier_id, acquisition):
            raise ValueError(f"{tier_id}: missing stable dossier archive route")

    for platform in platforms:
        path = RESOURCE_ROOT / f"data/tacz/recipe/industry/dossier_commission_{platform['slug']}.json"
        recipe = expected.get(path)
        if not isinstance(recipe, dict) or recipe.get("type") != "tacz:gun_smith_table_crafting":
            raise ValueError(f"{platform['slug']}: missing dossier commission")
        result = recipe.get("result", {})
        item = result.get("item", {}) if isinstance(result, dict) else {}
        if not isinstance(result, dict) or result.get("type") != "custom" \
                or result.get("group") != "tacz:misc" \
                or not isinstance(item, dict) or item.get("item") != "tacz:gun_blueprint" \
                or item.get("nbt") != master_blueprint_tag(platform):
            raise ValueError(f"{platform['slug']}: dossier commission result is not the exact master dossier")
        materials = recipe.get("materials")
        if not isinstance(materials, list) or len(materials) != 3:
            raise ValueError(f"{platform['slug']}: dossier commission needs archive, blank sheet and action fixture")
        expected_materials = (
            (dossier_archive_tag(manufacturing_tier(platform), acquisition), True),
            (template_blank_tag(), True),
            (action_jig_tag(action_profile(platform)), False),
        )
        for material, (tag, consumes) in zip(materials, expected_materials):
            if not isinstance(material, dict) or material.get("consume") is not consumes \
                    or material.get("item", {}).get("nbt") != tag:
                raise ValueError(f"{platform['slug']}: dossier commission has invalid evidence material")



def _carrier_recipe_stack_count(recipe: dict[str, Any], stack: Any) -> int:
    """Count one exact workpiece/supply identity in a generated carrier route."""
    count = (1 if recipe.get("ingredient") == stack else 0) + (1 if recipe.get("target") == stack else 0)
    for step in recipe.get("sequence", []):
        if isinstance(step, dict) and step.get("ingredient") == stack:
            count += 1
    return count


def validate_magazine_tooling_continuity(carriers: list[dict[str, Any]], platforms: list[dict[str, Any]],
                                         expected: dict[Path, Any]) -> None:
    """Prove every removable carrier has a real template/reverse → components → final chain."""
    blank_recipe = expected.get(RESOURCE_ROOT / "data/tacz/recipe/create/industry/press_die_carrier_gauge_blank.json")
    feed_blank_recipe = expected.get(RESOURCE_ROOT / "data/tacz/recipe/create/industry/form_carrier_feed_kit_blank.json")
    shell_recipe = expected.get(RESOURCE_ROOT / "data/tacz/recipe/create/industry/magazine_blank.json")
    if not isinstance(blank_recipe, dict) or _recipe_result_custom_data(blank_recipe) != carrier_gauge_blank_tag():
        raise ValueError("Missing neutral carrier-gauge blank Basin route")
    if not isinstance(feed_blank_recipe, dict) \
            or _recipe_result_custom_data(feed_blank_recipe) != carrier_feed_kit_blank_tag():
        raise ValueError("Missing neutral carrier feed-kit blank Basin route")
    if not isinstance(shell_recipe, dict) or shell_recipe.get("type") != "create:compacting" \
            or shell_recipe.get("results") != [{"id": "tacz:magazine_blank"}]:
        raise ValueError("Missing neutral magazine shell Basin route")

    platform_by_slug = {platform["slug"]: platform for platform in platforms}
    for carrier in carriers:
        carrier_id = carrier["id"]
        gauge = carrier_gauge_tag(carrier)
        body = carrier_body_tag(carrier)
        feed_kit = carrier_feed_kit_tag(carrier)
        magazine = carrier_magazine_tag(carrier)
        profile = carrier["_profile"]

        for source_slug in carrier["source_guns"]:
            path = RESOURCE_ROOT / (
                f"data/tacz/recipe/create/industry/calibrate_carrier_gauge_{carrier_id}_{source_slug}.json"
            )
            recipe = expected.get(path)
            platform = platform_by_slug.get(source_slug)
            if not isinstance(recipe, dict) or platform is None \
                    or recipe.get("target", {}).get("nbt") != carrier_gauge_blank_tag() \
                    or recipe.get("ingredient", {}).get("nbt") != production_blueprint_tag(platform) \
                    or _recipe_result_custom_data(recipe) != gauge \
                    or not recipe.get("keep_held_item"):
                raise ValueError(f"{carrier_id}: source template {source_slug} cannot calibrate its retained gauge")

        reverse = expected.get(RESOURCE_ROOT / f"data/tacz/recipe/create/industry/reverse_carrier_gauge_{carrier_id}.json")
        if not isinstance(reverse, dict) \
                or reverse.get("target", {}).get("nbt") != carrier_gauge_blank_tag() \
                or reverse.get("ingredient", {}).get("nbt") != magazine \
                or _recipe_result_custom_data(reverse) != gauge \
                or reverse.get("keep_held_item"):
            raise ValueError(f"{carrier_id}: empty carrier reverse evidence must be destructive and exact")

        body_recipe = expected.get(RESOURCE_ROOT / f"data/tacz/recipe/create/industry/form_carrier_body_{carrier_id}.json")
        if not isinstance(body_recipe, dict) or _recipe_result_custom_data(body_recipe) != body \
                or _carrier_recipe_stack_count(body_recipe, "tacz:magazine_blank") != profile["body_blank_count"] \
                or not any(_viewer_identity(gauge) == _viewer_identity(tag)
                           for tag in _recipe_partial_nbt_values(body_recipe)):
            raise ValueError(f"{carrier_id}: body must consume the declared neutral shell mass and retained gauge")

        feed_recipe = expected.get(RESOURCE_ROOT / f"data/tacz/recipe/create/industry/form_carrier_feed_kit_{carrier_id}.json")
        feed_blank = partial("tacz:gun_component_blank", carrier_feed_kit_blank_tag())
        if not isinstance(feed_recipe, dict) or _recipe_result_custom_data(feed_recipe) != feed_kit \
                or _carrier_recipe_stack_count(feed_recipe, feed_blank) != profile["feed_kit_blank_count"] \
                or not any(_viewer_identity(gauge) == _viewer_identity(tag)
                           for tag in _recipe_partial_nbt_values(feed_recipe)):
            raise ValueError(f"{carrier_id}: feed kit must consume its declared neutral stock and retained gauge")

        for label, recipe in (("body", body_recipe), ("feed", feed_recipe)):
            if not isinstance(recipe, dict):
                continue
            if recipe.get("type") == "create:sequenced_assembly" \
                    and len(recipe.get("sequence", [])) > MAX_SEQUENCED_ASSEMBLY_STEPS:
                raise ValueError(f"{carrier_id}: {label} route exceeds Create Fly's native JEI stage limit")
            if "tacz:modern_kinetic_gun" in canonical(recipe):
                raise ValueError(f"{carrier_id}: carrier manufacturing must not use a complete gun as tooling")

        assembly = expected.get(RESOURCE_ROOT / f"data/tacz/recipe/create/industry/assemble_carrier_{carrier_id}.json")
        if not isinstance(assembly, dict) \
                or assembly.get("target", {}).get("nbt") != body \
                or assembly.get("ingredient", {}).get("nbt") != feed_kit \
                or _recipe_result_custom_data(assembly) != magazine \
                or assembly.get("keep_held_item"):
            raise ValueError(f"{carrier_id}: final carrier assembly must consume body + named feed kit")
        # The exact final stack is the reverse-evidence input, while the two
        # named components are the exact final assembly inputs in recipe viewers.
        if reverse.get("ingredient", {}).get("nbt") != magazine:
            raise ValueError(f"{carrier_id}: final carrier → reverse-gauge viewer edge is disconnected")


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
    policy = load_default_gun_policy()
    explicit_platforms = load_platforms(policy)
    auto_platforms = discover_default_platforms({platform["slug"] for platform in explicit_platforms}, policy)
    platforms = [*explicit_platforms, *auto_platforms]
    cartridges = load_cartridges()
    magazine_carriers = load_magazine_carriers({cartridge["ammo"] for cartridge in cartridges})
    machine_assets = load_machine_assets()
    blueprint_acquisition = load_blueprint_acquisition()
    expected: dict[Path, Any] = {}
    english: dict[str, str] = {
        "item.tacz.gun_component_blank.furniture": "Neutral Exterior / Furniture Blank",
        "item.tacz.service_part_blank": "Neutral Service Replacement Blank",
        "item.tacz.maintenance_cleaning_kit": "Industrial Cleaning Kit",
        "item.tacz.press_die_blank.cartridge_gauge": "Neutral Cartridge Datum Gauge Blank",
        "item.tacz.gun_blueprint.blank": "Blank Tooling Sheet",
        "item.tacz.press_die.acceptance_gauge_stock": "Precision Acceptance Gauge Stock",
        "tooltip.tacz.blueprint.role.blank": "Blank tooling sheet — transfer a dossier or measured pattern onto it",
        "tooltip.tacz.blueprint.role.master": "Master manufacturing dossier — source document; retained by the transfer station",
        "tooltip.tacz.blueprint.role.production": "Production tooling template — calibrates dies and gauges; retained by the tooling station",
        "tooltip.tacz.blueprint.role.legacy": "Legacy platform blueprint — retained compatibility tooling",
        "tooltip.tacz.industry.action_jig": "Action-family fixture (held by a Deployer; not consumed)",
        "tooltip.tacz.industry.critical_gauge": "Platform critical-fit gauge (held by a Deployer; not consumed)",
        "tooltip.tacz.industry.acceptance_gauge": "Platform final-acceptance gauge (held by a Deployer; not consumed)",
        "tooltip.tacz.industry.action_profile": "Action profile: %s",
        "tooltip.tacz.industry.tooling_scope": "Mandatory tooling: %s",
        "tooltip.tacz.industry.tooling_scope.family_jig": "Family fixture at final fit",
        "tooltip.tacz.industry.tooling_scope.critical_gauge": "Platform critical-fit gauge",
        "tooltip.tacz.industry.tooling_scope.platform_tooling": "Platform dies during tooling setup",
        "tooltip.tacz.industry.tooling_scope.final_acceptance": "Platform final-acceptance gauge",
        "tooltip.tacz.industry.cartridge_gauge_blank": "Neutral datum-gauge blank — calibrate it with a sample firearm or declared datum",
        "tooltip.tacz.industry.carrier_gauge_blank": "Neutral carrier-gauge stock — calibrate it with a production template or an empty carrier sample",
        "tooltip.tacz.industry.carrier_gauge": "Reusable carrier specification gauge — forms the matching body and feed component",
        "tooltip.tacz.industry.carrier_component": "Named removable-carrier subassembly — install it at the final carrier station",
        "tooltip.tacz.industry.carrier_spec": "Carrier specification: %s / %s / %s rounds",
        "commands.tacz.industry.unavailable": "§cTACZ industry reference data is not available on this side.",
        "commands.tacz.industry.invalid_id": "§cInvalid gun identifier.",
        "commands.tacz.industry.audit": "§bIndustry audit§r — gun: %s, ammo: %s, attachment: %s; direct: %s, alias: %s, unresolved: %s; curated: %s, surveyed: %s",
        "commands.tacz.industry.reference_missing": "§eNo industry reference profile is available for %s.",
        "commands.tacz.industry.reference_header": "§bIndustry reference§r %s — model: %s, confidence: %s",
        "commands.tacz.industry.reference_action": "Action: %s; manufacturing profile: %s; tier: %s",
        "commands.tacz.industry.reference_feed": "Feed: device=%s, runtime=%s, carrier=%s, family=%s, capacity=%s",
        "commands.tacz.industry.reference_ammo": "Ammunition: class=%s, nominal=%s, expected=%s",
        "commands.tacz.industry.reference_evidence": "Evidence: %s",
        "tooltip.tacz.industry.case_datum_gauge": "Reverse-engineered case datum — calibrates only the matching case die",
        "tooltip.tacz.industry.projectile_datum_gauge": "Reverse-engineered projectile datum — calibrates only the matching projectile die",
        "tooltip.tacz.industry.dossier_archive": "Tier archive packet — consumed by a Gunsmith dossier commission",
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
        "gui.tacz.industrial_service.disassemble": "Disassemble",
        "gui.tacz.industrial_service.reassemble": "Reassemble",
        "gui.tacz.industrial_service.repair": "Repair components",
        "gui.tacz.industrial_service.clean": "Clean fouling",
        "gui.tacz.industrial_service.disassemble_hint": "Strip a safe, empty industrial gun into five preserved components.",
        "gui.tacz.industrial_service.reassemble_hint": "Reassemble five matching components without directly restoring their condition.",
        "gui.tacz.industrial_service.repair_hint": "Use steel and brass stocks here to restore only damaged components; no mechanical power required.",
        "gui.tacz.industrial_service.clean_hint": "Clean a safe assembled industrial gun with matching tooling and Basin-made cleaning kits; component condition and fault state stay intact.",
        "gui.tacz.industrial_service.gun_input": "GUN IN",
        "gui.tacz.industrial_service.gun_output": "GUN OUT",
        "gui.tacz.industrial_service.tooling": "TEMPLATE / FIXTURE / WRENCH",
        "gui.tacz.industrial_service.components": "FIVE SERVICE COMPONENTS",
        "gui.tacz.industrial_service.materials": "REPAIR STOCK",
        "gui.tacz.industrial_service.cleaning_material": "CLEANER",
        "message.tacz.industrial_service.repair_materials": "Need %s high-carbon steel plate(s) and %s brass sheet(s).",
        "message.tacz.industrial_service.repair_success": "Repaired %s component(s); consumed %s steel plate(s) and %s brass sheet(s).",
        "message.tacz.industrial_service.components_already_serviceable": "All installed service components are already at full condition.",
        "message.tacz.industrial_service.gun_already_clean": "This safe industrial gun has no fouling to remove.",
        "message.tacz.industrial_service.cleaning_materials": "Need %s industrial cleaning kit(s).",
        "message.tacz.industrial_service.clean_success": "Removed %s fouling; consumed %s industrial cleaning kit(s).",
        "config.tacz.server.industry_maintenance_scope": "Industrial Maintenance Scope",
        "config.tacz.server.industry_maintenance_scope.desc": "Industrial maintenance records condition and fouling. INDUSTRIAL_ASSEMBLY safely limits it to real industrial-origin guns; ALL_GUNS migrates legacy guns full and clean. Feed faults require an explicit audited clear action; bench-only service faults require a real industrial service exit.",
        "config.tacz.server.industry_heat_stress_enabled": "Native heat maintenance stress",
        "config.tacz.server.industry_heat_stress_enabled.desc": "Use only a gun's real GunHeatData/HeatAmount as C.3 maintenance exposure; per-gun profiles still define the maximum stress.",
        "config.tacz.server.industry_heat_wear_scale": "Heat-derived wear scale",
        "config.tacz.server.industry_heat_fouling_scale": "Heat-derived fouling scale",
        "config.tacz.server.gun_experience_handling_enabled": "Gun proficiency handling bonuses",
        "config.tacz.server.gun_experience_handling_enabled.desc": "Enables capped per-physical-gun ADS, real projectile spread, and local recoil handling bonuses; never direct damage or maintenance bypasses.",
        "config.tacz.server.gun_experience_aim_time_reduction": "Maximum level-10 ADS-time reduction",
        "config.tacz.server.gun_experience_inaccuracy_reduction": "Maximum level-10 projectile-spread reduction",
        "config.tacz.server.gun_experience_recoil_reduction": "Maximum level-10 recoil-camera reduction",
        "tooltip.tacz.gun.proficiency_handling": "Proficiency handling: ADS %s faster · spread %s tighter · recoil %s lower",
        "toast.tacz.sub.handling_up": "This physical gun now handles more cleanly",
        "tooltip.tacz.maintenance.status": "Service: %s  |  Condition %s  |  Fouling %s",
        "tooltip.tacz.maintenance.good": "Good",
        "tooltip.tacz.maintenance.service": "Service Due",
        "tooltip.tacz.maintenance.repair": "Repair Required",
        "tooltip.tacz.maintenance.out_of_service": "Out of Service",
        "tooltip.tacz.maintenance.lockout": "Critical service lockout — repair components at the Industrial Service Bench",
        "tooltip.tacz.maintenance.service_fault": "Mechanical service fault — repair components at the Industrial Service Bench",
        "tooltip.tacz.maintenance.feed_jam": "Feed jam — press fire to cycle the action clear",
        "tooltip.tacz.maintenance.grade": "Maintenance grade: %s · planned barrel service: ~%s shots",
        "tooltip.tacz.maintenance.grade.field": "Field / legacy",
        "tooltip.tacz.maintenance.grade.service": "Service grade",
        "tooltip.tacz.maintenance.grade.enhanced": "Enhanced service grade",
        "tooltip.tacz.maintenance.grade.precision": "Precision barrel grade",
        "tooltip.tacz.maintenance.grade.heavy_duty": "Sustained-fire grade",
        "tooltip.tacz.service.component_condition": "Component condition: %s",
        "tooltip.tacz.service.component_gun": "Service identity: %s",
        "tooltip.tacz.service.component_repair": "Repair stations: damaged component + named part Deployer → fixture Deployer → Mechanical Press.",
        "tooltip.tacz.service.blank_step": "Step 1: form this neutral blank in a heated Basin, then use the matching component-die Deployer.",
        "tooltip.tacz.service.named_part_step": "Step 2: first replace the damaged component on a depot/belt, then run the fitted component through its fixture and press stations.",
        "tooltip.tacz.service.legacy_part": "Legacy B.2 replacement part — retained for world compatibility; use the Industrial Service Bench repair bays for new repairs.",
    }
    chinese: dict[str, str] = {
        "item.tacz.gun_component_blank.furniture": "中性外装套件毛坯",
        "item.tacz.service_part_blank": "中性维修替换件毛坯",
        "item.tacz.maintenance_cleaning_kit": "工业枪械清洁套件",
        "item.tacz.press_die_blank.cartridge_gauge": "中性弹药基准量规毛坯",
        "item.tacz.gun_blueprint.blank": "空白工装页",
        "item.tacz.press_die.acceptance_gauge_stock": "精密验收检具料坯",
        "tooltip.tacz.blueprint.role.blank": "空白工装页——用原始档案或实物测绘结果转印",
        "tooltip.tacz.blueprint.role.master": "原始工艺档案——作为转印来源，由工位持有且不消耗",
        "tooltip.tacz.blueprint.role.production": "生产工装模板——用于校准模具和量规，由工位持有且不消耗",
        "tooltip.tacz.blueprint.role.legacy": "旧版平台蓝图——可继续作为兼容工装使用",
        "tooltip.tacz.industry.action_jig": "动作族夹具（由部署器持有，不消耗）",
        "tooltip.tacz.industry.critical_gauge": "平台关键配合量规（由部署器持有，不消耗）",
        "tooltip.tacz.industry.acceptance_gauge": "平台最终验收检具（由部署器持有，不消耗）",
        "tooltip.tacz.industry.action_profile": "动作类型：%s",
        "tooltip.tacz.industry.tooling_scope": "强制工装：%s",
        "tooltip.tacz.industry.tooling_scope.family_jig": "最终配合使用动作族夹具",
        "tooltip.tacz.industry.tooling_scope.critical_gauge": "平台关键配合量规",
        "tooltip.tacz.industry.tooling_scope.platform_tooling": "建线时校准平台模具",
        "tooltip.tacz.industry.tooling_scope.final_acceptance": "平台最终验收检具",
        "tooltip.tacz.industry.cartridge_gauge_blank": "中性基准量规毛坯——用样枪或已声明基准校准",
        "tooltip.tacz.industry.carrier_gauge_blank": "中性供弹器量规料坯——用生产模板或空供弹器样本校准",
        "tooltip.tacz.industry.carrier_gauge": "可复用供弹器规格量规——成型对应壳体与供弹组件",
        "tooltip.tacz.industry.carrier_component": "命名的可拆卸供弹器子总成——在最终供弹器工位装配",
        "tooltip.tacz.industry.carrier_spec": "供弹器规格：%s / %s / %s 发",
        "commands.tacz.industry.unavailable": "§c当前端没有可用的 TACZ 工业参考数据。",
        "commands.tacz.industry.invalid_id": "§c无效的枪械标识符。",
        "commands.tacz.industry.audit": "§b工业审计§r — 枪：%s，弹药：%s，配件：%s；直接：%s，别名：%s，未解析：%s；已校验：%s，测绘候选：%s",
        "commands.tacz.industry.reference_missing": "§e%s 没有可用的工业参考档案。",
        "commands.tacz.industry.reference_header": "§b工业参考§r %s — 型号键：%s，置信度：%s",
        "commands.tacz.industry.reference_action": "动作：%s；制造档案：%s；层级：%s",
        "commands.tacz.industry.reference_feed": "供弹：设备=%s，运行时=%s，载具行为=%s，兼容族=%s，容量=%s",
        "commands.tacz.industry.reference_ammo": "弹药：类别=%s，标称=%s，预期=%s",
        "commands.tacz.industry.reference_evidence": "依据：%s",
        "tooltip.tacz.industry.case_datum_gauge": "逆向弹壳基准——只能校准对应弹壳模具",
        "tooltip.tacz.industry.projectile_datum_gauge": "逆向弹头基准——只能校准对应弹头模具",
        "tooltip.tacz.industry.dossier_archive": "层级档案包——在枪械工作台档案委托中消耗",
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
        "gui.tacz.industrial_service.disassemble": "拆解",
        "gui.tacz.industrial_service.reassemble": "复装",
        "gui.tacz.industrial_service.repair": "维修组件",
        "gui.tacz.industrial_service.clean": "清除污垢",
        "gui.tacz.industrial_service.disassemble_hint": "将安全、清空的工业枪拆为五个保真组件。",
        "gui.tacz.industrial_service.reassemble_hint": "复装五个匹配组件，不会直接恢复其枪况。",
        "gui.tacz.industrial_service.repair_hint": "在此消耗钢板和黄铜板恢复损坏组件；不需要机械动力。",
        "gui.tacz.industrial_service.clean_hint": "将安全、已清空的工业成枪与匹配工装、Basin 制成的清洁套件放入此处清污；不恢复组件枪况，也不解除故障。",
        "gui.tacz.industrial_service.gun_input": "枪械输入",
        "gui.tacz.industrial_service.gun_output": "枪械输出",
        "gui.tacz.industrial_service.tooling": "模板 / 检具 / 扳手",
        "gui.tacz.industrial_service.components": "五个勤务组件",
        "gui.tacz.industrial_service.materials": "维修材料",
        "gui.tacz.industrial_service.cleaning_material": "清洁剂",
        "message.tacz.industrial_service.repair_materials": "需要 %s 块高碳钢板和 %s 张黄铜板。",
        "message.tacz.industrial_service.repair_success": "已维修 %s 个组件；消耗 %s 块钢板和 %s 张黄铜板。",
        "message.tacz.industrial_service.components_already_serviceable": "所有勤务组件均已处于满枪况。",
        "message.tacz.industrial_service.gun_already_clean": "这把安全的工业枪没有可清除的污垢。",
        "message.tacz.industrial_service.cleaning_materials": "需要 %s 份工业枪械清洁套件。",
        "message.tacz.industrial_service.clean_success": "已清除 %s 点污垢；消耗 %s 份工业枪械清洁套件。",
        "config.tacz.server.industry_maintenance_scope": "工业维护范围",
        "config.tacz.server.industry_maintenance_scope.desc": "工业维护记录枪况和污垢。INDUSTRIAL_ASSEMBLY 仅作用于真实工业来源枪械；ALL_GUNS 会让旧枪以满状态、清洁状态安全迁移。供弹卡滞必须有显式、已审计的清障动作；勤务锁止必须有真实工业维修出口。",
        "config.tacz.server.industry_heat_stress_enabled": "原生过热维护应力",
        "config.tacz.server.industry_heat_stress_enabled.desc": "仅使用枪自身真实的 GunHeatData/HeatAmount 作为 C.3 维护暴露；每枪档案仍决定最大应力。",
        "config.tacz.server.industry_heat_wear_scale": "热量额外磨损倍率",
        "config.tacz.server.industry_heat_fouling_scale": "热量额外污垢倍率",
        "config.tacz.server.gun_experience_handling_enabled": "枪械熟练度操控收益",
        "config.tacz.server.gun_experience_handling_enabled.desc": "启用按实体枪计算、封顶的瞄准、真实弹道散布与本地后坐操控收益；绝不直接增加伤害或绕过维护。",
        "config.tacz.server.gun_experience_aim_time_reduction": "10 级最大瞄准时间缩短",
        "config.tacz.server.gun_experience_inaccuracy_reduction": "10 级最大真实散布缩减",
        "config.tacz.server.gun_experience_recoil_reduction": "10 级最大后坐镜头缩减",
        "tooltip.tacz.gun.proficiency_handling": "熟练操控：瞄准快 %s · 散布紧 %s · 后坐低 %s",
        "toast.tacz.sub.handling_up": "这把实体枪的操控更加熟练",
        "tooltip.tacz.maintenance.status": "勤务：%s  |  枪况 %s  |  污垢 %s",
        "tooltip.tacz.maintenance.good": "良好",
        "tooltip.tacz.maintenance.service": "需保养",
        "tooltip.tacz.maintenance.repair": "需维修",
        "tooltip.tacz.maintenance.out_of_service": "停用",
        "tooltip.tacz.maintenance.lockout": "临界勤务锁止——请在工业勤务台维修组件",
        "tooltip.tacz.maintenance.service_fault": "机械勤务故障——请在工业勤务台维修组件",
        "tooltip.tacz.maintenance.feed_jam": "供弹卡滞——按开火键执行拉栓清障",
        "tooltip.tacz.maintenance.grade": "耐久等级：%s · 预计枪管勤务：约 %s 发",
        "tooltip.tacz.maintenance.grade.field": "野战 / 旧制等级",
        "tooltip.tacz.maintenance.grade.service": "制式勤务等级",
        "tooltip.tacz.maintenance.grade.enhanced": "强化制式等级",
        "tooltip.tacz.maintenance.grade.precision": "精密枪管等级",
        "tooltip.tacz.maintenance.grade.heavy_duty": "持续火力等级",
        "tooltip.tacz.service.component_condition": "组件枪况：%s",
        "tooltip.tacz.service.component_gun": "勤务身份：%s",
        "tooltip.tacz.service.component_repair": "维修工位：损坏组件 + 命名替换件部署器 → 检具部署器 → 动力冲压机。",
        "tooltip.tacz.service.blank_step": "第 1 步：先在加热 Basin 制成该中性毛坯，再由对应组件模具部署器成型。",
        "tooltip.tacz.service.named_part_step": "第 2 步：先在置物台/传送带上由命名替换件部署器替换损坏组件，再依次经过检具与动力冲压工位。",
        "tooltip.tacz.service.legacy_part": "旧 B.2 替换件——为旧存档保留；新维修请使用工业勤务台的维修材料槽。",
    }
    expected.update(generated_furniture_blank_files(platforms))
    expected.update(generated_template_blank_file(policy))
    expected.update(generated_surveying_files())
    expected.update(generated_action_jig_files(platforms, policy))
    expected.update(generated_stable_dossier_files(platforms, blueprint_acquisition))
    english.update(surveying_language_entries("en_us"))
    chinese.update(surveying_language_entries("zh_cn"))
    english.update(action_tooling_language_entries(platforms, policy, "en_us"))
    english.update(stable_dossier_language_entries(blueprint_acquisition, "en_us"))
    chinese.update(stable_dossier_language_entries(blueprint_acquisition, "zh_cn"))
    chinese.update(action_tooling_language_entries(platforms, policy, "zh_cn"))
    for platform in platforms:
        expected.update(generated_platform_files(platform))
        english.update(language_entries(platform, "en_us"))
        chinese.update(language_entries(platform, "zh_cn"))
    expected.update(generated_reference_profile_files(platforms))
    validate_audited_feed_jam_clear_actions(platforms, policy)
    expected.update(generated_maintenance_profile_files(platforms, policy))
    expected.update(generated_cartridge_gauge_blank_file())
    for cartridge in cartridges:
        expected.update(generated_cartridge_files(cartridge))
        english.update(cartridge_language_entries(cartridge, "en_us"))
        chinese.update(cartridge_language_entries(cartridge, "zh_cn"))
    expected.update(generated_blueprint_acquisition_files(platforms, blueprint_acquisition))
    expected.update(generated_magazine_files(magazine_carriers, platforms))
    english.update(magazine_language_entries(magazine_carriers, "en_us"))
    chinese.update(magazine_language_entries(magazine_carriers, "zh_cn"))
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
    obsolete_platform_paths = obsolete_generated_platform_files(expected, platforms)
    obsolete_template_paths = obsolete_template_compatibility_files(expected)
    obsolete_acquisition_paths = obsolete_blueprint_acquisition_files(expected)
    obsolete_dossier_paths = obsolete_dossier_commission_files(expected)
    obsolete_magazine_paths = obsolete_legacy_magazine_files(magazine_carriers)
    obsolete_carrier_paths = obsolete_generated_carrier_files(expected)
    obsolete_reference_paths = obsolete_generated_reference_profile_files(expected)
    obsolete_maintenance_paths = obsolete_generated_maintenance_profile_files(expected)
    obsolete_service_repair_paths = obsolete_generated_service_repair_files(expected)
    validate_effective_create_recipe_collisions(
        expected, obsolete_platform_paths | obsolete_template_paths | obsolete_magazine_paths | obsolete_carrier_paths
    )
    validate_platform_tooling_semantics(platforms, expected)
    validate_viewer_continuity(platforms, expected)
    validate_cartridge_tooling_continuity(cartridges, expected)
    validate_magazine_tooling_continuity(magazine_carriers, platforms, expected)
    validate_generated_reference_profiles(platforms, expected)
    validate_generated_maintenance_profiles(platforms, expected, policy)
    validate_initial_maintenance_assembly_outputs(platforms, expected)
    validate_stable_dossier_commissions(platforms, blueprint_acquisition, expected)

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

    # Structural profile names can rename generator-owned component recipe
    # files; delete old stems so Create never sees duplicate deployments.
    for path in sorted(obsolete_platform_paths):
        stale.append(str(path.relative_to(REPO)))
        if write:
            path.unlink()

    # Generator-stamped default reference profiles are regenerated from the
    # current platform/feed policy. Hand-authored third-party compatibility
    # profiles are intentionally never deleted here.
    for path in sorted(obsolete_reference_paths):
        stale.append(str(path.relative_to(REPO)))
        if write:
            path.unlink()

    # Phase-A maintenance profiles are generated only for bundled default guns;
    # authored compatibility-pack maintenance data is never removed.
    for path in sorted(obsolete_maintenance_paths):
        stale.append(str(path.relative_to(REPO)))
        if write:
            path.unlink()

    # One named repair line exists for each default platform component; remove
    # only generator-owned stale variants when platform manifests change.
    for path in sorted(obsolete_service_repair_paths):
        stale.append(str(path.relative_to(REPO)))
        if write:
            path.unlink()

    # The first tooling-rework draft briefly emitted one legacy compatibility
    # calibration recipe per die. One explicit restore route is clearer and
    # preserves every old template without duplicate JEI/REI entries.
    for path in sorted(obsolete_template_paths):
        stale.append(str(path.relative_to(REPO)))
        if write:
            path.unlink()

    # If a carrier identity is retired/renamed, remove only the named generated
    # carrier route family so it cannot remain as a stale alternate output.
    for path in sorted(obsolete_carrier_paths):
        stale.append(str(path.relative_to(REPO)))
        if write:
            path.unlink()

    # The first physical-magazine pass stamped a generic shell with a complete
    # firearm. Replace only those former generator-owned per-gun routes; the
    # new gauge/body/feed-kit chain lives under recipe/create/industry.
    for path in sorted(obsolete_magazine_paths):
        stale.append(str(path.relative_to(REPO)))
        if write:
            path.unlink()

    # Generated dossier commissions live in the ordinary Gunsmith recipe tree.
    # Remove only our named commission files when their platform source changes.
    for path in sorted(obsolete_dossier_paths):
        stale.append(str(path.relative_to(REPO)))
        if write:
            path.unlink()

    # Tiered acquisition supersedes the old monolithic level-5 cache/trade
    # set. Keep authored vanilla data untouched; remove only our blueprint_* /
    # industrial_blueprint_cache* generated files.
    for path in sorted(obsolete_acquisition_paths):
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
    print(
        f"Industry generator {mode}: {len(platforms)} platform/reference/maintenance profile manifest(s), "
        f"{len(cartridges)} cartridge manifest(s), {len(magazine_carriers)} removable-carrier manifest(s), "
        "all managed outputs current."
    )
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
