#!/usr/bin/env python3
"""Static regression checks for the lGmrtNC recipe-viewer failure (no JVM needed).

Checks the reported built-in recipes and the optional JEI hook wiring. This is NOT
an in-game crafting test, a codec decode test, or a Mixin application test.
Run: python3 scripts/verify_recipe_viewer_reload.py
"""
from pathlib import Path
import json
import re
import unittest

ROOT = Path(__file__).resolve().parent.parent
JAVA = ROOT / "src/main/java"
RESOURCES = ROOT / "src/main/resources"
BRIDGE = JAVA / "com/tacz/guns/client/compat/RecipeViewerReloadBridge.java"
MIXIN_NAME = "compat.jei.JeiClientLifecycleHandlerMixin"
MIXIN = JAVA / "cn/sh1rocu/tacz/mixin/compat/jei/JeiClientLifecycleHandlerMixin.java"
RECIPES = {
    "tacz:gun_smith_table": ("tacz:gun_smith_table", None),
    "lrtactical:smith_table": ("tacz:workbench_b", {"BlockId": "lrtactical:smith_table"}),
    "tacz:attachment_workbench": ("tacz:workbench_c", {"BlockId": "tacz:attachment_workbench"}),
    "tacz:ammo_workbench": ("tacz:workbench_a", {"BlockId": "tacz:ammo_workbench"}),
    "tacz:iron_ammo_box": ("tacz:ammo_box", {"Level": 0}),
    "tacz:gold_ammo_box": ("tacz:ammo_box", {"Level": 1}),
    "tacz:diamond_ammo_box": ("tacz:ammo_box", {"Level": 2}),
    "tacz:target": ("tacz:target", None),
    "tacz:statue": ("tacz:statue", None),
    "tacz:target_minecart": ("tacz:target_minecart", None),
}


def code(path):
    # Exclude comments, which deliberately document the forbidden event name.
    return re.sub(r"/\*.*?\*/|//[^\n]*", "", path.read_text(encoding="utf-8"), flags=re.S)


class RecipeViewerChecks(unittest.TestCase):
    def test_all_ten_reported_recipes_have_current_shaped_schema(self):
        for recipe_id, (item, custom_data) in RECIPES.items():
            with self.subTest(recipe=recipe_id):
                namespace, name = recipe_id.split(":")
                recipe = json.loads((RESOURCES / f"data/{namespace}/recipe/{name}.json").read_text())
                self.assertEqual(recipe["type"], "minecraft:crafting_shaped")
                result = recipe["result"]
                self.assertEqual(result["id"], item)
                self.assertNotIn("item", result)
                self.assertNotIn("nbt", result)
                self.assertEqual(result.get("components", {}).get("minecraft:custom_data"), custom_data)
                pattern = recipe["pattern"]
                self.assertTrue(1 <= len(pattern) <= 3)
                self.assertTrue(1 <= len(pattern[0]) <= 3)
                self.assertEqual(len({len(row) for row in pattern}), 1)
                symbols = set("".join(pattern)) - {" "}
                self.assertEqual(symbols, set(recipe["key"]))
                for ingredient in recipe["key"].values():
                    self.assertIsInstance(ingredient, str)
                    # All non-vanilla ingredient tags used by these recipes ship locally.
                    if ingredient.startswith("#") and not ingredient.startswith("#minecraft:"):
                        ns, tag = ingredient[1:].split(":")
                        tag_file = RESOURCES / f"data/{ns}/tags/item/{tag}.json"
                        self.assertTrue(json.loads(tag_file.read_text())["values"])

    def test_refresh_does_not_forge_recipe_sync_or_write_jei_recipe_cache(self):
        source = code(BRIDGE)
        for forbidden in ("AFTER_RECIPES_UPDATED", "clearClientRecipes", "setClientSyncedRecipes"):
            self.assertNotIn(forbidden, source)
        self.assertIn("jeiReloadListener.onResourceManagerReload(client.getResourceManager())", source)

    def test_optional_mixin_captures_safe_listener(self):
        config = json.loads((RESOURCES / "tacz.fabric.mixins.json").read_text())
        self.assertIn(MIXIN_NAME, config["client"])
        self.assertNotIn(MIXIN_NAME, config["mixins"])
        source = code(MIXIN)
        self.assertIn("@Pseudo", source)
        self.assertIn('targets = "mezz.jei.fabric.startup.ClientLifecycleHandler"', source)
        self.assertIn("getReloadListener()Lnet/minecraft/server/packs/resources/ResourceManagerReloadListener;", source)
        self.assertIn('at = @At("RETURN")', source)
        self.assertIn("require = 0", source)
        self.assertIn("RecipeViewerReloadBridge.setJeiReloadListener(cir.getReturnValue())", source)
        # Existing MixinPlugin derives the optional mod id from this package segment.
        self.assertEqual(("cn.sh1rocu.tacz.mixin." + MIXIN_NAME).split(".")[5], "jei")

    def test_listener_is_client_lifetime_not_connection_lifetime(self):
        source = code(BRIDGE)
        clear_body = source.split("public static void clear()", 1)[1].split("}", 1)[0]
        self.assertNotIn("jeiReloadListener", clear_body)
        for pending in ("reloadRequested", "reloadInProgress", "resourceFallbackUsed"):
            self.assertIn(f"{pending} = false", clear_body)


if __name__ == "__main__":
    unittest.main(verbosity=2)
