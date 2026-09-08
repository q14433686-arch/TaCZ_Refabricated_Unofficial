#!/usr/bin/env python3
"""Static B/C/D regression checks for refab 1.21.11 (not an in-game test).

Run: python3 docs/check_visible_bugs_39JqB2p.py [--jar build/libs/<mod>.jar]
With --jar, also verify that processResources/remapJar packaged the source overrides.
"""
import argparse
import json
from pathlib import Path
import unittest
import zipfile

ROOT = Path(__file__).resolve().parents[1]
RESOURCES = ROOT / "src/main/resources"
ANIMATION = "assets/tacz/custom/tacz_default_gun/assets/tacz/animations/glock_17.animation.json"
WHITELIST = "data/tacz/tags/entity_type/interact_key/whitelist.json"
BUNDLE = ROOT / "resources/tacz-26.1.2-source-and-resource-bundle.jar"
JAR = None


class VisibleBugsChecks(unittest.TestCase):
    def test_interact_whitelist(self):
        tag = json.loads((RESOURCES / WHITELIST).read_bytes())
        self.assertIs(tag["replace"], False)
        required = [v for v in tag["values"] if isinstance(v, str)]
        self.assertCountEqual(required, [
            "minecraft:camel", "minecraft:minecart", "minecraft:chest_minecart",
            "minecraft:item_frame", "minecraft:glow_item_frame", "minecraft:interaction",
            "#minecraft:boat", "minecraft:mule", "minecraft:villager",
            "minecraft:wandering_trader", "minecraft:horse",
        ])
        optional = [v for v in tag["values"] if isinstance(v, dict)]
        expected = [f"minecraft:{wood}_chest_boat" for wood in (
            "oak", "spruce", "birch", "jungle", "acacia", "cherry",
            "dark_oak", "pale_oak", "mangrove",
        )] + ["minecraft:bamboo_chest_raft"]
        self.assertCountEqual([v["id"] for v in optional], expected)
        for entry in optional:
            self.assertIs(entry["required"], False)

    def test_only_invalid_draw_sound_removed(self):
        with zipfile.ZipFile(BUNDLE) as bundle:
            original = json.loads(bundle.read(ANIMATION))
        fixed_bytes = (RESOURCES / ANIMATION).read_bytes()
        fixed = json.loads(fixed_bytes)
        # The animation key is 'draw'; 'raise' is part of the erroneous sound name.
        removed = original["animations"]["draw"].pop("sound_effects")
        self.assertEqual(removed, {"0.0": {"effect": "p24_pi_golf17_stockskel_raise"}})
        self.assertEqual(original, fixed, "All other animation data must remain unchanged")
        self.assertNotIn(b"golf17", fixed_bytes)

    def test_no_shared_pipeline_hand_registration(self):
        for path in (ROOT / "src/main/java").rglob("*.java"):
            source = path.read_text(encoding="utf-8")
            for removed in ("assignCommonEntityPipelinesToHandIfNeeded",
                            "commonEntityPipelinesAssigned", "commonEntityPipelinesAssignAttempted"):
                self.assertNotIn(removed, source, str(path))
        iris = (ROOT / "src/main/java/com/tacz/guns/compat/iris/IrisCompat.java").read_text()
        self.assertNotIn("RenderPipelines.", iris)
        # This branch also classifies TACZ-owned mesh pipelines; preserve those bridges.
        for retained in ("assignPipelineToIris", "assignMeshPipelineToHand", "assignMeshPipelineToEntity"):
            self.assertIn(retained, iris)

    def test_packaged_overrides(self):
        if JAR is None:
            self.skipTest("Pass --jar to verify the actual build artifact")
        with zipfile.ZipFile(JAR) as jar:
            for path in (ANIMATION, WHITELIST):
                self.assertEqual(jar.namelist().count(path), 1, path)
                self.assertEqual(jar.read(path), (RESOURCES / path).read_bytes(), path)


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--jar", type=Path)
    args = parser.parse_args()
    JAR = args.jar
    unittest.main(argv=[__file__], verbosity=2)
