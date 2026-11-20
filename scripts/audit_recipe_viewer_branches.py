#!/usr/bin/env python3
"""Read-only audit of frozen branch snapshots; NOT a game/codec/network test.

Run from any directory: python3 scripts/audit_recipe_viewer_branches.py
Requires the audited commits to exist locally (fetch refs without checking them out).
No dependencies, network access, checkout, or working-tree writes.
"""

import json
from pathlib import Path
import subprocess

ROOT = Path(__file__).resolve().parents[1]
BASELINE = "ece5a4a5d1a1ab9dd2e77380941879dd5fb881d7"
SNAPSHOTS = {
    "26.3": "63390406c82bb85b0145e7b173c856be3bb5d8f8",
    "26.1.2": "1ec4429100929f360b0281bc360cca7ad2785ec8",
    "1.21.11": "bdf18af9696f34eb2b8dba988375c6502fbef1e4",
}
RECIPES = (
    "tacz:gun_smith_table", "lrtactical:smith_table", "tacz:attachment_workbench",
    "tacz:ammo_workbench", "tacz:iron_ammo_box", "tacz:gold_ammo_box",
    "tacz:diamond_ammo_box", "tacz:target", "tacz:statue", "tacz:target_minecart",
)


def show(commit, path):
    return subprocess.check_output(
        ["git", "show", f"{commit}:{path}"], cwd=ROOT, text=True,
    )


def require(condition, message):
    if not condition:
        raise ValueError(message)


def audit_recipe(commit, recipe_id):
    namespace, name = recipe_id.split(":")
    path = f"src/main/resources/data/{namespace}/recipe/{name}.json"
    recipe = json.loads(show(commit, path))
    require(recipe == json.loads(show(BASELINE, path)), f"{recipe_id}: differs from 26.2 baseline")
    require(recipe["type"] == "minecraft:crafting_shaped", f"{recipe_id}: type")
    pattern, key, result = recipe["pattern"], recipe["key"], recipe["result"]
    require(1 <= len(pattern) <= 3 and 1 <= len(pattern[0]) <= 3,
            f"{recipe_id}: pattern size")
    require(all(len(row) == len(pattern[0]) for row in pattern), f"{recipe_id}: ragged pattern")
    require(set("".join(pattern)) - {" "} == set(key), f"{recipe_id}: pattern/key mismatch")
    require("id" in result and "item" not in result and "nbt" not in result,
            f"{recipe_id}: legacy result")
    require(result.get("count", 1) > 0, f"{recipe_id}: output count")
    for ingredient in key.values():
        require(isinstance(ingredient, str), f"{recipe_id}: non-string ingredient")
        if ingredient.startswith("#") and not ingredient.startswith("#minecraft:"):
            tag_namespace, tag_name = ingredient[1:].split(":")
            tag_path = f"src/main/resources/data/{tag_namespace}/tags/item/{tag_name}.json"
            require(bool(json.loads(show(commit, tag_path))["values"]),
                    f"{recipe_id}: empty shipped tag {ingredient}")
    # This checks JSON shape and shipped tag declarations only. It does not invoke
    # Minecraft codecs, resolve live tag members, or apply external datapack overrides.


def main():
    rows = []
    for branch, commit in SNAPSHOTS.items():
        for recipe_id in RECIPES:
            audit_recipe(commit, recipe_id)
        bridge = show(commit, "src/main/java/com/tacz/guns/client/compat/RecipeViewerReloadBridge.java")
        initializer = show(commit, "src/main/java/cn/sh1rocu/tacz/TaCZFabric.java")
        properties = dict(
            line.split("=", 1) for line in show(commit, "gradle.properties").splitlines()
            if "=" in line and not line.lstrip().startswith("#")
        )
        rows.append({
            "branch": branch,
            "commit": commit,
            "recipe_json_checks_passed": len(RECIPES),
            "jei_dependency": properties.get("jei_version"),
            "rei_dependency": properties.get("rei_version"),
            "has_fake_jei_event_path": 'getField("AFTER_RECIPES_UPDATED")' in bridge,
            "has_direct_jei_restart_path": 'getMethod("restartJei")' in bridge,
            "has_rei_full_reload_path": 'candidate.getName().equals("reloadPlugins")' in bridge,
            "has_vanilla_serializer_sync_opt_in": "synchronizeRecipeSerializer" in initializer,
            "has_once_per_connection_fallback_guard": "resourceFallbackUsed" in bridge,
        })
    print(json.dumps({
        "scope": "static JSON/source inspection only; booleans describe code presence, not runtime safety",
        "baseline": BASELINE,
        "branches": rows,
    }, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
