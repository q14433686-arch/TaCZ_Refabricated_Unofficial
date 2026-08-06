package com.tacz.guns.industry.reference;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.tacz.guns.GunMod;
import com.tacz.guns.config.sync.SyncConfig;
import com.tacz.guns.industry.IndustryProfileManager;
import com.tacz.guns.resource.ICommonResourceProvider;
import com.tacz.guns.resource.index.CommonGunIndex;
import net.minecraft.resources.Identifier;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Synthesises honest GUI-based industrial fallback recipes for audited guns.
 *
 * <p>Default/declared platforms retain their high-fidelity Create terminals.
 * For a third-party gun whose result id resolves to a real loaded GunIndex,
 * this factory does not invent a receiver geometry or pretend to know its feed
 * device. It creates a clearly labelled <em>surveyed platform</em> dossier,
 * production template and five-neutral-blank platform kit in the existing
 * Gunsmith Table GUI, then adds that kit/template/fixture to the gun's real
 * original material bill. The GUI is a genuine multi-slot operation; no list
 * of inputs is falsely placed on a Create Depot.</p>
 */
public final class SurveyedIndustryRecipeFactory {
    private static final String SURVEYING_PLATFORM = "surveying";
    private static final String SURVEY_ACTION = "surveyed";
    private static final String SURVEY_TIER = "surveyed";
    private static final String SURVEY_SCOPE = "surveyed";
    private static final String GROUP = "tacz:misc";
    private static final String TABLE_TYPE = "tacz:gun_smith_table_crafting";
    private static final String GENERATED_MARKER = "industry_surveyed_generated";
    private static final String FALLBACK_MARKER = "industry_surveyed_fallback";

    private SurveyedIndustryRecipeFactory() {
    }

    public static Result apply(Map<Identifier, JsonElement> source, Set<Identifier> declaredAssemblyRecipes,
                               ICommonResourceProvider assets) {
        if (!IndustryProfileManager.isCreateFlyProfileActive()
                || SyncConfig.AUTO_DISCOVER_INDUSTRY_REPLACEMENTS == null
                || !SyncConfig.AUTO_DISCOVER_INDUSTRY_REPLACEMENTS.get()) {
            return new Result(source, 0, 0);
        }

        Map<Identifier, JsonElement> output = new LinkedHashMap<>();
        Map<Identifier, SurveyedPlatform> platforms = new LinkedHashMap<>();
        int transformed = 0;
        for (Map.Entry<Identifier, JsonElement> entry : source.entrySet()) {
            JsonElement raw = entry.getValue();
            SurveyedPlatform platform = declaredAssemblyRecipes.contains(entry.getKey())
                    ? null : surveyedPlatform(raw, assets);
            if (platform == null) {
                output.put(entry.getKey(), raw);
                continue;
            }
            JsonObject recipe = raw.getAsJsonObject().deepCopy();
            appendSurveyedFinalMaterials(recipe, platform);
            output.put(entry.getKey(), recipe);
            platforms.putIfAbsent(platform.gunId(), platform);
            transformed++;
        }

        int commissions = 0;
        for (SurveyedPlatform platform : platforms.values()) {
            commissions += putIfAbsent(output, dossierId(platform), dossierRecipe(platform)) ? 1 : 0;
            commissions += putIfAbsent(output, templateId(platform), templateRecipe(platform)) ? 1 : 0;
            commissions += putIfAbsent(output, kitId(platform), kitRecipe(platform)) ? 1 : 0;
        }
        if (transformed > 0) {
            GunMod.LOGGER.info(
                    "Synthesized {} surveyed industrial gun fallback(s) and {} Gunsmith Table survey operation(s) for {} audited platform(s).",
                    transformed, commissions, platforms.size()
            );
        }
        return new Result(Map.copyOf(output), platforms.size(), transformed);
    }

    private static boolean putIfAbsent(Map<Identifier, JsonElement> output, Identifier id, JsonObject recipe) {
        if (output.containsKey(id)) {
            GunMod.LOGGER.warn("Skipping generated surveyed industry operation {} because a data pack already owns that recipe id.", id);
            return false;
        }
        output.put(id, recipe);
        return true;
    }

