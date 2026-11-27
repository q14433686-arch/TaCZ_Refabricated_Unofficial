package com.tacz.guns.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;

class ExportFingerprintTest {
    @Test void versionAndContentsIndependentlyInvalidateExport(@TempDir Path dir) throws Exception {
        Files.writeString(dir.resolve("probe.txt"), "one");
        var url = dir.toUri().toURL();
        var first = GetJarResources.calculateSourceFingerprint(url, "A");
        assertEquals(first, GetJarResources.calculateSourceFingerprint(url, "A"));
        assertNotEquals(first, GetJarResources.calculateSourceFingerprint(url, "B"));
        Files.setLastModifiedTime(dir.resolve("probe.txt"), java.nio.file.attribute.FileTime.fromMillis(10000));
        assertEquals(first, GetJarResources.calculateSourceFingerprint(url, "A"));
        Files.writeString(dir.resolve("probe.txt"), "two");
        assertNotEquals(first, GetJarResources.calculateSourceFingerprint(url, "A"));
    }

    @Test void backupFailureLeavesLiveTreeAlone(@TempDir Path dir) throws Exception {
        var source = Files.createDirectory(dir.resolve("source"));
        Files.writeString(source.resolve("index"), "new default");
        var live = Files.createDirectory(dir.resolve("live"));
        Files.writeString(live.resolve("index"), "local edit");
        var notADirectory = Files.writeString(dir.resolve("blocked"), "leave me alone");
        assertThrows(java.io.IOException.class, () -> GetJarResources.replaceExport(source.toUri().toURL(), live, notADirectory));
        assertEquals("local edit", Files.readString(live.resolve("index")));
        assertEquals("leave me alone", Files.readString(notADirectory));
        var backup = dir.resolve("backup");
        GetJarResources.replaceExport(source.toUri().toURL(), live, backup);
        assertEquals("new default", Files.readString(live.resolve("index")));
        Files.writeString(live.resolve("index"), "local edit");
        GetJarResources.replaceExport(source.toUri().toURL(), live, backup);
        try (var zips = Files.list(backup)) {
            var archives = zips.toList();
            assertEquals(1, archives.size(), "identical backup is not rotated twice");
            try (var zip = new java.util.zip.ZipFile(archives.getFirst().toFile()); var in = zip.getInputStream(zip.getEntry("index"))) {
                assertEquals("local edit", new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8));
            }
        }
    }
}
