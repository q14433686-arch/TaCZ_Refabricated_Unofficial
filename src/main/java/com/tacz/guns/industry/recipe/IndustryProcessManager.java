package com.tacz.guns.industry.recipe;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.tacz.guns.GunMod;
import com.tacz.guns.industry.IndustryProfileManager;
import com.tacz.guns.resource.CommonAssetsManager;
import com.tacz.guns.resource.manager.CommonDataManager;
import com.tacz.guns.resource.network.DataType;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.profiling.ProfilerFiller;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Synchronises a viewer-friendly projection of TACZ-owned Create Fly recipes.
 *
 * <p>Create Fly's own 26.2 build excludes its REI source set, so REI otherwise
 * has no display graph for valid {@code create:*} recipes. This manager reads
 * the actual recipe JSON under {@code recipe/create/} (an explicit opt-in
 * directory for any gun-pack namespace), and sends the parsed process graph
 * over TACZ's existing common data channel.</p>
 */
public final class IndustryProcessManager extends CommonDataManager<IndustryProcessDefinition> {
    public IndustryProcessManager() {
        // Scan the stable top-level recipe directory, then filter ids beginning
        // with create/.  FileToIdConverter cannot safely scan recipe/create
        // directly because TACZ's legacy recipes -> recipe compatibility layer
        // may remap a root recipes/foo.json to recipe/foo.json, which does not
        // share the nested create/ prefix and crashes fileToId().
        super(DataType.INDUSTRY_PROCESS, IndustryProcessDefinition.class, CommonAssetsManager.GSON,
                "recipe", "IndustryProcessLoader");
    }

    @Override
    protected IndustryProcessDefinition parseJson(JsonElement element) {
        return IndustryProcessDefinition.fromCreateRecipe(element);
    }

    @Override
    protected void apply(Map<Identifier, JsonElement> objects, ResourceManager resourceManager, ProfilerFiller profiler) {
        if (!IndustryProfileManager.isCreateFlyProfileActive()) {
            super.apply(Map.<Identifier, JsonElement>of(), resourceManager, profiler);
            return;
        }
        Map<Identifier, JsonElement> ours = new LinkedHashMap<>();
        for (Map.Entry<Identifier, JsonElement> entry : objects.entrySet()) {
            if (!entry.getValue().isJsonObject()) {
                continue;
            }
            // recipe/create is an explicit opt-in directory. Any gun-pack
            // namespace may use it, allowing third-party industrial recipes to
            // appear in the TACZ REI bridge without Java registration.
            if (!entry.getKey().getPath().startsWith("create/")) {
                continue;
            }
            JsonObject object = entry.getValue().getAsJsonObject();
            JsonElement type = object.get("type");
            if (type != null && type.isJsonPrimitive() && type.getAsJsonPrimitive().isString()
                    && type.getAsString().startsWith("create:")
                    && IndustryProcessDefinition.fromCreateRecipe(entry.getValue()) != null) {
                ours.put(entry.getKey(), entry.getValue());
            }
        }
        reportAmbiguousCompactingRecipes(ours);
        super.apply(ours, resourceManager, profiler);
    }

