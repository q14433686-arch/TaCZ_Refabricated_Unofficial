#!/usr/bin/env python3
"""Author/CI validation for ordered mixed-ammo content.

Players do not run this tool. It verifies that each explicit alternate AmmoId
has a real index, profile, mechanically prepared type blank, retained-gauge
profile imprint, projectile die/form route and four-slot cartridge assembly
output instead of being only a display/NBT alias.
"""
from __future__ import annotations

import argparse
import json
from pathlib import Path
from typing import Any

ROOT = Path(__file__).resolve().parents[1]
RES = ROOT / "src/main/resources"
PROFILE_ROOT = RES / "data/tacz/industry/ammo_profiles"
INDEX_ROOT = RES / "data/tacz/index/ammo"
ASSEMBLY_ROOT = RES / "data/tacz/industry/cartridge_assembly"
CREATE_ROOT = RES / "data/tacz/recipe/create/industry"
LEGACY_PROFILE_BLANK_ROOT = RES / "data/tacz/recipe/industry"
LANG_ROOT = RES / "assets/tacz/lang"

# A physical ingredient selects the projectile construction kind at the Create
# Basin. The following retained cartridge gauge supplies the calibre/AmmoId;
# neither a display key nor a generic NBT recipe chooses a final ammunition id.
PROFILE_KIND_MATERIALS = {
    "ap": "minecraft:iron_nugget",
    "hp": "minecraft:gold_nugget",
    "slug": "minecraft:quartz",
}
NEUTRAL_PROJECTILE_BLANK = {
    "IndustryPlatform": "ammunition",
    "IndustryPartKind": "projectile_blank",
    "IndustryDisplayName": "item.tacz.projectile_blank",
}


def read(path: Path) -> dict[str, Any]:
    return json.loads(path.read_text(encoding="utf-8"))


def fail(message: str) -> None:
    raise ValueError(message)


def custom_data_from_result(recipe: dict[str, Any], path: Path) -> dict[str, Any]:
    results = recipe.get("results")
    if not isinstance(results, list) or not results or not isinstance(results[0], dict):
        fail(f"{path}: missing Create result")
    result = results[0].get("components", {}).get("minecraft:custom_data", {})
    if not isinstance(result, dict):
        fail(f"{path}: result lacks minecraft:custom_data")
    return result


def type_blank(kind: str) -> dict[str, str]:
    return {
        "IndustryPlatform": "ammunition",
        "IndustryPartKind": "projectile_type_blank",
        "IndustryDisplayName": f"item.tacz.projectile_blank.type_{kind}",
        "ProjectileType": kind,
    }


def exact_profile_blank(ammo: str, caliber: str, kind: str) -> dict[str, str]:
    _, path = ammo.split(":", 1)
    return {
        "IndustryPlatform": "ammunition",
        "IndustryPartKind": "projectile_profile_blank",
        "IndustryDisplayName": f"item.tacz.projectile_blank.{path}",
        "CartridgeCaliber": caliber,
        "CartridgeAmmoId": ammo,
        "ProjectileType": kind,
    }


def cartridge_gauge(caliber: str) -> dict[str, str]:
    return {
        "IndustryPlatform": "ammunition",
        "IndustryPartKind": "cartridge_gauge",
        "IndustryDisplayName": f"item.tacz.press_die.gauge_{caliber}",
        "CartridgeCaliber": caliber,
    }


def contains_exact_ingredient(recipe: dict[str, Any], expected: Any) -> bool:
    ingredients = recipe.get("ingredients")
    return isinstance(ingredients, list) and expected in ingredients


