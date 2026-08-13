package cn.sh1rocu.tacz.util.forge;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.Strictness;
import com.google.gson.stream.JsonReader;
import com.mojang.serialization.JsonOps;
import com.tacz.guns.GunMod;
import com.tacz.guns.crafting.GunSmithTableIngredient;
import net.minecraft.nbt.NbtOps;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.IoSupplier;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Set;

/**
 * Converts the result form used by pre-data-component gun packs while their recipe JSON is being
 * read from the aggregate TACZ gun-pack resource pack.
 *
 * <p>Old packs commonly contain a normal vanilla shaped recipe such as:
 * <pre>
 * "result": {
 *   "item": "tacz:workbench_b",
 *   "nbt": { "BlockId": "example:smith_table" }
 * }
 * </pre>
 * In 1.21.11, vanilla recipe codecs use {@code id} and the
 * {@code minecraft:custom_data} component instead. If the old result is handed to the codec
 * unchanged, its table item either fails to load or loses {@code BlockId}. A generic physical
 * {@code tacz:workbench_*} block with no custom identity cannot select the pack's recipe filter,
 * so it appears to have been replaced by another table using the same physical shape.
 *
 * <p>This adapter is deliberately applied only to standard vanilla recipe types inside gun packs.
 * TACZ's {@code tacz:gun_smith_table_crafting} data has its own serializer and must retain its
 * original result schema.
 */
public final class LegacyGunPackRecipeMigrator {
    private static final Gson GSON = new GsonBuilder()
            .setStrictness(Strictness.LENIENT)
            .create();

    private static final Set<String> VANILLA_RESULT_RECIPE_TYPES = Set.of(
            "minecraft:crafting_shaped",
            "minecraft:crafting_shapeless",
            "minecraft:smelting",
            "minecraft:blasting",
            "minecraft:smoking",
            "minecraft:campfire_cooking",
            "minecraft:stonecutting",
            "minecraft:smithing_transform"
    );

    private LegacyGunPackRecipeMigrator() {
    }

    /**
     * Wraps a pack resource without opening it until Minecraft asks for the stream.
     *
     * <p>The returned stream is always independent of the source stream. This matters because a
     * resource can be opened more than once during a reload (for example by the recipe manager and
     * a diagnostics path).
     */
    public static IoSupplier<InputStream> migrate(Identifier recipeLocation, IoSupplier<InputStream> source) {
        return () -> {
            try (InputStream input = source.get()) {
                return new ByteArrayInputStream(migrate(recipeLocation, input.readAllBytes()));
            }
        };
    }

    /**
     * Eagerly adapts an old plural-directory entry only when it is a standard vanilla recipe.
     *
     * <p>{@code data/<namespace>/recipes/} also contains TACZ's own
     * {@code tacz:gun_smith_table_crafting} files. Those are loaded by {@code TableRecipeManager}'s
     * dedicated legacy scan and must <em>not</em> be exposed to vanilla's {@code recipe/} loader:
     * their legacy counted-material ingredient schema is intentionally parsed by TACZ Gson code,
     * not vanilla's modern {@code Ingredient.CODEC}. Returning {@code null} keeps such entries on
     * that dedicated path while allowing old vanilla workbench recipes through.
     */
    public static IoSupplier<InputStream> migrateLegacyVanillaRecipe(Identifier recipeLocation,
                                                                       IoSupplier<InputStream> source) {
        try (InputStream input = source.get()) {
            byte[] originalBytes = input.readAllBytes();
            if (!isVanillaResultRecipe(originalBytes)) {
                return null;
            }
            byte[] migratedBytes = migrate(recipeLocation, originalBytes);
            return () -> new ByteArrayInputStream(migratedBytes);
        } catch (IOException | RuntimeException exception) {
            GunMod.LOGGER.warn("Could not inspect legacy gun-pack recipe {}; it will stay on TACZ's legacy path.",
                    recipeLocation, exception);
            return null;
        }
    }

