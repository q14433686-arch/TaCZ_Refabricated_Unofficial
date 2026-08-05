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

Cartridge manifests in `tools/industry/cartridges.json` generate case/projectile die calibration, forming, dedicated cartridge-assembly definitions, legacy-ammo replacement declarations, and all magazine calibration recipes derived from `industry/gun_feed/*.json`. The generator rejects a physical-magazine ammo family that has no cartridge source manifest.

Platform variants use TACZ's generic NBT-backed component, die, case, projectile, and blueprint items, so they deliberately share base textures. The generator only emits deterministic texture/model assets for fixed registry assets described by `tools/industry/machines.json`; it does not touch the separately licensed default gun-pack art.