    private static SurveyedPlatform surveyedPlatform(JsonElement raw, ICommonResourceProvider assets) {
        if (raw == null || !raw.isJsonObject()) {
            return null;
        }
        JsonObject recipe = raw.getAsJsonObject();
        if (recipe.has(FALLBACK_MARKER) || recipe.has(GENERATED_MARKER)
                || !recipe.has("materials") || !recipe.get("materials").isJsonArray()
                || recipe.getAsJsonArray("materials").size() == 0) {
            return null;
        }
        JsonObject result = object(recipe, "result");
        if (result == null || !"gun".equals(string(result, "type"))) {
            return null;
        }
        Identifier gunId = Identifier.tryParse(string(result, "id"));
        CommonGunIndex index = gunId == null ? null : assets.getGunIndex(gunId);
        if (gunId == null || index == null || index.getGunData() == null) {
            return null;
        }
        return new SurveyedPlatform(gunId, index.getGunData().getAmmoId(), index.getGunData().getAmmoAmount());
    }

    private static void appendSurveyedFinalMaterials(JsonObject recipe, SurveyedPlatform platform) {
        JsonArray materials = recipe.getAsJsonArray("materials");
        materials.add(material(partial("tacz:gun_component", surveyedKitTag(platform)), 1, true));
        materials.add(material(partial("tacz:gun_blueprint", productionTemplateTag(platform)), 1, false));
        materials.add(material(partial("tacz:press_die", surveyFixtureTag()), 1, false));
        recipe.addProperty(FALLBACK_MARKER, true);
    }

    private static JsonObject dossierRecipe(SurveyedPlatform platform) {
        return generatedTableRecipe(
                material(partial("tacz:gun_component_blank", surveyArchiveTag()), 1, true),
                material(partial("tacz:gun_blueprint", templateBlankTag()), 1, true),
                material(partial("tacz:press_die", surveyFixtureTag()), 1, false),
                customResult("tacz:gun_blueprint", masterDossierTag(platform))
        );
    }

    private static JsonObject templateRecipe(SurveyedPlatform platform) {
        return generatedTableRecipe(
                material(partial("tacz:gun_blueprint", templateBlankTag()), 1, true),
                material(partial("tacz:gun_blueprint", masterDossierTag(platform)), 1, false),
                material(partial("tacz:press_die", surveyFixtureTag()), 1, false),
                customResult("tacz:gun_blueprint", productionTemplateTag(platform))
        );
    }

    private static JsonObject kitRecipe(SurveyedPlatform platform) {
        JsonArray materials = new JsonArray();
        for (String blank : new String[]{"receiver", "bolt", "barrel", "trigger", "recoil"}) {
            materials.add(material(partial("tacz:gun_component_blank", structuralBlankTag(blank)), 1, true));
        }
        materials.add(material(partial("tacz:gun_blueprint", productionTemplateTag(platform)), 1, false));
        materials.add(material(partial("tacz:press_die", surveyFixtureTag()), 1, false));
        return generatedTableRecipe(materials, customResult("tacz:gun_component", surveyedKitTag(platform)));
    }

    private static JsonObject generatedTableRecipe(JsonObject first, JsonObject second, JsonObject third, JsonObject result) {
        JsonArray materials = new JsonArray();
        materials.add(first);
        materials.add(second);
        materials.add(third);
        return generatedTableRecipe(materials, result);
    }

    private static JsonObject generatedTableRecipe(JsonArray materials, JsonObject result) {
        JsonObject recipe = new JsonObject();
        recipe.addProperty("type", TABLE_TYPE);
        recipe.addProperty(GENERATED_MARKER, true);
        recipe.add("materials", materials);
        recipe.add("result", result);
        return recipe;
    }

    private static JsonObject customResult(String itemId, JsonObject nbt) {
        JsonObject item = new JsonObject();
        item.addProperty("item", itemId);
        item.addProperty("count", 1);
        item.add("nbt", nbt);
        JsonObject result = new JsonObject();
        result.addProperty("type", "custom");
        result.addProperty("group", GROUP);
        result.add("item", item);
        return result;
    }

    private static JsonObject material(JsonElement item, int count, boolean consume) {
        JsonObject material = new JsonObject();
        material.add("item", item);
        material.addProperty("count", count);
        material.addProperty("consume", consume);
        return material;
    }

    private static JsonObject partial(String itemId, JsonObject nbt) {
        JsonArray items = new JsonArray();
        items.add(itemId);
        JsonObject ingredient = new JsonObject();
        ingredient.addProperty("fabric:type", "forge:partial_nbt");
        ingredient.add("items", items);
        ingredient.add("nbt", nbt);
        return ingredient;
    }

