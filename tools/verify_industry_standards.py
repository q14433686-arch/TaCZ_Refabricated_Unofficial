#!/usr/bin/env python3
"""Author/CI verification for explicit cartridge and feed-interface standards.

Players never run this tool. It proves that the generated default standards,
per-gun declarations and real carrier manufacture all carry one consistent,
explicit contract; it does not infer interchangeability from cartridge names,
gun classes, models or capacity.
"""
from __future__ import annotations

import argparse
import json
from collections import defaultdict
from pathlib import Path
from typing import Any

ROOT = Path(__file__).resolve().parents[1]
RES = ROOT / "src/main/resources"
CART_MANIFEST = ROOT / "tools/industry/cartridges.json"
CARRIER_MANIFEST = ROOT / "tools/industry/magazine_carriers.json"
DEFAULT_FEED_ROOT = RES / "data/tacz/industry/gun_feed"
CARTRIDGE_STANDARD_ROOT = RES / "data/tacz/industry/cartridge_standards"
FEED_STANDARD_ROOT = RES / "data/tacz/industry/feed_standards"
CREATE_ROOT = RES / "data/tacz/recipe/create/industry"


def read(path: Path) -> Any:
    return json.loads(path.read_text(encoding="utf-8"))


def fail(message: str) -> None:
    raise ValueError(message)


def result_custom_data(recipe: dict[str, Any], path: Path) -> dict[str, Any]:
    results = recipe.get("results")
    if isinstance(results, list) and results and isinstance(results[0], dict):
        result = results[0]
    else:
        result = recipe.get("result")
    if not isinstance(result, dict):
        fail(f"{path}: missing Create result")
    custom = result.get("components", {}).get("minecraft:custom_data")
    if not isinstance(custom, dict):
        fail(f"{path}: result lacks minecraft:custom_data")
    return custom