def verify() -> int:
    profiles = sorted(PROFILE_ROOT.glob("*.json"))
    if not profiles:
        fail("no explicit industry/ammo_profiles entries found")
    stale_table_routes = sorted(LEGACY_PROFILE_BLANK_ROOT.glob("ammo_profile_blank_*.json"))
    if stale_table_routes:
        fail(
            "alternate profile blanks must use Create Basin + retained gauge routes, not Gunsmith Table: "
            + ", ".join(str(path.relative_to(ROOT)) for path in stale_table_routes)
        )

    languages = {locale: read(LANG_ROOT / f"{locale}.json") for locale in ("en_us", "zh_cn")}
    checked_type_recipes: set[str] = set()
    for profile_path in profiles:
        profile = read(profile_path)
        ammo = profile.get("ammo")
        canonical = profile.get("caliber_ammo")
        kind = profile.get("kind")
        if not isinstance(ammo, str) or ":" not in ammo:
            fail(f"{profile_path}: missing full ammo id")
        namespace, path = ammo.split(":", 1)
        if namespace != "tacz" or path != profile_path.stem:
            fail(f"{profile_path}: resource id must exactly match ammo {ammo}")
        if not isinstance(canonical, str) or ":" not in canonical or not isinstance(kind, str) or not kind:
            fail(f"{profile_path}: missing canonical calibre or kind")
        if kind not in PROFILE_KIND_MATERIALS:
            fail(f"{profile_path}: no real Create material route declared for projectile kind {kind!r}")
        _, caliber = canonical.split(":", 1)

        index_path = INDEX_ROOT / f"{path}.json"
        assembly_path = ASSEMBLY_ROOT / f"{path}.json"
        type_path = CREATE_ROOT / f"prepare_projectile_type_{kind}.json"
        imprint_path = CREATE_ROOT / f"imprint_projectile_profile_{path}.json"
        die_path = CREATE_ROOT / f"calibrate_projectile_die_{path}.json"
        form_path = CREATE_ROOT / f"form_projectile_{path}.json"
        for required in (index_path, assembly_path, type_path, imprint_path, die_path, form_path):
            if not required.exists():
                fail(f"{profile_path}: missing real content route {required.relative_to(ROOT)}")

        index = read(index_path)
        assembly = read(assembly_path)
        type_recipe = read(type_path)
        imprint = read(imprint_path)
        die = read(die_path)
        form = read(form_path)
        expected_type_blank = type_blank(kind)
        expected_profile_blank = exact_profile_blank(ammo, caliber, kind)

        if kind not in checked_type_recipes:
            checked_type_recipes.add(kind)
            if type_recipe.get("type") != "create:compacting":
                fail(f"{type_path}: type blank must be a real Create Basin compacting route")
            if not contains_exact_ingredient(type_recipe, PROFILE_KIND_MATERIALS[kind]):
                fail(f"{type_path}: missing physical {kind} material {PROFILE_KIND_MATERIALS[kind]}")
            neutral_ingredient = {
                "fabric:type": "forge:partial_nbt",
                "items": ["tacz:projectile_blank"],
                "nbt": NEUTRAL_PROJECTILE_BLANK,
            }
            if not contains_exact_ingredient(type_recipe, neutral_ingredient):
                fail(f"{type_path}: must consume one neutral projectile blank")
            type_results = type_recipe.get("results", [])
            if not isinstance(type_results, list) or not type_results or type_results[0].get("id") != "tacz:projectile_blank":
                fail(f"{type_path}: type preparation must output a physical projectile blank stack")
            if custom_data_from_result(type_recipe, type_path) != expected_type_blank:
                fail(f"{type_path}: result must be a type-only preform, not a final calibre/AmmoId")
            if type_recipe.get("heat_requirement") != "heated":
                fail(f"{type_path}: physical projectile type preparation must require a heated Basin")

        if imprint.get("type") != "create:deploying":
            fail(f"{imprint_path}: exact profile identity must be imprinted by a one-workpiece Create Deployer")
        target = imprint.get("target", {})
        ingredient = imprint.get("ingredient", {})
        if target.get("items") != ["tacz:projectile_blank"] or target.get("nbt") != expected_type_blank:
            fail(f"{imprint_path}: target must be the exact physical {kind} type preform")
        if ingredient.get("items") != ["tacz:press_die"] or ingredient.get("nbt") != cartridge_gauge(caliber):
            fail(f"{imprint_path}: retained cartridge gauge must provide exact calibre {caliber}")
        if imprint.get("keep_held_item") is not True:
            fail(f"{imprint_path}: cartridge gauge must be retained")
        imprint_results = imprint.get("results", [])
        if not isinstance(imprint_results, list) or not imprint_results or imprint_results[0].get("id") != "tacz:projectile_blank":
            fail(f"{imprint_path}: gauge imprint must output a physical projectile profile blank")
        if custom_data_from_result(imprint, imprint_path) != expected_profile_blank:
            fail(f"{imprint_path}: result lacks exact profile AmmoId/type")

        if assembly.get("ammo") != ammo or assembly.get("projectile_type") != kind:
            fail(f"{assembly_path}: ammo/projectile_type must exactly match profile")
        result = custom_data_from_result(form, form_path)
        if result.get("CartridgeAmmoId") != ammo or result.get("ProjectileType") != kind:
            fail(f"{form_path}: projectile output lacks exact AmmoId/type")
        die_result = custom_data_from_result(die, die_path)
        if die_result.get("ProjectileType") != kind:
            fail(f"{die_path}: die lacks exact profile projectile type")
        die_ingredient = die.get("ingredient", {}).get("nbt", {})
        form_target = form.get("target", {}).get("nbt", {})
        if die_ingredient != expected_profile_blank or form_target != expected_profile_blank:
            fail(f"{path}: profile blank must uniquely select both die calibration and projectile forming")
        name_key = index.get("name")
        if not isinstance(name_key, str) or any(name_key not in language for language in languages.values()):
            fail(f"{index_path}: named alternate AmmoId lacks en_us/zh_cn translation")
        for key in (expected_type_blank["IndustryDisplayName"], expected_profile_blank["IndustryDisplayName"]):
            if any(key not in language for language in languages.values()):
                fail(f"{profile_path}: missing en_us/zh_cn translation for {key}")

    print(
        f"Mixed-ammo content checked: {len(profiles)} explicit alternate AmmoId profile(s), "
        f"all with heated Basin type preparation, retained-gauge profile imprint, die/projectile and assembly routes."
    )
    return 0


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--check", action="store_true", help="validate committed mixed-ammo content")
    parser.parse_args()
    return verify()


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except (OSError, ValueError, json.JSONDecodeError) as exc:
        print(f"Mixed-ammo content check failed: {exc}")
        raise SystemExit(1)
