package com.tacz.probe;

import com.tacz.guns.GunMod;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.item.IAmmo;
import com.tacz.guns.api.item.IAttachment;
import com.tacz.guns.api.item.IGun;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.packs.resources.ResourceManager;

import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/** Compare *all* shipped IDs to the indexes consumers actually see, not just a sentinel gun. */
public class CatalogChecks {
    static void require(boolean passed, String message) { if (!passed) throw new AssertionError(message); }

    public static void loadedCatalog(net.minecraft.gametest.framework.GameTestHelper helper) {
        server(helper.getLevel().getServer());
        helper.succeed();
    }

    private static Set<Identifier> files(ResourceManager resources, String dir) {
        var names = FileToIdConverter.json(dir);
        return names.listMatchingResources(resources).keySet().stream().filter(id -> id.getNamespace().equals("tacz"))
                .map(names::fileToId).collect(Collectors.toSet());
    }

    private static <T> void catalog(MinecraftServer server, Set<Map.Entry<Identifier, T>> entries, int expected, String dir) {
        var decoded = entries.stream().map(Map.Entry::getKey).filter(id -> id.getNamespace().equals("tacz")).collect(Collectors.toSet());
        var shipped = files(server.getResourceManager(), "index/" + dir);
        if (shipped.size() != expected || !shipped.equals(decoded)) {
            var missing = new java.util.TreeSet<>(shipped);
            missing.removeAll(decoded);
            throw new AssertionError(dir + ": expected " + expected + ", shipped " + shipped.size() + ", decoded " + decoded.size() + ", missing " + missing);
        }
    }

    static void server(MinecraftServer server) {
        catalog(server, TimelessAPI.getAllCommonGunIndex(), 70, "guns");
        catalog(server, TimelessAPI.getAllCommonAmmoIndex(), 25, "ammo");
        catalog(server, TimelessAPI.getAllCommonAttachmentIndex(), 126, "attachments");
        catalog(server, TimelessAPI.getAllCommonBlockIndex(), 3, "blocks");
        catalog(server, TimelessAPI.getAllCommonSlotAdapterIndex(), 5, "slot_adapters");
        var advances = files(server.getResourceManager(), "advancement");
        long unlocks = advances.stream().filter(id -> id.getPath().startsWith("recipes/")).count();
        if (advances.size() - unlocks != 23 || unlocks != 17 || server.getAdvancements().get(Identifier.parse("lrtactical:recipes/smith_table")) == null)
            throw new AssertionError("Expected 23 pack advancements and 17+1 ordinary crafting unlocks: " + advances);
        for (var id : advances) if (server.getAdvancements().get(id) == null) throw new AssertionError("Undecoded advancement " + id);
        int recipes = 0;
        for (var id : files(server.getResourceManager(), "recipe")) {
            var path = id.getPath();
            if (!(path.startsWith("ammo/") || path.startsWith("attachments/") || path.startsWith("gun/"))) continue;
            var r = TimelessAPI.getRecipe(id).orElseThrow(() -> new AssertionError("Unloaded recipe " + id));
            var stack = r.getOutput();
            var expected = id.withPath(path.substring(path.indexOf('/') + 1));
            var product = switch (stack.getItem()) {
                case IGun g -> g.getGunId(stack);
                case IAmmo a -> a.getAmmoId(stack);
                case IAttachment a -> a.getAttachmentId(stack);
                default -> throw new AssertionError("Wrong output for " + id + ": " + stack);
            };
            if (!expected.equals(product) || r.getInputs().isEmpty() || r.getInputs().stream().anyMatch(i -> !i.isResolved() || i.getCount() <= 0))
                throw new AssertionError("Invalid ingredients or output for " + id + ": " + product);
            recipes++;
        }
        if (recipes != 202) throw new AssertionError("Expected 202 default table recipes, got " + recipes);
        GunMod.LOGGER.info("PORT_CATALOG: server loaded 229 live gun/ammo/attachment/table/adapter indexes, 202 typed recipes, 23 pack advancements and 18 crafting unlocks (native dye recipe)");
    }
}
