package com.tacz.guns.resource;

import com.google.gson.Gson;
import net.fabricmc.loader.api.Version;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.*;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraft.server.packs.resources.IoSupplier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.*;

class GunPackOverlayTest {
    @TempDir Path temp;
    private static final String META = """
            {"namespace":"fixture", "overlays":{"entries":[
              {"directory":"early","minecraft":"[1.21.1],[26.3]"},
              {"directory":"wrong","minecraft":"[1.21.1]"},
              {"directory":"late","minecraft":">=26.1 <27"}]}}
            """;

    @Test void rangeDialectAndBundledDependency() throws Exception {
        Version v = Version.parse("1.1.8+fabric.26.3.R1-hotfix3");
        assertTrue(GunPackVersions.matches("[1.1.8,1.2.0)", v));
        assertFalse(GunPackVersions.matches("(1.1.8,)", v));
        assertTrue(GunPackVersions.matches(">=1.1.8", v));
        assertFalse(GunPackVersions.matches("1.1.7", v)); // existing Fabric exact semantics
        assertTrue(GunPackVersions.dependenciesMatch(Map.of("lrtactical", "[0.3.0]", "minecraft", "[1.21.1]"), true));
        assertFalse(GunPackVersions.dependenciesMatch(Map.of("lrtactical", ">=1"), true));
    }

    @Test void rejectInvalidOverlayEvenIfVersionWouldSkipIt() throws Exception {
        Version v = Version.parse("26.3");
        for (String bad : List.of("../outside", "assets", "data", "early")) {
            var meta = new Gson().fromJson(META.replace("wrong", bad), PackMeta.class);
            assertThrows(IllegalArgumentException.class, () -> GunPackOverlayResolver.resolve(meta, v));
        }
        assertThrows(IllegalArgumentException.class, () -> GunPackVersions.matches("[1,2", v));
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void layersAndLifecycle(boolean archive) throws Exception {
        var files = Map.of("gunpack.meta.json", META, "assets/fixture/check/a.txt", "base",
                "early/assets/fixture/check/a.txt", "first", "late/assets/fixture/check/a.txt", "winner",
                "data/fixture/check/b.txt", "base only", "wrong/data/fixture/check/b.txt", "wrong version",
                "late/assets/fixture/lang/en_us.json", "{\"ok\":\"kept\",\"bad\":[]}",
                "early/data/fixture/recipes/r.json", "{\"type\":\"minecraft:crafting_shaped\",\"result\":{\"item\":\"minecraft:stick\"}}");
        Path pack = temp.resolve(archive ? "pack.zip" : "pack");
        if (archive) {
            try (var zip = new ZipOutputStream(Files.newOutputStream(pack))) {
                for (var entry : files.entrySet()) {
                    zip.putNextEntry(new ZipEntry(entry.getKey())); // intentionally no explicit directory entries
                    zip.write(entry.getValue().getBytes(StandardCharsets.UTF_8));
                    zip.closeEntry();
                }
            }
        } else {
            for (var entry : files.entrySet()) {
                Path target = pack.resolve(entry.getKey());
                Files.createDirectories(target.getParent());
                Files.writeString(target, entry.getValue());
            }
        }
        var meta = GunPackLoader.readMeta(pack);
        var selected = GunPackOverlayResolver.resolve(meta, Version.parse("26.3"));
        assertEquals(List.of("early", "late"), selected);
        var supplier = GunPackLoader.supplier(null, List.of(new GunPackLoader.GunPack(pack, meta.getName(), selected)));
        var info = new PackLocationInfo("fixture", Component.literal("Fixture"), PackSource.DEFAULT, Optional.empty());
        try (var metadata = supplier.openMetadata(info)) { /* probing must not own or close data */ }
        try (var resources = supplier.openResources(info, null).findFirst().orElseThrow()) {
            try (var second = supplier.openResources(info, null).findFirst().orElseThrow()) {
                assertEquals("winner", read(second.getResource(PackType.CLIENT_RESOURCES, Identifier.parse("fixture:check/a.txt"))));
            }
            check(resources, PackType.CLIENT_RESOURCES, "check", "a.txt", "winner");
            check(resources, PackType.SERVER_DATA, "check", "b.txt", "base only");
            var recipe = read(resources.getResource(PackType.SERVER_DATA, Identifier.parse("fixture:recipe/r.json")));
            assertTrue(recipe.contains("\"id\""));
            check(resources, PackType.SERVER_DATA, "recipe", "r.json", recipe);
            var lang = read(resources.getResource(PackType.CLIENT_RESOURCES, Identifier.parse("fixture:lang/en_us.json")));
            assertTrue(lang.contains("kept"));
            assertFalse(lang.contains("bad"));
        }
    }

    private static void check(PackResources pack, PackType type, String dir, String name, String expected) throws Exception {
        var id = Identifier.parse("fixture:" + dir + "/" + name);
        Map<Identifier, IoSupplier<InputStream>> listed = new HashMap<>();
        pack.listResources(type, "fixture", dir, (key, supplier) -> assertNull(listed.put(key, supplier), "duplicate: " + key));
        assertEquals(expected, read(listed.get(id)));
        assertEquals(expected, read(pack.getResource(type, id)));
    }

    private static String read(IoSupplier<InputStream> supplier) throws Exception {
        assertNotNull(supplier);
        try (var in = supplier.get()) { return new String(in.readAllBytes(), StandardCharsets.UTF_8); }
    }
}