    /**
     * Performs a best-effort migration and returns the original bytes on malformed or unsupported
     * data. A bad third-party recipe must still be reported by vanilla's normal recipe error path;
     * this compatibility layer must not turn it into an unrelated load failure.
     */
    public static byte[] migrate(Identifier recipeLocation, byte[] originalBytes) {
        try {
            JsonElement root = parse(originalBytes);
            if (root == null || !root.isJsonObject()) {
                return originalBytes;
            }

            JsonObject recipe = root.getAsJsonObject();
            if (!isVanillaResultRecipe(recipe)) {
                return originalBytes;
            }

            boolean changed = migrateIngredients(recipe);
            JsonElement resultElement = recipe.get("result");
            if (resultElement != null && resultElement.isJsonObject()) {
                changed |= migrateResult(resultElement.getAsJsonObject());
            }

            if (!changed) {
                return originalBytes;
            }
            GunMod.LOGGER.debug("Migrated legacy gun-pack recipe {} to 1.21.11 data components/ingredients", recipeLocation);
            return GSON.toJson(root).getBytes(StandardCharsets.UTF_8);
        } catch (IOException | RuntimeException exception) {
            GunMod.LOGGER.warn("Could not migrate legacy gun-pack recipe {}; leaving its JSON unchanged: {}",
                    recipeLocation, exception.getMessage());
            return originalBytes;
        }
    }

    private static JsonElement parse(byte[] bytes) throws IOException {
        try (JsonReader reader = GSON.newJsonReader(
                new InputStreamReader(new ByteArrayInputStream(bytes), StandardCharsets.UTF_8))) {
            reader.setStrictness(Strictness.LENIENT);
            return GSON.fromJson(reader, JsonElement.class);
        }
    }

    private static boolean isVanillaResultRecipe(byte[] bytes) {
        try {
            JsonElement root = parse(bytes);
            return root != null && root.isJsonObject() && isVanillaResultRecipe(root.getAsJsonObject());
        } catch (IOException | RuntimeException ignored) {
            return false;
        }
    }

    private static boolean isVanillaResultRecipe(JsonObject recipe) {
        JsonElement type = recipe.get("type");
        return type != null
                && type.isJsonPrimitive()
                && type.getAsJsonPrimitive().isString()
                && VANILLA_RESULT_RECIPE_TYPES.contains(type.getAsString());
    }

    /**
     * Converts the pre-1.21 ingredient object form used by old workbench recipes.
     *
     * <p>For example, the supplied GunpowderRevolution table recipe uses
     * {@code {"tag":"forge:ingots/iron"}} in a shaped key and Apocalypse uses
     * {@code {"type":"forge:nbt", ...}} to transform one custom table into another.
     * 1.21.11 accepts {@code "#forge:ingots/iron"} and Fabric's registered custom-ingredient
     * shape instead. Reuse {@link GunSmithTableIngredient#normalizeLegacyIngredientJson(JsonElement)}
     * so normal table construction and TACZ's own counted-material recipes keep exactly the same
     * legacy conversion contract.
     */
    private static boolean migrateIngredients(JsonObject recipe) {
        String type = recipe.get("type").getAsString();
        return switch (type) {
            case "minecraft:crafting_shaped" -> migrateShapedKey(recipe);
            case "minecraft:crafting_shapeless" -> migrateIngredientArray(recipe, "ingredients");
            case "minecraft:smelting", "minecraft:blasting", "minecraft:smoking",
                    "minecraft:campfire_cooking", "minecraft:stonecutting" ->
                    migrateIngredient(recipe, "ingredient");
            case "minecraft:smithing_transform" -> {
                boolean changed = migrateIngredient(recipe, "template");
                changed |= migrateIngredient(recipe, "base");
                changed |= migrateIngredient(recipe, "addition");
                yield changed;
            }
            default -> false;
        };
    }

    private static boolean migrateShapedKey(JsonObject recipe) {
        JsonElement keyElement = recipe.get("key");
        if (keyElement == null || !keyElement.isJsonObject()) {
            return false;
        }
        JsonObject key = keyElement.getAsJsonObject();
        JsonObject migrated = new JsonObject();
        boolean changed = false;
        for (Map.Entry<String, JsonElement> entry : key.entrySet()) {
            JsonElement normalized = GunSmithTableIngredient.normalizeLegacyIngredientJson(entry.getValue());
            changed |= normalized != entry.getValue();
            migrated.add(entry.getKey(), normalized);
        }
        if (changed) {
            recipe.add("key", migrated);
        }
        return changed;
    }

