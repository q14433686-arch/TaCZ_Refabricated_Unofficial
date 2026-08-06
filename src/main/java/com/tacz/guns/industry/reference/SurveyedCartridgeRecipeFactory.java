package com.tacz.guns.industry.reference;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.tacz.guns.GunMod;
import com.tacz.guns.config.sync.SyncConfig;
import com.tacz.guns.industry.IndustryProfileManager;
import com.tacz.guns.resource.ICommonResourceProvider;
import com.tacz.guns.resource.index.CommonAmmoIndex;
import com.tacz.guns.resource.pojo.data.gun.FeedType;
import net.minecraft.resources.Identifier;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Generates a conservative cartridge-machine path for audited third-party
 * loose-ammo table results.
 *
 * <p>Only non-{@code tacz} ammo with a real loaded AmmoIndex and at least one
 * normal magazine/manual gun consumer is eligible. Fuel/inventory consumers
 * remain outside this path. The generated gauge/case/projectile commissions
 * preserve the legacy ammo material bill once as a real Gunsmith Table survey
 * operation; subsequent batches use exact NBT case/projectile stacks in the
 * dedicated four-slot cartridge assembler.</p>
 *
 * <p>Eligible normal-gun candidates use a real generic spent-case loop:
 * {@code eject_case=true} plus a measured reconditioning commission. The
 * shell's identity is exact, while its visual is deliberately a standard
 * material family until an explicit ammo reference supplies calibre art or
 * exceptional non-ejecting semantics.</p>
 */
public final class SurveyedCartridgeRecipeFactory {
    private static final String TABLE_TYPE = "tacz:gun_smith_table_crafting";
    private static final String GROUP = "tacz:misc";
    private static final String MARKER = "industry_surveyed_ammo_fallback";
    private static final String GENERATED_MARKER = "industry_surveyed_generated";
    private static final String SURVEYING_PLATFORM = "surveying";
    private static final String SURVEY_ACTION = "surveyed";
    private static final String PROJECTILE_TYPE = "surveyed";

    private SurveyedCartridgeRecipeFactory() {
    }

    public static Result apply(Map<Identifier, JsonElement> source, ICommonResourceProvider assets) {
        if (!IndustryProfileManager.isCreateFlyProfileActive()
                || SyncConfig.AUTO_DISCOVER_INDUSTRY_REPLACEMENTS == null
                || !SyncConfig.AUTO_DISCOVER_INDUSTRY_REPLACEMENTS.get()) {
            return new Result(source, Map.of(), 0);
        }
        Map<Identifier, JsonElement> output = new LinkedHashMap<>();
        Map<Identifier, SurveyedCartridge> cartridges = new LinkedHashMap<>();
        int replaced = 0;
        for (Map.Entry<Identifier, JsonElement> entry : source.entrySet()) {
            SurveyedCartridge cartridge = surveyedCartridge(entry.getKey(), entry.getValue(), assets);
            if (cartridge == null || cartridges.containsKey(cartridge.ammoId())) {
                output.put(entry.getKey(), entry.getValue());
                continue;
            }
            if (output.containsKey(gaugeId(cartridge)) || output.containsKey(caseId(cartridge))
                    || output.containsKey(projectileId(cartridge)) || output.containsKey(reconditionId(cartridge))) {
                GunMod.LOGGER.warn("Skipping surveyed cartridge fallback for {} because a data pack owns one of its generated commission ids.",
                        cartridge.ammoId());
                output.put(entry.getKey(), entry.getValue());
                continue;
            }
            JsonObject original = entry.getValue().getAsJsonObject().deepCopy();
            original.addProperty(MARKER, true);
            output.put(entry.getKey(), original);
            cartridges.put(cartridge.ammoId(), cartridge);
            output.put(gaugeId(cartridge), gaugeRecipe(cartridge));
            output.put(caseId(cartridge), caseRecipe(cartridge));
            output.put(projectileId(cartridge), projectileRecipe(cartridge));
            output.put(reconditionId(cartridge), reconditionRecipe(cartridge));
            replaced++;
        }

        Map<Identifier, JsonElement> definitions = new LinkedHashMap<>();
        for (SurveyedCartridge cartridge : cartridges.values()) {
            definitions.put(definitionId(cartridge), definition(cartridge));
        }
        if (replaced > 0) {
            GunMod.LOGGER.info("Synthesized {} surveyed cartridge fallback(s), {} survey/reconditioning commission(s), and {} dedicated assembler definition(s).",
                    replaced, replaced * 4, definitions.size());
        }
        return new Result(Map.copyOf(output), Map.copyOf(definitions), replaced);
    }

