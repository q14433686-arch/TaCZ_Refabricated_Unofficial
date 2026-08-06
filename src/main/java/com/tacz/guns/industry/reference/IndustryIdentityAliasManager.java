package com.tacz.guns.industry.reference;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.tacz.guns.GunMod;
import com.tacz.guns.resource.CommonAssetsManager;
import com.tacz.guns.resource.manager.CommonDataManager;
import com.tacz.guns.resource.network.DataType;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.profiling.ProfilerFiller;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Loads explicit recipe-result alias repairs before table recipes are parsed.
 *
 * <p>Files live under {@code data/<namespace>/industry/id_aliases/*.json}.
 * The file id is only provenance; its {@code recipe} field is the actual
 * table-recipe id being repaired. Invalid or conflicting aliases fail closed.
 * They never turn a filename resemblance into an automatic identity rewrite.</p>
 */
public final class IndustryIdentityAliasManager extends CommonDataManager<IndustryIdentityAlias> {
    private Map<Identifier, IndustryIdentityAlias> aliasesByRecipe = Map.of();
    private Map<Identifier, Identifier> sourceByRecipe = Map.of();

    public IndustryIdentityAliasManager() {
        super(DataType.INDUSTRY_ID_ALIAS, IndustryIdentityAlias.class, CommonAssetsManager.GSON,
                "industry/id_aliases", "IndustryIdentityAliasLoader");
    }

    @Override
    protected void apply(Map<Identifier, JsonElement> objects, ResourceManager resourceManager, ProfilerFiller profiler) {
        super.apply(objects, resourceManager, profiler);
        Map<Identifier, IndustryIdentityAlias> resolved = new LinkedHashMap<>();
        Map<Identifier, Identifier> sources = new LinkedHashMap<>();
        for (Map.Entry<Identifier, IndustryIdentityAlias> entry : getAllData().entrySet()) {
            IndustryIdentityAlias alias = entry.getValue();
            Identifier recipe = alias == null ? null : alias.getRecipe();
            if (recipe == null) {
                GunMod.LOGGER.error("Ignoring industry identity alias {}: recipe id is absent", entry.getKey());
                continue;
            }
            IndustryIdentityAlias.Validation declaration = alias.validateDeclaration();
            if (!declaration.valid()) {
                GunMod.LOGGER.error("Ignoring malformed industry identity alias {} for {}: {}", entry.getKey(), recipe,
                        declaration.reason());
                continue;
            }
            if (!alias.targetIsLoaded(CommonAssetsManager.get())) {
                // Built-in compatibility data may cover an optional pack. Do
                // not turn an absent optional dependency into an error; the
                // alias activates automatically once its target index loads.
                GunMod.LOGGER.debug("Leaving optional industry identity alias {} for {} dormant: target {} is absent.",
                        entry.getKey(), recipe, alias.getTarget());
                continue;
            }
            IndustryIdentityAlias.Validation validation = alias.validateAgainst(CommonAssetsManager.get());
            if (!validation.valid()) {
                GunMod.LOGGER.error("Ignoring industry identity alias {} for {}: {}", entry.getKey(), recipe,
                        validation.reason());
                continue;
            }
            Identifier oldSource = sources.putIfAbsent(recipe, entry.getKey());
            if (oldSource != null) {
                GunMod.LOGGER.error("Ignoring conflicting industry identity alias {} for {}; already supplied by {}",
                        entry.getKey(), recipe, oldSource);
                continue;
            }
            resolved.put(recipe, alias);
        }
        aliasesByRecipe = Map.copyOf(resolved);
        sourceByRecipe = Map.copyOf(sources);
        if (!aliasesByRecipe.isEmpty()) {
            GunMod.LOGGER.info("Loaded {} validated TACZ industry identity alias(es).", aliasesByRecipe.size());
        }
    }

    @Nullable
    public IndustryIdentityAlias getAlias(Identifier recipeId) {
        return recipeId == null ? null : aliasesByRecipe.get(recipeId);
    }

    public Map<Identifier, IndustryIdentityAlias> getAliasesByRecipe() {
        return aliasesByRecipe;
    }

    @Nullable
    public Identifier getSource(Identifier recipeId) {
        return recipeId == null ? null : sourceByRecipe.get(recipeId);
    }

    /**
     * Return a copy with an explicitly validated result id repaired. The input
     * map remains unmodified so the runtime audit can still report the upstream
     * id and distinguish direct resolution from a curated alias repair.
     */
    public JsonElement applyAlias(Identifier recipeId, JsonElement source) {
        IndustryIdentityAlias alias = getAlias(recipeId);
        if (alias == null || source == null || !source.isJsonObject()) {
            return source;
        }
        JsonObject raw = source.getAsJsonObject();
        if (!raw.has("result") || !raw.get("result").isJsonObject()) {
            return source;
        }
        JsonObject result = raw.getAsJsonObject("result");
        if (!alias.getKind().equals(string(result, "type"))) {
            GunMod.LOGGER.error("Ignoring industry alias {} from {}: target recipe result type is '{}' rather than '{}'.",
                    recipeId, getSource(recipeId), string(result, "type"), alias.getKind());
            return source;
        }
        Identifier target = alias.getTarget();
        if (target == null || target.toString().equals(string(result, "id"))) {
            return source;
        }
        JsonObject copy = raw.deepCopy();
        copy.getAsJsonObject("result").addProperty("id", target.toString());
        return copy;
    }

    public Map<Identifier, JsonElement> applyAliases(Map<Identifier, JsonElement> recipes) {
        Map<Identifier, JsonElement> output = new LinkedHashMap<>();
        for (Map.Entry<Identifier, JsonElement> entry : recipes.entrySet()) {
            output.put(entry.getKey(), applyAlias(entry.getKey(), entry.getValue()));
        }
        return output;
    }

    private static String string(JsonObject object, String key) {
        return object.has(key) && object.get(key).isJsonPrimitive() ? object.get(key).getAsString() : "";
    }
}