    private static boolean migrateIngredientArray(JsonObject recipe, String field) {
        JsonElement ingredientsElement = recipe.get(field);
        if (ingredientsElement == null || !ingredientsElement.isJsonArray()) {
            return false;
        }
        JsonArray ingredients = ingredientsElement.getAsJsonArray();
        JsonArray migrated = new JsonArray(ingredients.size());
        boolean changed = false;
        for (JsonElement ingredient : ingredients) {
            JsonElement normalized = GunSmithTableIngredient.normalizeLegacyIngredientJson(ingredient);
            changed |= normalized != ingredient;
            migrated.add(normalized);
        }
        if (changed) {
            recipe.add(field, migrated);
        }
        return changed;
    }

    private static boolean migrateIngredient(JsonObject recipe, String field) {
        JsonElement ingredient = recipe.get(field);
        if (ingredient == null) {
            return false;
        }
        JsonElement normalized = GunSmithTableIngredient.normalizeLegacyIngredientJson(ingredient);
        if (normalized == ingredient) {
            return false;
        }
        recipe.add(field, normalized);
        return true;
    }

    /**
     * Converts {@code item -> id} and {@code nbt -> components.minecraft:custom_data}.
     *
     * @return whether the JSON object changed
     */
    private static boolean migrateResult(JsonObject result) {
        boolean changed = false;
        JsonElement legacyNbt = result.get("nbt");
        if (legacyNbt != null && !legacyNbt.isJsonNull()) {
            JsonObject legacyCustomData = decodeLegacyNbt(legacyNbt);
            JsonObject components = getOrCreateComponents(result);
            JsonElement existingCustomData = components.get("minecraft:custom_data");
            if (existingCustomData == null || existingCustomData.isJsonNull()) {
                components.add("minecraft:custom_data", legacyCustomData);
            } else if (existingCustomData.isJsonObject()) {
                // An explicitly authored modern component wins field-by-field. This allows a pack
                // transition period where both forms are present without discarding new data.
                components.add("minecraft:custom_data",
                        mergeObjects(legacyCustomData, existingCustomData.getAsJsonObject()));
            } else {
                throw new IllegalArgumentException("result.components.minecraft:custom_data is not an object");
            }
            result.remove("nbt");
            changed = true;
        }

        JsonElement legacyItem = result.get("item");
        if (legacyItem != null && !legacyItem.isJsonNull()) {
            if (!result.has("id")) {
                result.add("id", legacyItem.deepCopy());
            }
            // Modern result codecs only need id. Removing the old field also avoids relying on a
            // codec's unknown-field policy.
            result.remove("item");
            changed = true;
        }

        // A result with only a modern id/components was untouched. This intentionally avoids
        // rewriting ordinary current-format gun packs.
        return changed;
    }

    private static JsonObject getOrCreateComponents(JsonObject result) {
        JsonElement componentsElement = result.get("components");
        if (componentsElement == null || componentsElement.isJsonNull()) {
            JsonObject components = new JsonObject();
            result.add("components", components);
            return components;
        }
        if (!componentsElement.isJsonObject()) {
            throw new IllegalArgumentException("result.components is not an object");
        }
        return componentsElement.getAsJsonObject();
    }

    /** Converts either legacy SNBT text or a legacy JSON object into component JSON. */
    private static JsonObject decodeLegacyNbt(JsonElement legacyNbt) {
        JsonElement converted = NbtOps.INSTANCE.convertTo(JsonOps.INSTANCE, CraftingHelper.getNBT(legacyNbt));
        if (!converted.isJsonObject()) {
            throw new IllegalArgumentException("legacy result.nbt is not a compound tag");
        }
        return converted.getAsJsonObject();
    }

    /** Deep-merges two NBT-like JSON objects, with {@code modern} taking precedence. */
    private static JsonObject mergeObjects(JsonObject legacy, JsonObject modern) {
        JsonObject merged = legacy.deepCopy();
        for (Map.Entry<String, JsonElement> entry : modern.entrySet()) {
            JsonElement old = merged.get(entry.getKey());
            JsonElement replacement = entry.getValue();
            if (old != null && old.isJsonObject() && replacement.isJsonObject()) {
                merged.add(entry.getKey(), mergeObjects(old.getAsJsonObject(), replacement.getAsJsonObject()));
            } else {
                merged.add(entry.getKey(), replacement.deepCopy());
            }
        }
        return merged;
    }
}
