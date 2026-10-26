#!/usr/bin/env python3
"""Guard 26.3 block-entity removal, target persistence and block/cart skin wiring.

This is a source-level check. The isolated behavioral tests in
``scripts/test_block_entities.py`` and a real game session cover different
failure modes; neither can replace the other.
"""

from __future__ import annotations

import argparse
import re
from pathlib import Path

from verify_block_drops import java_code, method_body

ROOT = Path(__file__).resolve().parents[1]
BLOCKS = Path("src/main/java/com/tacz/guns/block")
RENDERER = Path("src/main/java/com/tacz/guns/client/renderer/block/TargetRenderer.java")
MINECART = Path("src/main/java/com/tacz/guns/entity/TargetMinecart.java")
MINECART_RENDERER = Path("src/main/java/com/tacz/guns/client/renderer/entity/TargetMinecartRenderer.java")


def check(root: Path) -> list[str]:
    errors: list[str] = []
    entity_dir = root / BLOCKS / "entity"
    if not entity_dir.is_dir():
        return ["missing block-entity source directory"]

    for path in sorted(entity_dir.glob("*.java")):
        code = java_code(path)
        if not re.search(r"class\s+\w+\s+extends\s+BlockEntity\b", code):
            continue
        if re.search(r"\b(?:private|protected|public)\s+ItemStack\s+\w+\s*=", code):
            if not method_body(code, "preRemoveSideEffects") and "tacz:no-drop-on-remove" not in path.read_text():
                errors.append(f"{path.name}: ItemStack field has no removal side-effects hook")
        if re.search(r"\b(?:public|protected)\s+void\s+onRemove\s*\(", code):
            errors.append(f"{path.name}: obsolete onRemove hook")

    try:
        statue = java_code(entity_dir / "StatueBlockEntity.java")
        target = java_code(entity_dir / "TargetBlockEntity.java")
        block = java_code(root / BLOCKS / "TargetBlock.java")
        renderer = java_code(root / RENDERER)
        minecart = java_code(root / MINECART)
        minecart_renderer = java_code(root / MINECART_RENDERER)
    except OSError as exc:
        return errors + [str(exc)]

    removal = method_body(statue, "preRemoveSideEffects")
    if not all(part in removal for part in (
        "super.preRemoveSideEffects(pos, state)", "!level.isClientSide()", "this.dropItem(state, false)"
    )):
        errors.append("StatueBlockEntity: missing server-side drop of the saved gun using the old state")
    drop = method_body(statue, "dropItem")
    # The no-argument overload delegates to the state-aware implementation.
    if "this.dropItem(this.getBlockState(), true)" not in drop:
        errors.append("StatueBlockEntity: interaction drop no longer delegates to the state-aware implementation")
    if "state.hasProperty(FACING)" not in statue or "this.gunItem = ItemStack.EMPTY" not in statue:
        errors.append("StatueBlockEntity: state fallback or idempotent clearing is missing")

    load = method_body(target, "loadAdditional")
    save = method_body(target, "saveAdditional")
    legacy = method_body(target, "readLegacyOwner")
    if not all(part in load for part in ("OWNER_PROFILE_TAG", "ResolvableProfile.CODEC", "readLegacyOwner")):
        errors.append("TargetBlockEntity: modern profile must be read with a legacy fallback")
    if not all(part in save for part in ("OWNER_PROFILE_TAG", "ResolvableProfile.CODEC", "OWNER_UUID_TAG", "OWNER_NAME_TAG")):
        errors.append("TargetBlockEntity: modern profile and flat compatibility keys are not saved")
    if not all(part in legacy for part in ("OWNER_UUID_TAG", "OWNER_NAME_TAG", "LEGACY_OWNER_TAG", "getIntArray")):
        errors.append("TargetBlockEntity: legacy flat keys or Owner compound migration is missing")

    resolve = method_body(target, "resolveOwnerProfile")
    current_lookup = method_body(target, "isCurrentLookup")
    if (not all(part in resolve for part in (
            "nonCriticalIoPool()", "fetchByName", "fetchById", "server.execute", "isCurrentLookup",
            "ResolvableProfile.createUnresolved(complete.id())", "GunMod.LOGGER.warn"
        )) or "this.profileLookupGeneration == generation" not in current_lookup
            or "ResolvableProfile.createResolved(complete)" in resolve):
        errors.append("TargetBlockEntity: UUID lookup must remain asynchronous, stale-safe and dynamic for client skins")
    if re.search(r"new\s+GameProfile\s*\(\s*null\b", block) or "setOwnerName(" not in method_body(block, "setPlacedBy"):
        errors.append("TargetBlock: named placement must use a resolvable profile, not a null UUID")
    block_extract = method_body(renderer, "extractRenderState")
    if ("context.playerSkinRenderCache()" not in renderer
            or not all(part in block_extract for part in (
                "getOwnerProfile()", "skinCache.getOrDefault(owner)",
                "DefaultPlayerSkin.get(owner.partialProfile())", "state.skinTexture = null"
            ))):
        errors.append("TargetRenderer: skin extraction must use the 26.3 cache with a safe fallback")

    cart_owner = method_body(minecart, "getOwnerProfile")
    cart_profile = method_body(minecart, "getGameProfile")
    if (re.search(r"new\s+GameProfile\s*\(\s*null\b", minecart)
            or not all(part in cart_owner for part in (
                "this.getCustomName()", "!name.equals(this.ownerProfileName)",
                "ResolvableProfile.createUnresolved(name)", "this.ownerProfile = null"
            ))
            or "profile.partialProfile()" not in cart_profile):
        errors.append("TargetMinecart: name changes must rebuild a non-null-UUID dynamic profile")
    cart_extract = method_body(minecart_renderer, "extractRenderState")
    cart_submit = method_body(minecart_renderer, "submitMinecartContents")
    if ("ctx.getPlayerSkinRenderCache()" not in minecart_renderer
            or not all(part in cart_extract for part in (
                "entity.getOwnerProfile()", "skinCache.getOrDefault(owner)",
                "DefaultPlayerSkin.get(owner.partialProfile())", "targetState.skinTexture = null"
            ))
            or "t.skinTexture" not in cart_submit
            or "createLookup(" in minecart_renderer):
        errors.append("TargetMinecartRenderer: skin extraction must use the 26.3 cache with a safe fallback")

    return errors


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--strict", action="store_true", help="return nonzero on a regression")
    parser.add_argument("--root", type=Path, default=ROOT, help=argparse.SUPPRESS)
    args = parser.parse_args()
    errors = check(args.root)
    for error in errors:
        print(f"[FAIL] {error}")
    if not errors:
        print("26.3 block entity persistence/removal and target skins: OK (static checks only)")
    return int(bool(errors) and args.strict)


if __name__ == "__main__":
    raise SystemExit(main())
