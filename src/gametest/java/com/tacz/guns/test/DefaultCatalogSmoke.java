package com.tacz.guns.test;

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
final class DefaultCatalogSmoke {
    private static Set<Identifier> files(ResourceManager resources, String dir) {
        var names = FileToIdConverter.json(dir);
        return names.listMatchingResources(resources).keySet().stream().filter(id -> id.getNamespace().equals("tacz"))
                .map(names::fileToId).collect(Collectors.toSet());
    }

    static <T> void catalog(Set<Map.Entry<Identifier, T>> entries, int expected, String kind) {
        if (entries.stream().filter(e -> e.getKey().getNamespace().equals("tacz")).count() != expected)
            throw new AssertionError("Incomplete default " + kind + " catalog (expected " + expected + "): " + entries.stream().map(Map.Entry::getKey).toList());
    }

    static void server(MinecraftServer server) {
        catalog(TimelessAPI.getAllCommonGunIndex(), 70, "gun");
        catalog(TimelessAPI.getAllCommonAmmoIndex(), 25, "ammo");
        catalog(TimelessAPI.getAllCommonAttachmentIndex(), 126, "attachment");
        catalog(TimelessAPI.getAllCommonBlockIndex(), 3, "block");
        catalog(TimelessAPI.getAllCommonSlotAdapterIndex(), 5, "adapter");
        for (var e : TimelessAPI.getAllCommonGunIndex()) {
            var index = e.getValue();
            if (e.getKey().getNamespace().equals("tacz") && index.getGunData().getScript() != null && index.getScript() == null)
                throw new AssertionError("Unloaded logic script: " + e.getKey());
        }
        var advances = files(server.getResourceManager(), "advancement");
        if (advances.size() != 23) throw new AssertionError("Expected 23 default advancements: " + advances);
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
        GunMod.LOGGER.info("PORT_CATALOG: server loaded 229 indexes, 202 typed recipes, logic scripts and 23 advancements");
    }
}
