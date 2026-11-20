package com.tacz.guns.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;

class ExportFingerprintTest {
    @Test void partialBackupCannotReplaceOriginalOrPruneUserFiles(@TempDir Path dir) throws Exception {
        var pack = Files.createDirectory(dir.resolve("default"));
        var backups = Files.createDirectory(dir.resolve("backups"));
        var custom = Files.writeString(pack.resolve("custom.txt"), "my pack edit");
        var mine = Files.writeString(backups.resolve("manual.zip"), "player-owned");
        GetJarResources.backupFiles(pack, backups);
        GetJarResources.backupFiles(pack, backups);
        try (var list = Files.list(backups)) { assertEquals(2, list.count()); }
        try { Files.createSymbolicLink(pack.resolve("linked"), custom.getFileName()); }
        catch (UnsupportedOperationException | FileSystemException ex) {
            org.junit.jupiter.api.Assumptions.abort("File system does not permit test symlinks: " + ex.getMessage());
        }
        assertThrows(java.io.IOException.class, () -> GetJarResources.backupFiles(pack, backups));
        assertEquals("my pack edit", Files.readString(custom));
        assertEquals("player-owned", Files.readString(mine));
        try (var list = Files.list(backups)) { assertEquals(2, list.count(), "staging file or duplicate backup left behind"); }
    }

    @Test void versionAndContentsIndependentlyInvalidateExport(@TempDir Path dir) throws Exception {
        Files.writeString(dir.resolve("probe.txt"), "one");
        var url = dir.toUri().toURL();
        var first = GetJarResources.calculateSourceFingerprint(url, "A");
        assertEquals(first, GetJarResources.calculateSourceFingerprint(url, "A"));
        assertNotEquals(first, GetJarResources.calculateSourceFingerprint(url, "B"));
        Files.writeString(dir.resolve("probe.txt"), "longer");
        assertNotEquals(first, GetJarResources.calculateSourceFingerprint(url, "A"));
    }
}