    private static SurveyedCartridge surveyedCartridge(Identifier recipeId, JsonElement raw,
                                                        ICommonResourceProvider assets) {
        if (raw == null || !raw.isJsonObject()) {
            return null;
        }
        JsonObject recipe = raw.getAsJsonObject();
        if (recipe.has(MARKER) || recipe.has(GENERATED_MARKER)
                || !recipe.has("materials") || !recipe.get("materials").isJsonArray()
                || recipe.getAsJsonArray("materials").size() == 0) {
            return null;
        }
        JsonObject result = object(recipe, "result");
        if (result == null || !"ammo".equals(string(result, "type"))) {
            return null;
        }
        Identifier ammoId = Identifier.tryParse(string(result, "id"));
        if (ammoId == null || "tacz".equals(ammoId.getNamespace())) {
            return null;
        }
        CommonAmmoIndex ammoIndex = assets.getAmmoIndex(ammoId);
        if (ammoIndex == null || !hasNormalGunConsumer(ammoId, assets)) {
            return null;
        }
        int requested = positive(result, "count", 1);
        int batch = Math.clamp(requested, 1, Math.max(1, ammoIndex.getStackSize()));
        String caliber = "surveyed/" + ammoId.getNamespace() + "/" + ammoId.getPath();
        int propellant = Math.max(1, (batch + 7) / 8);
        return new SurveyedCartridge(recipeId, ammoId, caliber, batch, propellant,
                recipe.getAsJsonArray("materials").deepCopy());
    }

    private static boolean hasNormalGunConsumer(Identifier ammoId, ICommonResourceProvider assets) {
        boolean normal = false;
        for (var entry : assets.getAllGuns()) {
            var data = entry.getValue() == null ? null : entry.getValue().getGunData();
            if (data == null || !ammoId.equals(data.getAmmoId()) || data.getReloadData() == null) {
                continue;
            }
            FeedType feed = data.getReloadData().getType();
            if (feed == FeedType.FUEL || feed == FeedType.INVENTORY) {
                continue;
            }
            if (feed == FeedType.MAGAZINE || feed == FeedType.MANUAL) {
                normal = true;
            }
        }
        return normal;
    }

    private static JsonObject gaugeRecipe(SurveyedCartridge cartridge) {
        JsonArray materials = cartridge.legacyMaterials().deepCopy();
        materials.add(material(partial("tacz:press_die", cartridgeGaugeBlankTag()), 1, true));
        materials.add(material(partial("tacz:press_die", surveyFixtureTag()), 1, false));
        return tableRecipe(materials, customResult("tacz:press_die", gaugeTag(cartridge)));
    }

    private static JsonObject caseRecipe(SurveyedCartridge cartridge) {
        JsonArray materials = new JsonArray();
        materials.add(material(partial("tacz:cartridge_case_blank", caseBlankTag()), 1, true));
        materials.add(material(partial("tacz:press_die", gaugeTag(cartridge)), 1, false));
        materials.add(material(partial("tacz:press_die", surveyFixtureTag()), 1, false));
        return tableRecipe(materials, customResult("tacz:cartridge_case", caseTag(cartridge)));
    }

