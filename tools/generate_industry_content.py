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

CREATE_CONDITIONS = [{"condition": "fabric:all_mods_loaded", "values": ["create"]}]
STRUCTURAL_ORDER = ("receiver", "bolt", "barrel", "trigger", "recoil")


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


def output(item: str, nbt: dict[str, Any]) -> dict[str, Any]:
    return {"id": item, "components": {"minecraft:custom_data": nbt}}


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


def load_cartridges() -> list[dict[str, Any]]:
    manifest = read_json(CARTRIDGE_MANIFEST)
    entries = manifest.get("calibers") if isinstance(manifest, dict) else None
    if not isinstance(entries, list) or not entries:
        raise ValueError(f"{CARTRIDGE_MANIFEST}: 'calibers' must be a non-empty list")
    required = (
        "id", "ammo", "master_gun", "projectile_type",
        "case_name_en", "case_name_zh", "projectile_name_en", "projectile_name_zh",
        "case_die_name_en", "case_die_name_zh", "projectile_die_name_en", "projectile_die_name_zh",
    )
    seen_ids: set[str] = set()
    seen_ammo: set[str] = set()
    for entry in entries:
        if not isinstance(entry, dict):
            raise ValueError(f"{CARTRIDGE_MANIFEST}: each caliber must be an object")
        for key in required:
            value = entry.get(key)
            if not isinstance(value, str) or not value:
                raise ValueError(f"{CARTRIDGE_MANIFEST}: caliber missing '{key}'")
        if entry["id"] in seen_ids or entry["ammo"] in seen_ammo:
            raise ValueError(f"{CARTRIDGE_MANIFEST}: duplicate caliber id or ammo id")
        seen_ids.add(entry["id"])
        seen_ammo.add(entry["ammo"])
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
        component_entries.append({"kind": final_kind, "display_name": component_key})

    result[RESOURCE_ROOT / f"data/tacz/industry/assembly/gun/{slug}.json"] = {
        "platform": name,
        "blueprint_display_name": blueprint_key,
        "terminal_process": f"tacz:create/industry/assemble_{slug}",
        "components": component_entries,
        "materials": materials,
    }

    initial = parts[0]
    initial_component_key = f"item.tacz.gun_component.{name}_{initial['kind']}"
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
    }]
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
    for material in materials:
        for _ in range(material["count"]):
            sequence.append({
                "type": "create:deploying",
                "target": "$ingredient",
                "ingredient": material["item"],
                "results": ["$result"],
            })

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
        }),
        "sequence": sequence,
    }
    return result


def generated_cartridge_files(caliber: dict[str, Any]) -> dict[Path, Any]:
    caliber_id = caliber["id"]
    projectile_type = caliber["projectile_type"]
    master = partial("tacz:modern_kinetic_gun", {"GunId": caliber["master_gun"]})
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
    }
    projectile = {
        "IndustryPlatform": "ammunition",
        "IndustryPartKind": "projectile",
        "IndustryDisplayName": f"item.tacz.projectile_core.{caliber_id}_{projectile_type}",
        "CartridgeCaliber": caliber_id,
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
    files[RESOURCE_ROOT / f"data/tacz/recipe/create/industry/calibrate_case_die_{caliber_id}.json"] = deploying(
        case_blank_die, master, output("tacz:press_die", case_die)
    )
    files[RESOURCE_ROOT / f"data/tacz/recipe/create/industry/calibrate_projectile_die_{caliber_id}.json"] = deploying(
        projectile_blank_die, master, output("tacz:press_die", projectile_die)
    )
    files[RESOURCE_ROOT / f"data/tacz/recipe/create/industry/form_case_{caliber_id}.json"] = deploying(
        case_stock, partial("tacz:press_die", case_die), output("tacz:cartridge_case", case)
    )
    files[RESOURCE_ROOT / f"data/tacz/recipe/create/industry/form_projectile_{caliber_id}.json"] = deploying(
        projectile_stock, partial("tacz:press_die", projectile_die), output("tacz:projectile_core", projectile)
    )
    files[RESOURCE_ROOT / f"data/tacz/industry/cartridge_assembly/{caliber_id}.json"] = {
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
        "count": 1,
    }
    files[RESOURCE_ROOT / f"data/tacz/industry/ammo/{caliber_id}.json"] = {
        "legacy_recipe": f"tacz:ammo/{caliber_id}"
    }
    return files


def cartridge_language_entries(caliber: dict[str, Any], language: str) -> dict[str, str]:
    suffix = "zh" if language == "zh_cn" else "en"
    caliber_id = caliber["id"]
    projectile_type = caliber["projectile_type"]
    return {
        f"item.tacz.cartridge_case.{caliber_id}": caliber[f"case_name_{suffix}"],
        f"item.tacz.projectile_core.{caliber_id}_{projectile_type}": caliber[f"projectile_name_{suffix}"],
        f"item.tacz.press_die.case_{caliber_id}": caliber[f"case_die_name_{suffix}"],
        f"item.tacz.press_die.projectile_{caliber_id}_{projectile_type}": caliber[f"projectile_die_name_{suffix}"],
    }


def generated_magazine_files(cartridge_ammo_ids: set[str]) -> dict[Path, Any]:
    files: dict[Path, Any] = {}
    feed_root = RESOURCE_ROOT / "data/tacz/industry/gun_feed"
    for path in sorted(feed_root.glob("*.json")):
        feed = read_json(path)
        if feed.get("mechanism") != "detachable_magazine":
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
