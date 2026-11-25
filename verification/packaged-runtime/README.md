# Test the distributed JAR, not the development source sets

`scripts/test_packaged_connection.py` makes two temporary copies of this small consumer build. In the `none` lane each JVM loads only Fabric Loader/API, Forge Config API Port and the same product JAR (including its nested libraries). Only the **client** has a test entrypoint. A directory gun pack exists only on the dedicated server and declares `tacz: >=1.2.0` and `minecraft: [26.1.2]`. Receiving its index exercises the installed product version, not a synthetic version string. The test joins over loopback TCP, observes its index, renames it during a real `/reload`, disconnects, checks the old cache was cleared and rejoins with a fresh index and the same seven component-bearing items. The server starts with the pinned `569869f` receiver's full default pack, its directory-source fingerprint, an edited AK index and a user-added file. The supervisor compares every file in the backup against that edited tree and every newly installed file against the product JAR. A new edit must then survive ordinary reload without creating a second backup. This exercises the **new server** with a pre-existing export, not an older client protocol or a second launch of the old binary. Game logs, screenshots and the JAR digest are retained by the distribution workflow. No server, local saves, credentials, or downloaded mod JARs belong in this directory.

To run on Linux with Java 25, after accepting the [Minecraft EULA](https://aka.ms/MinecraftEULA) for your disposable test server:

```sh
# Only needed if the receiver commit is missing in a shallow clone:
git fetch --no-tags --depth=1 origin 569869f107a60aacfa211c4fc658fcd1c58aee93
./gradlew jar
xvfb-run -a python3 scripts/test_packaged_connection.py --accept-test-eula path/to/actual-production.jar
```


The `--viewer jei` and `--viewer rei` lanes add exactly one matching viewer to both JVMs (and its dependencies). Each lane hand-places a gun smith table recipe in the real 3×3 menu twice, checks the server result with and without the final ingredient, and must reconnect with exactly two tables and no remaining ingredients. No test recipe grants or creative clicks. The client recipe book unlocks when the server supplies the iron blocks. With a viewer the probe queries its live ordinary crafting and TaCZ gun and ammo-workbench displays across both server-only index/recipe generations. This does **not** test the unrelated REI client-only cheat-give command fallback.