    private static JsonObject projectileRecipe(SurveyedCartridge cartridge) {
        JsonArray materials = new JsonArray();
        materials.add(material(partial("tacz:projectile_blank", projectileBlankTag()), 1, true));
        materials.add(material(partial("tacz:press_die", gaugeTag(cartridge)), 1, false));
        materials.add(material(partial("tacz:press_die", surveyFixtureTag()), 1, false));
        return tableRecipe(materials, customResult("tacz:projectile_core", projectileTag(cartridge)));
    }

    private static JsonObject reconditionRecipe(SurveyedCartridge cartridge) {
        JsonArray materials = new JsonArray();
        materials.add(material(partial("tacz:cartridge_case", spentCaseTag(cartridge)), 1, true));
        materials.add(material(partial("tacz:press_die", gaugeTag(cartridge)), 1, false));
        materials.add(material(partial("tacz:press_die", surveyFixtureTag()), 1, false));
        return tableRecipe(materials, customResult("tacz:cartridge_case", caseTag(cartridge)));
    }

    private static JsonObject definition(SurveyedCartridge cartridge) {
        JsonObject definition = new JsonObject();
        definition.addProperty("case_item", "tacz:cartridge_case");
        definition.addProperty("case_caliber", cartridge.caliber());
        definition.addProperty("case_display_name", "item.tacz.cartridge_case.surveyed");
        definition.addProperty("projectile_item", "tacz:projectile_core");
        definition.addProperty("projectile_caliber", cartridge.caliber());
        definition.addProperty("projectile_type", PROJECTILE_TYPE);
        definition.addProperty("projectile_display_name", "item.tacz.projectile_core.surveyed");
        definition.addProperty("primer_item", "tacz:primer");
        definition.addProperty("propellant_item", "tacz:industrial_propellant");
        definition.addProperty("ammo", cartridge.ammoId().toString());
        definition.addProperty("count", cartridge.batch());
        definition.addProperty("case_count", cartridge.batch());
        definition.addProperty("projectile_count", cartridge.batch());
        definition.addProperty("primer_count", cartridge.batch());
        definition.addProperty("propellant_count", cartridge.propellant());
        // Candidates reach this path only after a normal magazine/manual gun
        // consumer was observed; fuel/inventory routes were rejected above.
        // The case remains visibly generic, but it is a real exact-calibre
        // ItemStack and can be reconditioned through the generated commission.
        definition.addProperty("eject_case", true);
        definition.addProperty("spent_case_display_name", "item.tacz.cartridge_case.spent_surveyed");
        definition.addProperty(GENERATED_MARKER, true);
        return definition;
    }

