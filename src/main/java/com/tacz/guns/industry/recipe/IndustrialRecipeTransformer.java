package com.tacz.guns.industry.recipe;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.tacz.guns.GunMod;
import com.tacz.guns.config.sync.SyncConfig;
import com.tacz.guns.industry.IndustryProfileManager;
import com.tacz.guns.resource.CommonAssetsManager;
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
 * as a GUI/inventory shortcut. Unknown declared assemblies deliberately leave
 * their legacy recipe intact; uncurated recipes without a declaration can be
 * given the separate runtime industrial fallback gate.</p>
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
        // A declared platform is never silently downgraded to the generic
        // auto-discovery path: a broken declared sequence deliberately retains
        // its legacy route so its author can see and correct the declaration.
        Set<Identifier> declaredAssemblyRecipes = new HashSet<>(rawAssemblies.keySet());
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

        AutoFallbackStats autoFallbacks = autoDiscoverFallbacks(transformed, declaredAssemblyRecipes);
        GunMod.LOGGER.info(
                "CREATE_FLY industry profile removed {} legacy gun-table terminal recipe(s) in favour of validated sequential assembly, removed {} legacy ammo table recipe(s), and synthesized {} gun / {} ammo / {} attachment fallback replacement(s); {} unresolved result identity recipe(s) retained unchanged.",
                removedGunTableRecipes, replacedAmmoRecipes.size(), autoFallbacks.guns(), autoFallbacks.ammo(),
                autoFallbacks.attachments(), autoFallbacks.unresolved());
        return transformed;
    }

    /**
     * Runtime fallback for every recognised but uncurated gun-pack table
     * recipe. This is intentionally a material-gate replacement, not a fake
     * attempt to infer a real receiver/bolt geometry from arbitrary JSON.
     * Curated platform declarations retain the high-fidelity component path;
     * unknown third-party guns, ammo and attachments remain craftable but gain
     * deterministic industrial intermediates without requiring their author or
     * a player to run an external generator.
     */
    private static AutoFallbackStats autoDiscoverFallbacks(Map<Identifier, JsonElement> recipes,
                                                           Set<Identifier> declaredAssemblyRecipes) {
        if (SyncConfig.AUTO_DISCOVER_INDUSTRY_REPLACEMENTS == null
                || !SyncConfig.AUTO_DISCOVER_INDUSTRY_REPLACEMENTS.get()) {
            return AutoFallbackStats.EMPTY;
        }
        int guns = 0;
        int ammo = 0;
        int attachments = 0;
        int unresolved = 0;
        for (Map.Entry<Identifier, JsonElement> entry : recipes.entrySet()) {
            if (declaredAssemblyRecipes.contains(entry.getKey()) || !entry.getValue().isJsonObject()) {
                continue;
            }
            JsonObject recipe = entry.getValue().getAsJsonObject();
            // Surveyed fallbacks already add a real platform kit, production
            // template and retained survey fixture to the original material
            // bill. Do not dilute that contract by appending the old generic
            // raw-material gate a second time.
            if (recipe.has("industry_surveyed_fallback")) {
                continue;
            }
            JsonObject result = object(recipe, "result");
            if (result == null) {
                continue;
            }
            String type = string(result, "type");
            if (isIndustrialResultType(type) && !hasLoadedResultIdentity(type, result)) {
                // A typoed/cross-pack id must remain visible as its original
                // recipe until an explicit industry/id_aliases entry repairs
                // it. Adding industrial costs to a result that cannot exist
                // would turn an upstream data error into a misleading gate.
                unresolved++;
                GunMod.LOGGER.warn("Skipping automatic industrial fallback for {}: unresolved {} result id '{}'.",
                        entry.getKey(), type, string(result, "id"));
                continue;
            }
            int materialWeight = materialWeight(recipe);
            int resultCount = resultCount(result);
            boolean changed = switch (type) {
                case "gun" -> {
                    appendMaterial(recipe, "tacz:high_carbon_steel_plate", clamp(1, 6, (materialWeight + 23) / 24));
                    appendMaterial(recipe, "tacz:gun_component_blank", clamp(1, 4, (materialWeight + 39) / 40));
                    appendMaterial(recipe, "create:brass_sheet", 1);
                    guns++;
                    yield true;
                }
                case "ammo" -> {
                    // Legacy gun packs commonly output a stack/batch. The
                    // fallback must charge one industrial cartridge set per
                    // result item rather than turn a single blank into 18+ rounds.
                    appendMaterial(recipe, "tacz:cartridge_case_blank", resultCount);
                    appendMaterial(recipe, "tacz:projectile_blank", resultCount);
                    appendMaterial(recipe, "tacz:primer", resultCount);
                    appendMaterial(recipe, "tacz:industrial_propellant", resultCount);
                    ammo++;
                    yield true;
                }
                case "attachment" -> {
                    appendMaterial(recipe, "tacz:high_carbon_steel_plate", clamp(1, 3, (materialWeight + 31) / 32));
                    appendMaterial(recipe, "create:brass_sheet", 1);
                    attachments++;
                    yield true;
                }
                default -> false;
            };
            if (changed) {
                recipe.addProperty("industry_auto_fallback", true);
            }
        }
        return new AutoFallbackStats(guns, ammo, attachments, unresolved);
    }

    private static boolean isIndustrialResultType(String type) {
        return "gun".equals(type) || "ammo".equals(type) || "attachment".equals(type);
    }

    private static boolean hasLoadedResultIdentity(String type, JsonObject result) {
        Identifier id = Identifier.tryParse(string(result, "id"));
        if (id == null) {
            return false;
        }
        return switch (type) {
            case "gun" -> CommonAssetsManager.get().getGunIndex(id) != null;
            case "ammo" -> CommonAssetsManager.get().getAmmoIndex(id) != null;
            case "attachment" -> CommonAssetsManager.get().getAttachmentIndex(id) != null;
            default -> false;
        };
    }

    private static int materialWeight(JsonObject recipe) {
        JsonArray materials = recipe.has("materials") && recipe.get("materials").isJsonArray()
                ? recipe.getAsJsonArray("materials") : new JsonArray();
        int weight = 0;
        for (JsonElement raw : materials) {
            if (!raw.isJsonObject()) {
                continue;
            }
            JsonObject material = raw.getAsJsonObject();
            try {
                weight += material.has("count") ? Math.max(1, material.get("count").getAsInt()) : 1;
            } catch (RuntimeException ignored) {
                weight++;
            }
        }
        return Math.max(1, weight);
    }

    private static int resultCount(JsonObject result) {
        try {
            return result.has("count") ? clamp(result.get("count").getAsInt(), 1, 99) : 1;
        } catch (RuntimeException ignored) {
            return 1;
        }
    }

    private static void appendMaterial(JsonObject recipe, String itemId, int count) {
        JsonArray materials;
        if (recipe.has("materials") && recipe.get("materials").isJsonArray()) {
            materials = recipe.getAsJsonArray("materials");
        } else {
            materials = new JsonArray();
            recipe.add("materials", materials);
        }
        // Reusing a direct material id means increase its amount instead of
        // creating a duplicate row in the gun-smith table UI.
        for (JsonElement raw : materials) {
            if (!raw.isJsonObject()) {
                continue;
            }
            JsonObject material = raw.getAsJsonObject();
            JsonElement item = material.get("item");
            if (item != null && item.isJsonPrimitive() && item.getAsJsonPrimitive().isString()
                    && itemId.equals(item.getAsString())) {
                int old = 1;
                try {
                    old = material.has("count") ? material.get("count").getAsInt() : 1;
                } catch (RuntimeException ignored) {
                    // Replace malformed count with the generated safe value.
                }
                material.addProperty("count", Math.max(1, old) + Math.max(1, count));
                return;
            }
        }
        JsonObject material = new JsonObject();
        material.addProperty("item", itemId);
        material.addProperty("count", Math.max(1, count));
        materials.add(material);
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static JsonObject object(JsonObject parent, String key) {
        return parent.has(key) && parent.get(key).isJsonObject() ? parent.getAsJsonObject(key) : null;
    }

    private record AutoFallbackStats(int guns, int ammo, int attachments, int unresolved) {
        private static final AutoFallbackStats EMPTY = new AutoFallbackStats(0, 0, 0, 0);
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
