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

`tools/industry/icon_mapping.json` is the baseline authoring source for the **client-resource** NBT identity → icon mapping. The generator mirrors the repaired first batch from `extras/icon_packs/TACZ_icons_pack_fixed.zip`, then overlays `extras/icon_packs/TACZ_extra_COMPLETE.zip`. The complete pack's `identity -> texture_name` authoring map is validated against the live generated backlog and converted into `assets/tacz/industry_icons/complete.json` (746 exact Item/NBT entries); its raw family/tint rules are deliberately not passed to the runtime loader because they are not this project's `entries[]` schema. The generator writes compatibility/audit reports under `extras/icon_packs/`, plus `TACZ_industry_icon_catalog.json` and `docs/INDUSTRY_ICON_COVERAGE.md`. This is still author/CI work only: at runtime players simply receive ordinary assets and the client reload listener reads valid mapping JSON from any resource pack. See `docs/INDUSTRY_ICON_MAPPING.md` for third-party extension.

`tools/industry/icon_geometry_overrides.json` is a deliberately short, reviewed list of lossless 32×32 icon corrections. The generator decodes the complete pack's RGBA PNGs, audits centroids/components/bounds, applies only listed integer translations or explicit stray-pixel removals without resampling, and emits `TACZ_extra_COMPLETE_geometry_report.json` / `docs/TACZ_EXTRA_COMPLETE_GEOMETRY_AUDIT.md`.

`tools/industry/machines.json` binds real registry blocks to supplied Blockbench models from `extras/industry_packs/TACZ_industry_blocks.zip`. The generator embeds the artist-owned `tacz_extra` model/texture assets, emits `tacz` parent wrappers plus four horizontal-facing blockstate variants, removes the old generated 16×16 cube placeholders, and writes `TACZ_industry_blocks_asset_report.json` / `docs/INDUSTRY_BLOCK_ASSET_COVERAGE.md`. It verifies model texture bindings, the source item/blockstate variants, PNG headers and image dimensions before writing.

Platform variants continue to use TACZ's generic NBT-backed component, die, case, projectile, and blueprint items; the mapping layer lets resource packs distinguish their stable identities without duplicate item registrations. The generator only writes independent GPL resource layers and does not modify the separately licensed default gun-pack art.

`manufacturing_tier` on every platform controls the blueprint recipe and discovery route. `legacy` patterns use low-tooling compacting and early weaponsmith/world sources; `service` schematics use standard mechanical crafting and master weaponsmith stock; `advanced` technical packages and `precision` dossiers move to expensive mechanical crafting plus rarer expedition tables. Generated blueprints carry `IndustryBlueprintTier` for tooltip/provenance, but platform matching remains backward-compatible with old blueprint stacks. See `docs/INDUSTRIAL_TECH_TIER_DESIGN.md`.
