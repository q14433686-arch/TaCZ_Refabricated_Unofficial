# Test the distributed JAR, not the development source sets

`scripts/test_packaged_connection.py` makes two temporary copies of this small consumer build. Each JVM loads only Fabric Loader/API, Forge Config API Port and the same product JAR (including its nested libraries). Only the **client** has a test entrypoint. A directory gun pack exists only on the dedicated server. The test joins over loopback TCP, observes its index, renames it during a real `/reload`, disconnects, checks the old cache was cleared and rejoins with a fresh index and the same seven component-bearing items. Game logs, screenshots and the JAR digest are retained by the distribution workflow. No server, local saves, credentials, or downloaded mod JARs belong in this directory.

To run on Linux with Java 25, after accepting the [Minecraft EULA](https://aka.ms/MinecraftEULA) for your disposable test server:

```sh
./gradlew jar
xvfb-run -a python3 scripts/test_packaged_connection.py --accept-test-eula path/to/actual-production.jar
```