def verify() -> int:
    cartridges = read(CART_MANIFEST).get("calibers")
    if not isinstance(cartridges, list) or not cartridges:
        fail(f"{CART_MANIFEST}: calibers must be a non-empty list")
    expected_cartridge_paths: set[Path] = set()
    cartridge_by_ammo: dict[str, dict[str, Any]] = {}
    for cartridge in cartridges:
        if not isinstance(cartridge, dict):
            fail(f"{CART_MANIFEST}: each cartridge must be an object")
        caliber = cartridge.get("id")
        ammo = cartridge.get("ammo")
        if not isinstance(caliber, str) or not isinstance(ammo, str) or ammo != f"tacz:{caliber}":
            fail(f"{CART_MANIFEST}: default cartridge must use matching tacz:id and ammo")
        path = CARTRIDGE_STANDARD_ROOT / f"{caliber}.json"
        expected_cartridge_paths.add(path)
        standard = read(path)
        expected = {
            "schema_version": 1,
            "canonical_ammo": ammo,
            "cartridge_caliber": caliber,
        }
        if standard != expected:
            fail(f"{path}: expected exact central cartridge standard {expected}")
        cartridge_by_ammo[ammo] = cartridge
    actual_cartridge_paths = set(CARTRIDGE_STANDARD_ROOT.glob("*.json"))
    if actual_cartridge_paths != expected_cartridge_paths:
        fail("cartridge standard file set differs from the default cartridge manifest")

    manifest = read(CARRIER_MANIFEST)
    carriers = manifest.get("carriers")
    if not isinstance(carriers, list) or not carriers:
        fail(f"{CARRIER_MANIFEST}: carriers must be a non-empty list")
    by_standard: dict[str, list[dict[str, Any]]] = defaultdict(list)
    for carrier in carriers:
        if not isinstance(carrier, dict):
            fail(f"{CARRIER_MANIFEST}: carrier must be an object")
        for key in ("id", "family", "feed_standard", "ammo", "capacity", "mechanism"):
            if key not in carrier:
                fail(f"{CARRIER_MANIFEST}: carrier missing {key}")
        expected_standard = f"tacz:{carrier['family']}"
        if carrier["feed_standard"] != expected_standard:
            fail(f"{CARRIER_MANIFEST}: {carrier['id']} must bind {expected_standard}")
        by_standard[carrier["feed_standard"]].append(carrier)

    # Explicit third-party declarations already using one generated standard
    # may contribute audited base/xmag capacities. This is not discovery by
    # class/model/capacity: their sidecar already names the exact standard id.
    extension_capacities: dict[str, set[int]] = defaultdict(set)
    bound_third_party_feeds = 0
    for path in sorted((RES / "data").glob("*/industry/gun_feed/*.json")):
        feed = read(path)
        standard_id = feed.get("feed_standard") if isinstance(feed, dict) else None
        if not isinstance(standard_id, str) or standard_id not in by_standard:
            continue
        if path.relative_to(RES / "data").parts[0] != "tacz":
            bound_third_party_feeds += 1
        members = by_standard[standard_id]
        if feed.get("mechanism") not in {member["mechanism"] for member in members} \
                or feed.get("magazine_family") not in {member["family"] for member in members} \
                or feed.get("ammo") not in {member["ammo"] for member in members}:
            fail(f"{path}: {standard_id} disagrees with central mechanism/family/ammo")
        capacities = [feed.get("magazine_capacity")]
        variants = feed.get("carrier_variants", [])
        if isinstance(variants, list):
            capacities.extend(variant.get("capacity") for variant in variants if isinstance(variant, dict))
        for capacity in capacities:
            if not isinstance(capacity, int) or not 1 <= capacity <= 512:
                fail(f"{path}: {standard_id} has invalid declared capacity")
            extension_capacities[standard_id].add(capacity)

    expected_feed_paths: set[Path] = set()
    for standard_id, members in by_standard.items():
        namespace, path_name = standard_id.split(":", 1)
        if namespace != "tacz":
            fail(f"{CARRIER_MANIFEST}: generated default standard must use tacz namespace: {standard_id}")
        expected_path = FEED_STANDARD_ROOT / f"{path_name}.json"
        expected_feed_paths.add(expected_path)
        standard = read(expected_path)
        mechanisms = {member["mechanism"] for member in members}
        families = {member["family"] for member in members}
        ammo_ids = {member["ammo"] for member in members}
        if len(mechanisms) != 1 or len(families) != 1 or len(ammo_ids) != 1:
            fail(f"{CARRIER_MANIFEST}: {standard_id} has ambiguous members")
        ammo = next(iter(ammo_ids))
        expected = {
            "schema_version": 1,
            "mechanism": next(iter(mechanisms)),
            "magazine_family": next(iter(families)),
            "cartridge_standard": f"tacz:{cartridge_by_ammo[ammo]['id']}",
            "accepted_capacities": sorted({member["capacity"] for member in members} | extension_capacities[standard_id]),
        }
        if standard != expected:
            fail(f"{expected_path}: expected exact feed-interface standard {expected}")
    actual_feed_paths = set(FEED_STANDARD_ROOT.glob("*.json"))
    if actual_feed_paths != expected_feed_paths:
        fail("feed-interface standard file set differs from the removable-carrier manifest")

    default_external = 0
    for path in sorted(DEFAULT_FEED_ROOT.glob("*.json")):
        feed = read(path)
        if feed.get("mechanism") not in {"detachable_magazine", "belt"}:
            continue
        default_external += 1
        standard_id = feed.get("feed_standard")
        expected_standard = f"tacz:{feed.get('magazine_family')}"
        if standard_id != expected_standard:
            fail(f"{path}: external default feed must bind {expected_standard}")
        if standard_id not in by_standard:
            fail(f"{path}: feed_standard has no carrier-manifest identity")

    for carrier in carriers:
        carrier_id = carrier["id"]
        standard = carrier["feed_standard"]
        final_recipe_path = CREATE_ROOT / f"assemble_carrier_{carrier_id}.json"
        final_recipe = read(final_recipe_path)
        final = result_custom_data(final_recipe, final_recipe_path)
        if final.get("MagazineFeedStandard") != standard:
            fail(f"{final_recipe_path}: manufactured carrier lacks exact MagazineFeedStandard")
        for role in ("target", "ingredient"):
            input_tag = final_recipe.get(role, {}).get("nbt", {})
            if "MagazineFeedStandard" in input_tag:
                fail(f"{final_recipe_path}: old named carrier subassemblies must remain valid upgrade inputs")
        for stem in ("form_carrier_body", "form_carrier_feed_kit"):
            route_path = CREATE_ROOT / f"{stem}_{carrier_id}.json"
            route = read(route_path)
            result = result_custom_data(route, route_path)
            if result.get("MagazineFeedStandard") != standard:
                fail(f"{route_path}: newly formed subassembly lacks exact MagazineFeedStandard")
        reverse_path = CREATE_ROOT / f"reverse_carrier_gauge_{carrier_id}.json"
        reverse = read(reverse_path)
        reverse_tag = reverse.get("ingredient", {}).get("nbt", {})
        if "MagazineFeedStandard" in reverse_tag:
            fail(f"{reverse_path}: old empty-carrier reverse evidence must not require the new standard tag")

    # A feed standard is additive governance, not permission to remove an
    # already explicit generic-family reload route. Keep the regression guard
    # close to the resource assertions because this bug is otherwise invisible
    # to JSON-only validation: the game loads every definition successfully but
    # generic magazines silently disappear from reload selection.
    physical_service = (ROOT / "src/main/java/com/tacz/guns/industry/magazine/PhysicalMagazineService.java").read_text(encoding="utf-8")
    standard_service = (ROOT / "src/main/java/com/tacz/guns/industry/magazine/FeedInterfaceStandardService.java").read_text(encoding="utf-8")
    if "hasSameDeclaredCarrierCaliber(definition.getAmmoId(), item.getAmmoId(magazine))" not in physical_service \
            or "AmmoProfileService.isSameCaliber(first.getAmmoId(), second.getAmmoId())" not in standard_service \
            or "carrierHasExplicitMatchingStandard" in physical_service:
        fail("feed standards must preserve explicit family + canonical-calibre generic magazine reload compatibility")

    print(
        f"Industry standards checked: {len(cartridges)} cartridge standard(s), "
        f"{len(by_standard)} feed-interface standard(s), {default_external} bound default external and "
        f"{bound_third_party_feeds} explicitly matched third-party feed declaration(s); generic-magazine regression guard passed."
    )
    return 0


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--check", action="store_true", help="validate committed industrial standard resources")
    parser.parse_args()
    return verify()


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except (OSError, ValueError, json.JSONDecodeError, KeyError) as exc:
        print(f"Industry standards check failed: {exc}")
        raise SystemExit(1)
