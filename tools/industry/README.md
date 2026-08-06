# Industry content generator

The explicit platform manifests in `tools/industry/platforms/` are the source of truth for curated platform resources. The remaining bundled default-gun recipes are discovered through `tools/industry/default_gun_policy.json`, so all default guns receive generated high-fidelity platform resources without one manifest per gun.

```bash
# Rewrite managed recipes, assembly declarations, language keys, and machine texture/model assets
python3 tools/generate_industry_content.py --write

# CI/review mode: fail when checked-in generated output is stale
python3 tools/generate_industry_content.py --check
```

On Windows, use `py` in place of `python3` when appropriate.

The generator intentionally runs at authoring/build-tool time, not at game runtime. The generated output remains ordinary resource JSON and can still be inspected, overridden by datapacks, synchronized to recipe viewers, and debugged normally.

Cartridge manifests in `tools/industry/cartridges.json` cover every bundled default loose-ammo id (currently 24). They generate case/projectile die calibration, forming, dedicated cartridge-assembly definitions, legacy-ammo replacement declarations, recoverable spent-case reconditioning routes, and all magazine calibration recipes derived from `industry/gun_feed/*.json`. Each entry explicitly carries a ballistic balance tier, batch output count, propellant count, and case/projectile blank mass factors; the generator rejects batches beyond final stack/legacy batch limits or propellant below the legacy per-round floor. It also reads each final ammo index's effective `stack_size` and writes the same 26.2 `minecraft:max_stack_size` component to its case/projectile outputs. A declared master gun is verified to actually use that ammo id; default ammo with no matching bundled firearm uses an explicitly named multi-slot mechanical-crafter calibre gauge instead of an unrelated gun. The generator rejects a physical-magazine ammo family or bundled default ammo id that has no cartridge source manifest.

`tools/industry/blueprint_acquisition.json` controls generated 26.2 data-driven master-weaponsmith trade entries and TACZ world-chest blueprint cache injection. These are independent acquisition routes for every generated platform blueprint; their original compacting recipe remains a deliberate fallback, not a player build prerequisite.

`tools/industry/icon_mapping.json` is the authoring source for the **client-resource** NBT identity → icon mapping. The generator mirrors the repaired user-supplied `tacz_extra` assets from `extras/icon_packs/TACZ_icons_pack_fixed.zip` into the mod, writes `assets/tacz/industry_icons/default.json`, and creates the exact identity audit at `extras/icon_packs/TACZ_industry_icon_catalog.json` plus `docs/INDUSTRY_ICON_COVERAGE.md`. This is still author/CI work only: at runtime players simply receive ordinary assets and the client reload listener reads mapping JSON from any resource pack. See `docs/INDUSTRY_ICON_MAPPING.md` for third-party extension.

`tools/industry/machines.json` binds real registry blocks to supplied Blockbench models from `extras/industry_packs/TACZ_industry_blocks.zip`. The generator embeds the artist-owned `tacz_extra` model/texture assets, emits `tacz` parent wrappers plus four horizontal-facing blockstate variants, removes the old generated 16×16 cube placeholders, and writes `TACZ_industry_blocks_asset_report.json` / `docs/INDUSTRY_BLOCK_ASSET_COVERAGE.md`. It verifies model texture bindings, the source item/blockstate variants, PNG headers and image dimensions before writing.

Platform variants continue to use TACZ's generic NBT-backed component, die, case, projectile, and blueprint items; the mapping layer lets resource packs distinguish their stable identities without duplicate item registrations. The generator only writes independent GPL resource layers and does not modify the separately licensed default gun-pack art.
