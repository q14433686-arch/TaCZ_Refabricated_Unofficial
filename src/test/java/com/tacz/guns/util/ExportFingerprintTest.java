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
        Files.writeString(dir.resolve("probe.txt"), "longer");
        assertNotEquals(first, GetJarResources.calculateSourceFingerprint(url, "A"));
    }
}
