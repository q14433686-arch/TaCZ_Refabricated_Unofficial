# 1.21.11 distribution probes

This is an independent Loom project. Compile the small test mod against a named copy of the product, remap it, then run in **intermediary** with the original product and real Minecraft/Fabric libraries. No test classes or optional viewers belong in the product JAR. Test worlds and EULA files are disposable. Run from the repository root with JDK 21, Python 3.11+ and (for the client) Xvfb:

```sh
./gradlew remapJar
JAR=$(find build/libs -name '*.jar' ! -name '*-sources.jar' ! -name '*-dev.jar')
python3 scripts/test_12111_server.py --accept-test-eula "$JAR"
python3 scripts/test_12111_connection.py --accept-test-eula --viewer none "$JAR"
# Separate jobs/clean instances; never co-install the two viewers:
python3 scripts/test_12111_connection.py --accept-test-eula --viewer jei "$JAR"
python3 scripts/test_12111_connection.py --accept-test-eula --viewer rei "$JAR"
```

The server-only mod registers vanilla `test_function`s with data-driven `test_instance` entries rather than reflecting on named enum constants in development annotations. The TCP server, by contrast, has **no test mod**; it supplies the ingredients and packs, and the client is a non-operator. JEI/REI probes query installed registrations and exercise the transfer handler, not just our plugin methods. Artifacts contain the server/client logs, operator conversion reports and the server-check XML; acceptance belongs to the [specific commit/run](../../docs/PORT_1_2_1_21_11.md), not to the presence of this fixture.

### Vanilla ocular aperture (no Iris, no PIP)

```sh
python3 scripts/test_12111_player.py --accept-test-eula --suite optics "$JAR"
```

A real singleplayer M700 with a standard 8× scope is raised, held and released against a two-colour concrete wall. The probe reads the two live GL depth copies that `ScopeDepthCopyState` exposes and records the aperture mask (`aperture < world`). It requires a valid copy from the current frame, a bounded, non-border-touching central mask in steady ADS, and no mask after release. The raise sample records the mask mid-transition but does not claim reprojection. Iris, Sodium, Complementary, reprojected PIP, thermal and near-field light cases are not covered by this suite.

### REI give diagnostic (not a crafting test)

```sh
python3 scripts/test_12111_rei_give.py --accept-test-eula --backend client-only "$JAR"
python3 scripts/test_12111_rei_give.py --accept-test-eula --backend matched "$JAR"
```

Each starts a new loopback survival server without a test mod. The installed REI client invokes its normal give handler for the **registered** AK47 entry, not a fabricated packet. A server menu round trip and vanilla count-only inventory queries check that a non-operator got nothing. The supervisor then grants operator status to that disposable player, repeats the same action, compares it with the player's explicit component-bearing `/give`, logs the full stacks, revokes operator and stops the instance. A client-only command fallback losing `GunId` is a **diagnostic result**, not a compatibility fix. The matched packet backend must deliver one selected AK47 with its OEM part. The default command above **also requires identical NBT types**. The CI comparison uses `--characterize-wire`: compare *every* component in the cursor and inventory to an independent target `ItemStack.OPTIONAL_CODEC` JSON wire round-trip; the two server counts and exact minimal `/give` control must match. Report `fullComponentMatch` separately and warn if a numeric tag narrows. Never rewrite TaCZ items or REI's packets to make a comparison pass.

### Native presentation (no optional mods)

```sh
python3 scripts/test_12111_presentation.py --accept-test-eula "$JAR"
```

Three fresh production clients use explicit launcher UUIDs A, B and A, with the same player name. Only the actual `config/tacz/view-profiles/*.json` travels between instances. Exercise the Pause button, rebound keyboard/mouse shortcut, invalid paste, cancel, save, type vs. single-item assignments and inheritance. The last client reads the presented HUD pixels; its PNGs, per-phase JSON and logs go in `build/packaged-evidence/presentation/`. This doesn't test a hardware controller, an Iris shader or the optics.

The return launch additionally compares the paused hand and muzzle locator for A’s saved position vs. a zero-position draft, and checks that the hip HUD disappears during real ADS and returns on release.

### Separate display and installed integration clients

```sh
for suite in display pal controller; do
    python3 scripts/test_12111_player.py --accept-test-eula --suite "$suite" "$JAR" || exit 1
done
```

The display-only external fixture whitelists an emerald and supplies a cyan mesh-only LOD. In the integrated survival world the display probe aims at a cell, captures the empty, held-preview and deposited frames, and checks the actual server hand and block entity before and after a bound input. It compares the outline at the same pixel positions and reads the hint after mouse/keyboard Use, during a hold and on expiry. The fixture pack is not in the product.

The PAL-only client uses **1.1.9**. A test-mod-only, remapped model observer samples a remote avatar’s rendered support arm on two re-entries; this is not a second TCP client. The controller-only client loads signed **Controllable 0.25.7 + Framework 0.13.6**, checks their release JARs by SHA-256, and drives a virtual SDL2 device through the installed binding registry. Its rumble callback is not a test of physical motors. These are test procedures; see the port record for actual outcomes.