    private static JsonObject tableRecipe(JsonArray materials, JsonObject result) {
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

    private static JsonObject cartridgeGaugeBlankTag() {
        JsonObject tag = new JsonObject();
        tag.addProperty("IndustryPlatform", "ammunition");
        tag.addProperty("IndustryPartKind", "cartridge_gauge_blank");
        tag.addProperty("IndustryDisplayName", "item.tacz.press_die_blank.cartridge_gauge");
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

    private static JsonObject caseBlankTag() {
        JsonObject tag = new JsonObject();
        tag.addProperty("IndustryPlatform", "ammunition");
        tag.addProperty("IndustryPartKind", "case_blank");
        tag.addProperty("IndustryDisplayName", "item.tacz.cartridge_case_blank");
        return tag;
    }

    private static JsonObject projectileBlankTag() {
        JsonObject tag = new JsonObject();
        tag.addProperty("IndustryPlatform", "ammunition");
        tag.addProperty("IndustryPartKind", "projectile_blank");
        tag.addProperty("IndustryDisplayName", "item.tacz.projectile_blank");
        return tag;
    }

    private static JsonObject gaugeTag(SurveyedCartridge cartridge) {
        JsonObject tag = surveyedIdentity(cartridge, "survey_cartridge_gauge", "item.tacz.press_die.survey_cartridge_gauge");
        tag.addProperty("DieTargetKind", "surveyed_cartridge");
        tag.addProperty("ProjectileType", PROJECTILE_TYPE);
        return tag;
    }

    private static JsonObject caseTag(SurveyedCartridge cartridge) {
        return surveyedIdentity(cartridge, "case", "item.tacz.cartridge_case.surveyed");
    }

    private static JsonObject spentCaseTag(SurveyedCartridge cartridge) {
        // Must exactly mirror CartridgeAssemblyDefinition#createSpentCase.
        // That server path intentionally writes the stable ammo/calibre fields,
        // not the authoring-only IndustrySurveyAmmoId provenance field.
        JsonObject tag = new JsonObject();
        tag.addProperty("IndustryPlatform", "ammunition");
        tag.addProperty("IndustryPartKind", "spent_case");
        tag.addProperty("IndustryDisplayName", "item.tacz.cartridge_case.spent_surveyed");
        tag.addProperty("CartridgeCaliber", cartridge.caliber());
        tag.addProperty("CartridgeAmmoId", cartridge.ammoId().toString());
        tag.addProperty("SpentCartridgeCase", true);
        return tag;
    }

    private static JsonObject projectileTag(SurveyedCartridge cartridge) {
        JsonObject tag = surveyedIdentity(cartridge, "projectile", "item.tacz.projectile_core.surveyed");
        tag.addProperty("ProjectileType", PROJECTILE_TYPE);
        return tag;
    }

    private static JsonObject surveyedIdentity(SurveyedCartridge cartridge, String kind, String displayName) {
        JsonObject tag = new JsonObject();
        tag.addProperty("IndustryPlatform", "ammunition");
        tag.addProperty("IndustryPartKind", kind);
        tag.addProperty("IndustryDisplayName", displayName);
        tag.addProperty("CartridgeCaliber", cartridge.caliber());
        tag.addProperty("CartridgeAmmoId", cartridge.ammoId().toString());
        tag.addProperty("IndustrySurveyAmmoId", cartridge.ammoId().toString());
        return tag;
    }

    private static Identifier gaugeId(SurveyedCartridge cartridge) {
        return id("gauge", cartridge);
    }

    private static Identifier caseId(SurveyedCartridge cartridge) {
        return id("case", cartridge);
    }

    private static Identifier projectileId(SurveyedCartridge cartridge) {
        return id("projectile", cartridge);
    }

    private static Identifier reconditionId(SurveyedCartridge cartridge) {
        return id("recondition", cartridge);
    }

    private static Identifier definitionId(SurveyedCartridge cartridge) {
        return id("definition", cartridge);
    }

    private static Identifier id(String operation, SurveyedCartridge cartridge) {
        return Identifier.fromNamespaceAndPath(GunMod.MOD_ID,
                "industry/survey/ammo_" + operation + "/" + cartridge.ammoId().getNamespace() + "/" + cartridge.ammoId().getPath());
    }

    private static JsonObject object(JsonObject parent, String key) {
        return parent.has(key) && parent.get(key).isJsonObject() ? parent.getAsJsonObject(key) : null;
    }

    private static String string(JsonObject object, String key) {
        return object.has(key) && object.get(key).isJsonPrimitive() ? object.get(key).getAsString() : "";
    }

    private static int positive(JsonObject object, String key, int fallback) {
        try {
            return object.has(key) ? Math.max(1, object.get(key).getAsInt()) : fallback;
        } catch (RuntimeException ignored) {
            return fallback;
        }
    }

    public record Result(Map<Identifier, JsonElement> recipes, Map<Identifier, JsonElement> definitions,
                         int replacedAmmoRecipes) {
    }

    private record SurveyedCartridge(Identifier sourceRecipeId, Identifier ammoId, String caliber, int batch,
                                     int propellant, JsonArray legacyMaterials) {
    }
}
