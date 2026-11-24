#!/usr/bin/env python3
"""Check the 39JqB2p resource fixes; optionally verify the actual distributable JAR.

Usage: python3 scripts/check_visible_bug_resources.py [--jar build/libs/<mod>.jar]
This is a static resource check, not an in-game compatibility test.
"""

import argparse
import hashlib
import json
from pathlib import Path
import zipfile

ROOT = Path(__file__).resolve().parents[1]
ANIMATION = "assets/tacz/custom/tacz_default_gun/assets/tacz/animations/glock_17.animation.json"
WHITELIST = "data/tacz/tags/entity_type/interact_key/whitelist.json"
BUNDLE = ROOT / "resources/tacz-26.1.2-source-and-resource-bundle.jar"
CHEST_BOATS = {
    f"minecraft:{wood}_chest_boat"
    for wood in (
        "oak", "spruce", "birch", "jungle", "acacia", "dark_oak",
        "mangrove", "cherry", "pale_oak",
    )
} | {"minecraft:bamboo_chest_raft"}
OTHER_ENTITIES = {
    "minecraft:camel", "minecraft:minecart", "minecraft:chest_minecart",
    "minecraft:item_frame", "minecraft:glow_item_frame", "minecraft:interaction",
    "minecraft:mule", "minecraft:villager", "minecraft:wandering_trader", "minecraft:horse",
}


def check_resources(read):
    tag = json.loads(read(WHITELIST))
    assert tag["replace"] is False, "Whitelist must still merge with other packs"
    values = tag["values"]
    strings = [v for v in values if isinstance(v, str)]
    optional = [v for v in values if isinstance(v, dict)]
    assert set(strings) == OTHER_ENTITIES | {"#minecraft:boat"}, strings
    assert len(strings) == len(OTHER_ENTITIES) + 1, "Duplicate whitelist entry"
    assert len(optional) == len(CHEST_BOATS), "Expected ten chest boats/rafts"
    assert {v["id"] for v in optional} == CHEST_BOATS, optional
    assert all(v.get("required") is False for v in optional), optional
    assert len(values) == len(strings) + len(optional), "Unexpected tag entry type"

    animation = read(ANIMATION)
    assert b"golf17" not in animation, "Missing sound reference reintroduced"
    fixed = json.loads(animation)
    assert "sound_effects" not in fixed["animations"]["draw"]
    # The 1.2 animation changed beyond its draw sound. Verify every byte against the
    # audited native-draw-sound variant, not the superseded 1.1.8 bundle animation.
    assert hashlib.sha256(animation).hexdigest() == "246a05cc0ef304c51a52742fbaa1f9cc4e9846bc513eb152cf42706cd88e3af1"


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--jar", type=Path, help="Also check packaged resources and override precedence")
    args = parser.parse_args()
    source = ROOT / "src/main/resources"
    check_resources(lambda path: (source / path).read_bytes())
    print("PASS: source whitelist and Glock 17 1.2 override (draw sound absent)")
    if args.jar:
        with zipfile.ZipFile(args.jar) as jar:
            for path in (WHITELIST, ANIMATION):
                assert jar.namelist().count(path) == 1, f"Missing or duplicate JAR entry: {path}"
                assert jar.read(path) == (source / path).read_bytes(), f"Source override lost: {path}"
            check_resources(jar.read)
        print(f"PASS: packaged resources match source overrides: {args.jar}")


if __name__ == "__main__":
    main()
