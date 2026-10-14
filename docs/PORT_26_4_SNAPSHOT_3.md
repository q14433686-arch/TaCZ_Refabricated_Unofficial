# Experimental Minecraft 26.4 Snapshot 3 port

Target: Minecraft `26.4-snapshot-3`, Fabric Loader `0.19.5`, Java 25,
Fabric API `0.162.2+26.4`, Loom `1.18-SNAPSHOT`, Gradle `9.7.0`.
Mod version: `1.1.8+fabric.26.4.R1` (build metadata preserves gun-pack version predicates).
Based on upstream branch `26.3`. This is an experimental port, not a stable release.

## Changes

- Move RenderPipeline imports to `com.mojang.blaze3d.pipeline`.
- Replace removed `Mth.clamp` calls with Java's `Math.clamp`.
- Adopt registered `BlockSoundSets`, resolving optional step sounds through the level.
  Silent blocks do not play a grenade bounce sound; sticking/bouncing still proceeds.
- Update Level constructor calls and LevelRenderer render/injection descriptors.
- Remove the obsolete SkyRenderer target redirect: 26.4 has no cached renderTarget
  field and instead receives the active RenderPass from LevelRenderer.
- Use vanilla `core/screentriangle` for PIP composition.
- Replace AbstractTexture aliases with non-owning TextureHandles. A narrowly scoped
  TextureManager lookup mixin resolves mask/font aliases without registering them as
  owning TextureResources, which would close resources owned by the mask target or
  font atlas on replacement. Closed aliases are discarded.
- Keep Cloth Config and ModMenu as optional compile dependencies; development runs
  no longer force their 26.3 versions to load. Optional integration code remains compiled.
- Use Fabric Loader's normalized Minecraft version `26.4-alpha.3` in mod metadata,
  while resolving the Minecraft artifact by its original `26.4-snapshot-3` ID.
- Raise the development client's heap from 768 MiB to 2 GiB: the first world test
  exhausted the old limit during chunk saving on exit. Use at least 2 GiB in the launcher.

## Temporary Forge Config API Port override

As checked on 2026-10-10, Forge Config API Port's Maven repository and Modrinth do
not publish a 26.4 build. This port retains **26.3.0**; Fabric otherwise refuses to
load it because its metadata requires `~26.3-`.

Install the TACZ jar, Fabric API `0.162.2+26.4`, and FCAP **26.3.0** in the instance.
Copy [the example](examples/26.4-snapshot-3/fabric_loader_dependencies.json) to
`config/fabric_loader_dependencies.json` in that instance. If the file already exists,
merge only the `forgeconfigapiport` entry into its `overrides` object. The example
preserves FCAP's Fabric Loader and Fabric API requirements and replaces only its
Minecraft requirement. Development runs use the same file in `run/config/`.

This is a version-check workaround, not a port of FCAP or a claim of complete
compatibility. Remove it when installing an official compatible FCAP build.
See [Fabric dependency override documentation](https://docs.fabricmc.net/players/troubleshooting/dependency-overrides).

Cloth Config, JEI/REI, PAL, Zoomify, Shoulder Surfing, Iris and Voxy have **not** been
verified in this snapshot. Compile pins inherited from 26.3 are not recommendations
to install those versions or override their requirements.

## Verification

Local verification on 2026-10-10, Java 25 / macOS / Apple M5 / Vulkan (MoltenVK):

- Full Gradle build and access-widener validation passed.
- README release consistency passed in strict mode.
- Client loaded Fabric API, FCAP (with the above override), and TACZ.
- Entered an Overworld with the integrated server and loaded the default gun pack
  and LRTactical indices; ordinary world rendering proceeded without the previous
  SkyRenderer mixin crash.
- All **13** custom render pipelines compiled, including PIP composition.

Not verified: shooting/reloading, scope mask/PIP appearance and resizing/reloading
while aiming, grenade interactions, external gun packs, dedicated server startup,
OpenGL, other operating systems, or optional integration combinations. Passing
compilation and basic world entry does not establish these behaviors.

## Build

```sh
./gradlew build
bash scripts/check_release_consistency.sh --strict
```

The distributable jar is in `build/libs/`; the `-sources.jar` is for source inspection.
For development, place the override in `run/config/` before `./gradlew runClient`.

Recommended upstream destination: a separate `26.4-snapshot-3` branch. Keep the
existing `26.3` release line available for Minecraft 26.3 users.
