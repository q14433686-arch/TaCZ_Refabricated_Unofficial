package com.tacz.guns.util;

import com.google.gson.JsonParser;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.gametest.framework.GameTestHelper;
import org.apache.commons.io.FileUtils;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Objects;
import java.util.UUID;
import java.util.zip.ZipFile;

/** Same bundled bytes; compare a current manifest with an otherwise identical previous-version manifest. */
public final class ExportUpgradeSmoke {
    @GameTest
    public void modifiedPackBackedUpOnlyOnVersionChange(GameTestHelper h) throws Exception {
        var root = Files.createTempDirectory("tacz-export-upgrade-");
        String folder = "port-test-" + UUID.randomUUID();
        String src = "/export-test";
        var local = root.resolve(folder).resolve("probe.txt");
        var backup = FabricLoader.getInstance().getGameDir().resolve("tacz_backup").resolve(folder);
        try {
            var source = Objects.requireNonNull(getClass().getResource(src));
            GetJarResources.copyModDirectory(getClass(), src, root, folder);
            h.assertTrue(Files.readString(local).equals("pack default\n"), "Initial export failed");
            Files.writeString(local, "my edit\n");
            GetJarResources.copyModDirectory(getClass(), src, root, folder);
            h.assertTrue(Files.readString(local).equals("my edit\n") && !Files.exists(backup), "Unchanged version overwrote user edit");
            var manifest = root.resolve(".export-state.json");
            var state = JsonParser.parseString(Files.readString(manifest)).getAsJsonObject();
            var entries = state.getAsJsonObject("entries");
            h.assertTrue(entries.size() == 1, "Wrong export manifest");
            entries.addProperty(entries.keySet().iterator().next(), GetJarResources.calculateSourceFingerprint(source, "previous-test-version"));
            Files.writeString(manifest, state.toString());
            GetJarResources.copyModDirectory(getClass(), src, root, folder);
            h.assertTrue(Files.readString(local).equals("pack default\n"), "Version-only upgrade skipped export");
            try (var files = Files.list(backup)) {
                var archives = files.toList();
                h.assertTrue(archives.size() == 1, "Expected exactly one backup");
                try (var zip = new ZipFile(archives.getFirst().toFile()); var contents = zip.getInputStream(zip.getEntry("probe.txt"))) {
                    h.assertTrue(new String(contents.readAllBytes(), StandardCharsets.UTF_8).equals("my edit\n"), "Backup lost user edit");
                }
            }
        } finally {
            FileUtils.deleteDirectory(root.toFile());
            FileUtils.deleteDirectory(backup.toFile());
        }
        com.tacz.guns.GunMod.LOGGER.info("PORT_RECEIVER_SERVER: export upgrade backup assertions passed");
        h.succeed();
    }
}