    private static JsonObject templateBlankTag() {
        JsonObject tag = new JsonObject();
        tag.addProperty("IndustryPlatform", "tooling");
        tag.addProperty("IndustryPartKind", "template_blank");
        tag.addProperty("IndustryDisplayName", "item.tacz.gun_blueprint.blank");
        tag.addProperty("IndustryBlueprintRole", "blank");
        return tag;
    }

    private static JsonObject surveyArchiveTag() {
        JsonObject tag = new JsonObject();
        tag.addProperty("IndustryPlatform", SURVEYING_PLATFORM);
        tag.addProperty("IndustryPartKind", "survey_archive");
        tag.addProperty("IndustryDisplayName", "item.tacz.survey_archive");
        return tag;
    }

    private static JsonObject surveyFixtureTag() {
        JsonObject tag = new JsonObject();
        tag.addProperty("IndustryPlatform", SURVEYING_PLATFORM);
        tag.addProperty("IndustryPartKind", "survey_fixture");
        tag.addProperty("IndustryDisplayName", "item.tacz.press_die.survey_fixture");
        tag.addProperty("DieTargetKind", SURVEY_ACTION);
        return tag;
    }

    private static JsonObject structuralBlankTag(String kind) {
        JsonObject tag = new JsonObject();
        tag.addProperty("IndustryPlatform", "machining");
        tag.addProperty("IndustryPartKind", kind + "_blank");
        tag.addProperty("IndustryDisplayName", "item.tacz.gun_component_blank");
        return tag;
    }

    private static JsonObject masterDossierTag(SurveyedPlatform platform) {
        return blueprintTag(platform, "master", "item.tacz.gun_dossier.surveyed");
    }

    private static JsonObject productionTemplateTag(SurveyedPlatform platform) {
        return blueprintTag(platform, "production", "item.tacz.gun_template.surveyed");
    }

    private static JsonObject blueprintTag(SurveyedPlatform platform, String role, String displayName) {
        JsonObject tag = surveyedIdentity(platform, "blueprint", displayName);
        tag.addProperty("IndustryBlueprintTier", SURVEY_TIER);
        tag.addProperty("IndustryBlueprintRole", role);
        tag.addProperty("IndustryActionProfile", SURVEY_ACTION);
        tag.addProperty("IndustryToolingScope", SURVEY_SCOPE);
        return tag;
    }

    private static JsonObject surveyedKitTag(SurveyedPlatform platform) {
        JsonObject tag = surveyedIdentity(platform, "surveyed_platform_kit",
                "item.tacz.gun_component.surveyed_platform_kit");
        tag.addProperty("IndustryActionProfile", SURVEY_ACTION);
        tag.addProperty("IndustryToolingScope", SURVEY_SCOPE);
        return tag;
    }

    private static JsonObject surveyedIdentity(SurveyedPlatform platform, String kind, String displayName) {
        JsonObject tag = new JsonObject();
        tag.addProperty("IndustryPlatform", platform.platformId());
        tag.addProperty("IndustryPartKind", kind);
        tag.addProperty("IndustryDisplayName", displayName);
        tag.addProperty("IndustrySurveyGunId", platform.gunId().toString());
        if (platform.ammoId() != null) {
            tag.addProperty("IndustrySurveyAmmoId", platform.ammoId().toString());
        }
        tag.addProperty("IndustrySurveyCapacity", platform.capacity());
        return tag;
    }

    private static Identifier dossierId(SurveyedPlatform platform) {
        return generatedId("dossier", platform);
    }

    private static Identifier templateId(SurveyedPlatform platform) {
        return generatedId("template", platform);
    }

    private static Identifier kitId(SurveyedPlatform platform) {
        return generatedId("kit", platform);
    }

    private static Identifier generatedId(String operation, SurveyedPlatform platform) {
        return Identifier.fromNamespaceAndPath(GunMod.MOD_ID,
                "industry/survey/" + operation + "/" + platform.gunId().getNamespace() + "/" + platform.gunId().getPath());
    }

    private static JsonObject object(JsonObject parent, String key) {
        return parent.has(key) && parent.get(key).isJsonObject() ? parent.getAsJsonObject(key) : null;
    }

    private static String string(JsonObject object, String key) {
        return object.has(key) && object.get(key).isJsonPrimitive() ? object.get(key).getAsString() : "";
    }

    public record Result(Map<Identifier, JsonElement> recipes, int platforms, int transformedGunRecipes) {
    }

    private record SurveyedPlatform(Identifier gunId, Identifier ammoId, int capacity) {
        private String platformId() {
            return "surveyed/" + gunId.getNamespace() + "/" + gunId.getPath();
        }
    }
}
