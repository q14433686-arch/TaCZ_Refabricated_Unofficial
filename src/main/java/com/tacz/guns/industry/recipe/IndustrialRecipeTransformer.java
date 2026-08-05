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
 * Activates data-declared Create Fly terminal assembly without editing the
 * CC-BY-NC default gun pack.
 *
 * <p>A real Create terminal assembly is a {@code create:sequenced_assembly},
 * not a fake multi-item Depot recipe. The Depot/belt carries one workpiece at
 * a time; every deployment step supplies one separate held item. Therefore,
 * once the named process has been found and shape-validated, this transformer
 * removes the corresponding legacy gun-table recipe instead of rebuilding it
 * as a GUI/inventory shortcut. Unknown or malformed declarations deliberately
 * leave the legacy recipe intact.</p>
 */
public final class IndustrialRecipeTransformer {
    private IndustrialRecipeTransformer() {
    }

    /**
     * Return a new map only when the industrial profile is active. This makes
     * LEGACY byte-for-byte preserve existing loaded recipe elements.
     *
     * @param source                  accepted TACZ gun-table recipes
     * @param rawAssemblies           terminal declaration files, keyed by legacy table-recipe id
     * @param rawAmmoReplacements     old table-ammo declarations
     * @param allRawRecipes           unfiltered {@code recipe/**} data used to verify named Create processes
     */
    public static Map<Identifier, JsonElement> transform(Map<Identifier, JsonElement> source,
                                                          Map<Identifier, JsonElement> rawAssemblies,
                                                          Map<Identifier, JsonElement> rawAmmoReplacements,
                                                          Map<Identifier, JsonElement> allRawRecipes) {
        if (!IndustryProfileManager.isCreateFlyProfileActive()) {
            return source;
        }

        Map<Identifier, JsonElement> transformed = new LinkedHashMap<>(source);
        Set<Identifier> replacedAmmoRecipes = legacyAmmoRecipeIds(rawAmmoReplacements);
        replacedAmmoRecipes.forEach(transformed::remove);

        int removedGunTableRecipes = 0;
        for (Map.Entry<Identifier, JsonElement> entry : rawAssemblies.entrySet()) {
            if (!transformed.containsKey(entry.getKey())) {
                continue;
            }
            IndustryAssemblyDefinition assembly = IndustryAssemblyDefinition.fromJson(entry.getValue());
            if (assembly == null) {
                GunMod.LOGGER.warn("Ignoring invalid industry assembly declaration {} and retaining legacy table recipe.",
                        entry.getKey());
                continue;
            }
            JsonElement rawProcess = allRawRecipes.get(assembly.getTerminalProcess());
            if (!isSingleWorkpieceSequencedAssembly(rawProcess)) {
                GunMod.LOGGER.warn(
                        "Industry assembly {} names {}, but it is missing or is not a one-workpiece Create sequenced assembly; retaining legacy table recipe.",
                        entry.getKey(), assembly.getTerminalProcess());
                continue;
            }
            transformed.remove(entry.getKey());
            removedGunTableRecipes++;
        }

        GunMod.LOGGER.info(
                "CREATE_FLY industry profile removed {} legacy gun-table terminal recipe(s) in favour of validated sequential assembly and removed {} legacy ammo table recipe(s).",
                removedGunTableRecipes, replacedAmmoRecipes.size());
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

    /**
     * Validate the physical invariant which matters for a Create Depot/belt:
     * every nested step handles exactly one flowing workpiece. A Deployer may
     * hold one separate ingredient, but no step may declare an {@code ingredients}
     * array that implies putting several distinct stacks on the Depot.
     *
     * <p>Pressing/filling steps are allowed for future content packs because
     * they still process only the transitional workpiece. Current built-ins use
     * deploying steps exclusively so their components and reusable blueprints
     * remain observable physical inputs.</p>
     */
    private static boolean isSingleWorkpieceSequencedAssembly(JsonElement raw) {
        if (raw == null || !raw.isJsonObject()) {
            return false;
        }
        JsonObject process = raw.getAsJsonObject();
        if (!"create:sequenced_assembly".equals(string(process, "type"))
                || !process.has("ingredient")
                || !process.has("transitional_item")
                || !process.has("result")
                || !process.has("sequence")
                || !process.get("sequence").isJsonArray()) {
            return false;
        }
        JsonArray sequence = process.getAsJsonArray("sequence");
        // Create Fly itself rejects a sequence whose expanded size is <= 1.
        if (sequence.size() < 2) {
            return false;
        }
        for (JsonElement rawStep : sequence) {
            if (!rawStep.isJsonObject()) {
                return false;
            }
            JsonObject step = rawStep.getAsJsonObject();
            // A multi-input Basin operation belongs outside the Depot sequence.
            if (step.has("ingredients")) {
                return false;
            }
            String type = string(step, "type");
            if ("create:deploying".equals(type)) {
                // The moving stack is target; the single Deployer-held material
                // is ingredient. They are never two stacks on the Depot.
                if (!isPlaceholder(step.get("target"))
                        || !step.has("ingredient")
                        || isPlaceholder(step.get("ingredient"))) {
                    return false;
                }
            } else if ("create:pressing".equals(type) || "create:filling".equals(type)) {
                // These nested operations use ingredient as the one moving
                // transitional workpiece and add no second Depot input.
                if (!isPlaceholder(step.get("ingredient")) || step.has("target")) {
                    return false;
                }
            } else {
                return false;
            }
        }
        return true;
    }

    private static boolean isPlaceholder(JsonElement element) {
        return element != null && element.isJsonPrimitive()
                && element.getAsJsonPrimitive().isString()
                && "$ingredient".equals(element.getAsString());
    }

    private static String string(JsonObject object, String key) {
        return object.has(key) && object.get(key).isJsonPrimitive() && object.get(key).getAsJsonPrimitive().isString()
                ? object.get(key).getAsString() : "";
    }
}
