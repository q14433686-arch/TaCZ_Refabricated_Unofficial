package com.tacz.guns.industry.recipe;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.tacz.guns.GunMod;
import com.tacz.guns.industry.IndustryProfileManager;
import net.minecraft.resources.Identifier;

import java.util.HashSet;
import java.util.LinkedHashMap;
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

    private IndustrialRecipeTransformer() {
    }

    /**
     * Return a new map only when the industrial profile is active.  This makes
     * LEGACY byte-for-byte preserve existing loaded recipe elements.
     */
    public static Map<Identifier, JsonElement> transform(Map<Identifier, JsonElement> source,
                                                          Map<Identifier, JsonElement> rawAssemblies,
                                                          Map<Identifier, JsonElement> rawAmmoReplacements) {
        if (!IndustryProfileManager.isCreateFlyProfileActive()) {
            return source;
        }

        Map<Identifier, JsonElement> transformed = new LinkedHashMap<>(source);
        Set<Identifier> replacedAmmoRecipes = legacyAmmoRecipeIds(rawAmmoReplacements);
        replacedAmmoRecipes.forEach(transformed::remove);

        int rewritten = 0;
        for (Map.Entry<Identifier, JsonElement> entry : rawAssemblies.entrySet()) {
            JsonElement rawRecipe = transformed.get(entry.getKey());
            IndustryAssemblyDefinition assembly = IndustryAssemblyDefinition.fromJson(entry.getValue());
            if (rawRecipe == null || !rawRecipe.isJsonObject() || assembly == null) {
                continue;
            }
            JsonObject recipe = rawRecipe.getAsJsonObject().deepCopy();
            recipe.add("materials", assemblyMaterials(assembly));
            transformed.put(entry.getKey(), recipe);
            rewritten++;
        }

        GunMod.LOGGER.info("CREATE_FLY industry profile replaced {} built-in gun assembly recipe(s) and removed {} legacy ammo table recipe(s).",
                rewritten, replacedAmmoRecipes.size());
        return transformed;
    }

    private static Set<Identifier> legacyAmmoRecipeIds(Map<Identifier, JsonElement> rawAmmoReplacements) {
        Set<Identifier> ids = new HashSet<>();
        for (JsonElement raw : rawAmmoReplacements.values()) {
            if (!raw.isJsonObject()) {
                continue;
            }
            JsonObject object = raw.getAsJsonObject();
            JsonElement id = object.get("legacy_recipe");
            if (id != null && id.isJsonPrimitive() && id.getAsJsonPrimitive().isString()) {
                Identifier parsed = Identifier.tryParse(id.getAsString());
                if (parsed != null) {
                    ids.add(parsed);
                }
            }
        }
        return ids;
    }

    private static JsonArray assemblyMaterials(IndustryAssemblyDefinition assembly) {
        JsonArray materials = new JsonArray();
        // The blueprint is checked by partial NBT but deliberately retained.
        materials.add(material(partialNbt(BLUEPRINT_ITEM, assembly.getPlatform(), "blueprint", assembly.getBlueprintDisplayName()), 1, false));
        for (IndustryAssemblyDefinition.Component part : assembly.getComponents()) {
            materials.add(material(partialNbt(COMPONENT_ITEM, assembly.getPlatform(), part.kind(), part.displayName()), 1, true));
        }
        for (IndustryAssemblyDefinition.Material material : assembly.getMaterials()) {
            materials.add(material(material.itemId(), material.count(), true));
        }
        return materials;
    }

    private static JsonObject partialNbt(String itemId, String platform, String kind, String displayName) {
        JsonObject ingredient = new JsonObject();
        ingredient.addProperty("type", "forge:partial_nbt");
        ingredient.addProperty("item", itemId);
        JsonObject nbt = new JsonObject();
        nbt.addProperty("IndustryPlatform", platform);
        nbt.addProperty("IndustryPartKind", kind);
        nbt.addProperty("IndustryDisplayName", displayName);
        ingredient.add("nbt", nbt);
        return ingredient;
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
}
