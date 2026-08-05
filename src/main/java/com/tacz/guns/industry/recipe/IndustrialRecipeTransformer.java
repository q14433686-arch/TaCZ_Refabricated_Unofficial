package com.tacz.guns.industry.recipe;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.tacz.guns.GunMod;
import com.tacz.guns.industry.IndustryProfileManager;
import net.minecraft.resources.Identifier;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Rewrites only TACZ's built-in terminal recipes when CREATE_FLY is active.
 *
 * <p>The default gun pack is CC BY-NC-ND, so this transformation deliberately
 * happens after pack loading in GPL code rather than editing its JSON files.
 * Third-party namespaces and unknown default recipe ids are untouched.  The
 * transformed map is also the map synchronised to clients, keeping the table,
 * JEI and REI on the same source of truth.</p>
 */
public final class IndustrialRecipeTransformer {
    private static final String COMPONENT_ITEM = "tacz:gun_component";
    private static final String BLUEPRINT_ITEM = "tacz:gun_blueprint";

    /** Ammunition now comes from Create Fly batch-processing, not the gun table. */
    private static final Set<Identifier> REPLACED_AMMO_RECIPES = Set.of(
            id("ammo/9mm"),
            id("ammo/556x45"),
            id("ammo/762x39"),
            id("ammo/12g")
    );

    private static final Map<Identifier, GunAssembly> GUN_ASSEMBLIES = Map.of(
            id("gun/ak47"), new GunAssembly("ak", List.of(
                    "receiver", "bolt", "barrel", "trigger", "recoil"),
                    List.of(new PlainMaterial("minecraft:oak_planks", 4))),
            id("gun/m4a1"), new GunAssembly("ar", List.of(
                    "receiver", "bolt", "barrel", "trigger", "recoil"),
                    List.of(new PlainMaterial("minecraft:oak_planks", 2), new PlainMaterial("minecraft:leather", 2))),
            id("gun/glock_17"), new GunAssembly("glock", List.of(
                    "frame", "slide", "barrel", "trigger", "recoil"),
                    List.of(new PlainMaterial("minecraft:leather", 2)))
    );

    private IndustrialRecipeTransformer() {
    }

    /**
     * Return a new map only when the industrial profile is active.  This makes
     * LEGACY byte-for-byte preserve existing loaded recipe elements.
     */
    public static Map<Identifier, JsonElement> transform(Map<Identifier, JsonElement> source) {
        if (!IndustryProfileManager.isCreateFlyProfileActive()) {
            return source;
        }

        Map<Identifier, JsonElement> transformed = new LinkedHashMap<>(source);
        REPLACED_AMMO_RECIPES.forEach(transformed::remove);

        int rewritten = 0;
        for (Map.Entry<Identifier, GunAssembly> entry : GUN_ASSEMBLIES.entrySet()) {
            JsonElement raw = transformed.get(entry.getKey());
            if (raw == null || !raw.isJsonObject()) {
                continue;
            }
            JsonObject recipe = raw.getAsJsonObject().deepCopy();
            recipe.add("materials", assemblyMaterials(entry.getValue()));
            transformed.put(entry.getKey(), recipe);
            rewritten++;
        }

        GunMod.LOGGER.info("CREATE_FLY industry profile replaced {} built-in gun assembly recipe(s) and removed {} legacy ammo table recipe(s).",
                rewritten, REPLACED_AMMO_RECIPES.size());
        return transformed;
    }

    private static JsonArray assemblyMaterials(GunAssembly assembly) {
        JsonArray materials = new JsonArray();
        // The blueprint is checked by partial NBT but deliberately retained.
        materials.add(material(partialNbt(BLUEPRINT_ITEM, assembly.platform(), "blueprint"), 1, false));
        for (String part : assembly.parts()) {
            materials.add(material(partialNbt(COMPONENT_ITEM, assembly.platform(), part), 1, true));
        }
        for (PlainMaterial material : assembly.extraMaterials()) {
            materials.add(material(material.itemId(), material.count(), true));
        }
        return materials;
    }

    private static JsonObject partialNbt(String itemId, String platform, String kind) {
        JsonObject ingredient = new JsonObject();
        ingredient.addProperty("type", "forge:partial_nbt");
        ingredient.addProperty("item", itemId);
        JsonObject nbt = new JsonObject();
        nbt.addProperty("IndustryPlatform", platform);
        nbt.addProperty("IndustryPartKind", kind);
        nbt.addProperty("IndustryDisplayName", displayNameKey(platform, kind));
        ingredient.add("nbt", nbt);
        return ingredient;
    }

    private static String displayNameKey(String platform, String kind) {
        return "blueprint".equals(kind)
                ? "item.tacz.gun_blueprint." + platform
                : "item.tacz.gun_component." + platform + "_" + kind;
    }

    private static JsonObject material(JsonElement item, int count, boolean consume) {
        JsonObject material = new JsonObject();
        material.add("item", item);
        material.addProperty("count", Math.max(1, count));
        if (!consume) {
            material.addProperty("consume", false);
        }
        return material;
    }

    private static JsonObject material(String itemId, int count, boolean consume) {
        return material(new com.google.gson.JsonPrimitive(itemId), count, consume);
    }

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(GunMod.MOD_ID, path);
    }

    private record GunAssembly(String platform, List<String> parts, List<PlainMaterial> extraMaterials) {
    }

    private record PlainMaterial(String itemId, int count) {
    }
}