    /**
     * Create Fly's Basin chooses by the number of distinct ingredient kinds,
     * not by the total count. Therefore both exact duplicate recipes and a
     * "larger bolt blank" recipe such as 3 steel + pig iron versus 2 steel +
     * pig iron can tie: the latter is a subset with the same two ingredient
     * kinds. In a tie, recipe iteration order decides which visible REI entry
     * is actually craftable.
     *
     * <p>Report these deterministic raw-data failures at reload time. Heat is
     * part of the comparison only as a minimum requirement: a NONE recipe
     * still matches a heated/superheated Basin. Full tag-set overlap analysis
     * cannot safely run during this reload phase because 26.2 applies tags
     * afterward; exact tag definitions and exact partial-NBT definitions are
     * nevertheless checked canonically here.</p>
     */
    private static void reportAmbiguousCompactingRecipes(Map<Identifier, JsonElement> recipes) {
        List<CompactingInputSignature> signatures = new ArrayList<>();
        for (Map.Entry<Identifier, JsonElement> entry : recipes.entrySet()) {
            JsonElement raw = entry.getValue();
            if (raw == null || !raw.isJsonObject()) {
                continue;
            }
            JsonObject recipe = raw.getAsJsonObject();
            if (!"create:compacting".equals(string(recipe, "type"))) {
                continue;
            }
            signatures.add(new CompactingInputSignature(
                    entry.getKey(),
                    canonicalMemberCounts(recipe.get("ingredients")),
                    canonicalMemberCounts(recipe.get("fluid_ingredients")),
                    heatRank(string(recipe, "heat_requirement"))
            ));
        }

        Set<String> reported = new HashSet<>();
        for (CompactingInputSignature target : signatures) {
            if (target.ingredientKindCount() > 9) {
                GunMod.LOGGER.error(
                        "Create compacting recipe {} has {} distinct semantic Basin ingredients; Create Fly accepts at most 9.",
                        target.id(), target.ingredientKindCount());
            }
            for (CompactingInputSignature candidate : signatures) {
                if (target == candidate || !candidate.canMatch(target)) {
                    continue;
                }
                String key = target.id() + " <- " + candidate.id();
                if (reported.add(key)) {
                    GunMod.LOGGER.error(
                            "Ambiguous Create compacting recipes: {} can tie with {} when {}'s minimal Basin inputs are present. "
                                    + "Create may choose only one; use a neutral stock followed by a physical Deployer calibration selector.",
                            target.id(), candidate.id(), target.id());
                }
            }
        }
    }

    /** Canonical, multiplicity-preserving input map after Create SizedIngredient coalescing. */
    private static Map<String, Integer> canonicalMemberCounts(JsonElement raw) {
        Map<String, Integer> members = new LinkedHashMap<>();
        if (raw != null && raw.isJsonArray()) {
            for (JsonElement element : raw.getAsJsonArray()) {
                members.merge(canonicalJson(element), 1, Integer::sum);
            }
        }
        return Map.copyOf(members);
    }

    private static int heatRank(String requirement) {
        return switch (requirement) {
            case "superheated" -> 2;
            case "heated" -> 1;
            default -> 0;
        };
    }

    private record CompactingInputSignature(Identifier id, Map<String, Integer> items,
                                            Map<String, Integer> fluids, int heat) {
        private int ingredientKindCount() {
            return items.size() + fluids.size();
        }

        /**
         * Whether this candidate can match while the target's exact minimal
         * inputs and minimum heat are in the Basin, with the same Create
         * priority. A lower-priority subset is safe; an equal-priority subset
         * is an order-dependent collision.
         */
        private boolean canMatch(CompactingInputSignature target) {
            return heat <= target.heat
                    && ingredientKindCount() == target.ingredientKindCount()
                    && IndustryProcessManager.countsFit(items, target.items)
                    && IndustryProcessManager.countsFit(fluids, target.fluids);
        }
    }

    private static boolean countsFit(Map<String, Integer> required, Map<String, Integer> available) {
        for (Map.Entry<String, Integer> entry : required.entrySet()) {
            if (available.getOrDefault(entry.getKey(), 0) < entry.getValue()) {
                return false;
            }
        }
        return true;
    }

    private static String canonicalJson(JsonElement element) {
        if (element == null || element.isJsonNull()) {
            return "null";
        }
        if (element.isJsonPrimitive()) {
            return element.toString();
        }
        if (element.isJsonArray()) {
            List<String> values = new ArrayList<>();
            for (JsonElement child : element.getAsJsonArray()) {
                values.add(canonicalJson(child));
            }
            return "[" + String.join(",", values) + "]";
        }
        List<String> keys = new ArrayList<>(element.getAsJsonObject().keySet());
        Collections.sort(keys);
        List<String> fields = new ArrayList<>();
        for (String key : keys) {
            fields.add(key + ":" + canonicalJson(element.getAsJsonObject().get(key)));
        }
        return "{" + String.join(",", fields) + "}";
    }

    private static String string(JsonObject object, String key) {
        return object.has(key) && object.get(key).isJsonPrimitive() && object.get(key).getAsJsonPrimitive().isString()
                ? object.get(key).getAsString() : "";
    }
}
