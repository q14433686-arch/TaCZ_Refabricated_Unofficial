#!/usr/bin/env python3
"""Author/CI validation for ordered mixed-ammo content.

Players do not run this tool. It verifies that each explicit alternate AmmoId
has a real index, profile, projectile die/form route and four-slot cartridge
assembly output instead of being only a display/NBT alias.
"""
from __future__ import annotations

import argparse
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
RES = ROOT / "src/main/resources"
PROFILE_ROOT = RES / "data/tacz/industry/ammo_profiles"
INDEX_ROOT = RES / "data/tacz/index/ammo"
ASSEMBLY_ROOT = RES / "data/tacz/industry/cartridge_assembly"
CREATE_ROOT = RES / "data/tacz/recipe/create/industry"
LANG_ROOT = RES / "assets/tacz/lang"


def read(path: Path) -> dict:
    return json.loads(path.read_text(encoding="utf-8"))


def fail(message: str) -> None:
    raise ValueError(message)


def verify() -> int:
    profiles = sorted(PROFILE_ROOT.glob("*.json"))
    if not profiles:
        fail("no explicit industry/ammo_profiles entries found")
    languages = {locale: read(LANG_ROOT / f"{locale}.json") for locale in ("en_us", "zh_cn")}
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
        index_path = INDEX_ROOT / f"{path}.json"
        assembly_path = ASSEMBLY_ROOT / f"{path}.json"
        die_path = CREATE_ROOT / f"calibrate_projectile_die_{path}.json"
        form_path = CREATE_ROOT / f"form_projectile_{path}.json"
        profile_blank_path = RES / f"data/tacz/recipe/industry/ammo_profile_blank_{path}.json"
        for required in (index_path, assembly_path, die_path, form_path, profile_blank_path):
            if not required.exists():
                fail(f"{profile_path}: missing real content route {required.relative_to(ROOT)}")
        index = read(index_path)
        assembly = read(assembly_path)
        die = read(die_path)
        form = read(form_path)
        profile_blank = read(profile_blank_path)
        if assembly.get("ammo") != ammo or assembly.get("projectile_type") != kind:
            fail(f"{assembly_path}: ammo/projectile_type must exactly match profile")
        result = form.get("results", [{}])[0].get("components", {}).get("minecraft:custom_data", {})
        if result.get("CartridgeAmmoId") != ammo or result.get("ProjectileType") != kind:
            fail(f"{form_path}: projectile output lacks exact AmmoId/type")
        die_result = die.get("results", [{}])[0].get("components", {}).get("minecraft:custom_data", {})
        if die_result.get("ProjectileType") != kind:
            fail(f"{die_path}: die lacks exact profile projectile type")
        blank_result = profile_blank.get("result", {}).get("item", {}).get("nbt", {})
        if blank_result.get("CartridgeAmmoId") != ammo or blank_result.get("ProjectileType") != kind:
            fail(f"{profile_blank_path}: profile blank lacks exact AmmoId/type")
        die_ingredient = die.get("ingredient", {}).get("nbt", {})
        form_target = form.get("target", {}).get("nbt", {})
        if die_ingredient != blank_result or form_target != blank_result:
            fail(f"{path}: profile blank must uniquely select both die calibration and projectile forming")
        name_key = index.get("name")
        if not isinstance(name_key, str) or any(name_key not in language for language in languages.values()):
            fail(f"{index_path}: named alternate AmmoId lacks en_us/zh_cn translation")
    print(f"Mixed-ammo content checked: {len(profiles)} explicit alternate AmmoId profile(s), all with index/die/projectile/assembly routes.")
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
