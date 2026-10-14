# Experimental Minecraft 26.4 Snapshot 4 update

Target: Minecraft `26.4-snapshot-4`, Fabric Loader `0.19.5`, Java 25,
Fabric API `0.162.3+26.4`, Loom `1.18-SNAPSHOT`, Gradle `9.7.0`.
Mod version remains `1.1.8+fabric.26.4.R1`; this is an unpublished update to PR #108.
Upstream target is the existing `26.4` branch. Original Snapshot 3 port notes and
historical verification remain in [PORT_26_4_SNAPSHOT_3.md](PORT_26_4_SNAPSHOT_3.md).

## Dependency setup

Fabric Loader normalizes this snapshot to `26.4-alpha.4` in dependency predicates.
As checked on 2026-10-14, Modrinth still lists FCAP 26.3.1/26.3.0 for Minecraft 26.3,
with no 26.4 release. The port keeps FCAP **26.3.0** as the previously tested baseline.
Install Fabric API **0.162.3+26.4** and FCAP **26.3.0** alongside the distributable jar.
Merge [the override example](examples/26.4-snapshot-4/fabric_loader_dependencies.json)
into the instance's `config/fabric_loader_dependencies.json`, preserving other overrides.
This is an experimental version-check workaround, not a port of FCAP.
Development runs use the same override in `run/config/`.

## Snapshot 4 API changes

- Register entity, block-entity and menu factories in BuiltInClientRegistries; the
  old Fabric renderer registry helpers were removed in Fabric rendering API 28.
- Remove the second registration of the three TACZ block-entity renderers from
  TaCZFabricClient; ModEntitiesRender retains their registration. The new registry
  rejects duplicate keys instead of replacing the previously registered factory.
- Adapt gun-pack sound redirection to SoundInstance.resolveAndSelectSound, retaining
  the selected sound's settings and replacing its resource path after successful selection.

## Verification

Local checks on 2026-10-14, Java 25 / macOS / Apple M5 / Vulkan:

- Full Gradle build, access-widener validation, strict README version consistency,
  mixin class integrity and packaged nested-library checks passed.
- Client passed mod initialization after removing duplicate renderer registration;
  sound engine and texture atlases loaded with FCAP 26.3.0 and the override.
- The contributor confirmed successful world entry and stable operation in their
  Snapshot 4 client on 2026-10-14.

Snapshot 3 results are historical and must not be treated as verification of
Snapshot 4. Detailed gameplay coverage, custom pipeline compilation, dedicated servers,
OpenGL and optional integrations require further coverage.

```sh
./gradlew build
bash scripts/check_release_consistency.sh --strict
./gradlew runClient
```

Use at least 2 GiB of client heap. Distributable jars are in `build/libs/`;
`-sources.jar` files are for source inspection. No local client-specific keybinding
changes are included in this upstream update.
