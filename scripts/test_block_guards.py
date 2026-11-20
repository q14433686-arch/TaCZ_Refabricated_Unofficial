#!/usr/bin/env python3
"""Mutation tests for the 26.2 offline drop and block-entity guards."""

from __future__ import annotations

import json
import os
import shutil
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
BLOCKS = Path("src/main/java/com/tacz/guns/block")
LOOT = Path("src/main/resources/data/tacz/loot_table/blocks")
RENDERER = Path("src/main/java/com/tacz/guns/client/renderer/block/TargetRenderer.java")
MINECART = Path("src/main/java/com/tacz/guns/entity/TargetMinecart.java")
MINECART_RENDERER = Path("src/main/java/com/tacz/guns/client/renderer/entity/TargetMinecartRenderer.java")


class GuardTests(unittest.TestCase):
    def setUp(self) -> None:
        self.tmp = tempfile.TemporaryDirectory(prefix="tacz-block-guard-")
        self.root = Path(self.tmp.name)
        for directory in (BLOCKS, LOOT):
            shutil.copytree(ROOT / directory, self.root / directory)
        for path in (RENDERER, MINECART, MINECART_RENDERER):
            (self.root / path).parent.mkdir(parents=True, exist_ok=True)
            shutil.copy2(ROOT / path, self.root / path)

    def tearDown(self) -> None:
        self.tmp.cleanup()

    def guard(self, script: str, should_pass: bool) -> None:
        env = dict(os.environ, PYTHONDONTWRITEBYTECODE="1")
        result = subprocess.run(
            [sys.executable, str(ROOT / "scripts" / script), "--strict", "--root", str(self.root)],
            text=True, capture_output=True, env=env,
        )
        self.assertEqual(result.returncode == 0, should_pass, result.stdout + result.stderr)

    def replace(self, path: Path, old: str, new: str) -> None:
        file = self.root / path
        text = file.read_text(encoding="utf-8")
        self.assertIn(old, text)
        file.write_text(text.replace(old, new, 1), encoding="utf-8")

    def test_current_sources_pass(self) -> None:
        self.guard("verify_block_drops.py", True)
        self.guard("verify_block_entity_persistence.py", True)

    def test_root_condition_regression_fails(self) -> None:
        path = self.root / LOOT / "workbench_b.json"
        data = json.loads(path.read_text(encoding="utf-8"))
        data["pools"][0]["entries"][0]["conditions"][0]["properties"]["part"] = "head"
        path.write_text(json.dumps(data), encoding="utf-8")
        self.guard("verify_block_drops.py", False)

    def test_ignored_storage_function_regression_fails(self) -> None:
        path = self.root / LOOT / "ammo_storage_gold.json"
        data = json.loads(path.read_text())
        entry = data["pools"][0]["entries"][0]
        entry["modifier"] = entry.pop("functions")[0]
        path.write_text(json.dumps(data))
        self.guard("verify_block_drops.py", False)

    def test_creative_half_regression_fails(self) -> None:
        self.replace(BLOCKS / "GunSmithTableBlockB.java", "bedPart == BedPart.HEAD", "bedPart == BedPart.FOOT")
        self.guard("verify_block_drops.py", False)

    def test_manual_second_drop_fails(self) -> None:
        path = BLOCKS / "GunSmithTableBlockB.java"
        self.replace(path, "return super.playerWillDestroy(level, pos, blockState, player);",
                     "Block.popResource(level, pos, null); return super.playerWillDestroy(level, pos, blockState, player);")
        self.guard("verify_block_drops.py", False)

    def test_missing_statue_hook_fails(self) -> None:
        self.replace(BLOCKS / "entity/StatueBlockEntity.java", "void preRemoveSideEffects(", "void obsoleteRemovalHook(")
        self.guard("verify_block_entity_persistence.py", False)

    def test_missing_profile_save_fails(self) -> None:
        self.replace(BLOCKS / "entity/TargetBlockEntity.java", "output.store(PROFILE_TAG,", "output.store(ALTERNATE_PROFILE_TAG,")
        self.guard("verify_block_entity_persistence.py", False)

    def test_default_only_skin_fails(self) -> None:
        self.replace(RENDERER, "skinRenderCache.getOrDefault(profile)", "skinRenderCache.getDefault(profile)")
        self.guard("verify_block_entity_persistence.py", False)

    def test_missing_block_fallback_fails(self) -> None:
        self.replace(RENDERER, "PlayerSkinRenderCache.DEFAULT_PLAYER_SKIN_RENDER_TYPE", "missingFallback()")
        self.guard("verify_block_entity_persistence.py", False)

    def test_stale_lookup_generation_fails(self) -> None:
        self.replace(BLOCKS / "entity/TargetBlockEntity.java", "lookupGeneration != generation || ", "")
        self.guard("verify_block_entity_persistence.py", False)

    def test_server_must_not_freeze_dynamic_profile(self) -> None:
        self.replace(BLOCKS / "entity/TargetBlockEntity.java", 'ResolvableProfile.createUnresolved(found.id())',
                     'ResolvableProfile.createResolved(found)')
        self.guard("verify_block_entity_persistence.py", False)

    def test_minecart_null_uuid_fails(self) -> None:
        self.replace(MINECART, "return this.ownerProfile;",
                     'return new GameProfile(null, "Alex");')
        self.guard("verify_block_entity_persistence.py", False)

    def test_minecart_name_change_regression_fails(self) -> None:
        self.replace(MINECART, "!customName.equals(this.ownerProfileSource)", "this.ownerProfile == null")
        self.guard("verify_block_entity_persistence.py", False)

    def test_minecart_default_only_skin_fails(self) -> None:
        self.replace(MINECART_RENDERER, "skinRenderCache.getOrDefault(profile)", "skinRenderCache.getDefault(profile)")
        self.guard("verify_block_entity_persistence.py", False)

    def test_missing_minecart_fallback_fails(self) -> None:
        self.replace(MINECART_RENDERER, "PlayerSkinRenderCache.DEFAULT_PLAYER_SKIN_RENDER_TYPE", "missingFallback()")
        self.guard("verify_block_entity_persistence.py", False)


if __name__ == "__main__":
    unittest.main(verbosity=2)
