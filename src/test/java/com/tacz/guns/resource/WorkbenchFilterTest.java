package com.tacz.guns.resource;

import com.tacz.guns.resource.filter.RecipeFilter;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackType;
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
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class WorkbenchFilterTest {
    @TempDir Path temp;
    private static final Identifier FILTER = Identifier.parse("tacz:recipe_filters/default.json");
    private static final String PATH = "data/tacz/recipe_filters/default.json";

    private static RecipeFilter parse(String json) {
        return CommonAssetsManager.GSON.fromJson(json, RecipeFilter.class);
    }

    @ParameterizedTest @ValueSource(strings = {"", "\"whitelist\":[],"})
    void blacklistOnlyFilterSurvivesServerToClientRoundTrip(String whitelist) {
        var server = parse("{" + whitelist + "\"blacklist\":[\"^addon:private/.*$\"]}");
        var client = parse(CommonAssetsManager.GSON.toJson(server)); // RecipeFilterManager -> RECIPE_FILTER
        for (var filter : List.of(server, client)) {
            for (String id : List.of("tacz:gun/ak47", "tacz:ammo/9mm", "tacz:attachments/scope_acog_ta31", "addon:public"))
                assertTrue(filter.contains(Identifier.parse(id)), id + " rejected by " + CommonAssetsManager.GSON.toJson(filter));
            assertFalse(filter.contains(Identifier.parse("addon:private/test")));
        }
    }

    @Test void invalidNonemptyWhitelistDoesNotBecomeAllowAllOnTheWire() {
        var filter = parse("{\"whitelist\":[\"Invalid Id\"]}");
        var id = Identifier.parse("tacz:gun/ak47");
        assertFalse(filter.contains(id));
        assertFalse(parse(CommonAssetsManager.GSON.toJson(filter)).contains(id));
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void contributionsAccumulateAcrossPacksButAnOverlayReplacesItsOwnBase(boolean reverse) throws Exception {
        Path pack = temp.resolve("base"), addon = temp.resolve("addon");
        write(pack.resolve(PATH), "{\"whitelist\":[\"obsolete:base_only\"]}");
        write(pack.resolve("active").resolve(PATH), "{\"whitelist\":[\"^tacz:.*$\"],\"blacklist\":[\"tacz:test\"]}");
        write(addon.resolve(PATH), "{\"whitelist\":[\"addon:entry\"],\"blacklist\":[\"^tacz:private/.*$\"]}");
        var a = new GunPackLoader.GunPack(pack, "base", List.of("active"));
        var b = new GunPackLoader.GunPack(addon, "addon");
        var supplier = GunPackLoader.supplier(null, reverse ? List.of(b, a) : List.of(a, b));
        var info = new PackLocationInfo("test", Component.literal("test"), PackSource.DEFAULT, Optional.empty());
        try (var resources = supplier.openResources(info, null).findFirst().orElseThrow()) {
            var listed = new HashMap<Identifier, IoSupplier<InputStream>>();
            resources.listResources(PackType.SERVER_DATA, "tacz", "recipe_filters", (id, in) -> assertNull(listed.put(id, in)));
            for (var source : List.of(listed.get(FILTER), resources.getResource(PackType.SERVER_DATA, FILTER))) {
                try (var in = source.get()) {
                    var filter = parse(new String(in.readAllBytes(), StandardCharsets.UTF_8));
                    assertAll(
                            () -> assertTrue(filter.contains(Identifier.parse("tacz:gun/ak47")), "default pack lost"),
                            () -> assertTrue(filter.contains(Identifier.parse("addon:entry")), "add-on lost"),
                            () -> assertFalse(filter.contains(Identifier.parse("obsolete:base_only")), "disabled base layer"),
                            () -> assertFalse(filter.contains(Identifier.parse("other:entry")), "whitelist was widened"),
                            () -> assertFalse(filter.contains(Identifier.parse("tacz:test")), "default blacklist lost"),
                            () -> assertFalse(filter.contains(Identifier.parse("tacz:private/test")), "add-on blacklist lost"));
                }
            }
        }
    }

    private static void write(Path path, String text) throws Exception {
        Files.createDirectories(path.getParent());
        Files.writeString(path, text);
    }
}
