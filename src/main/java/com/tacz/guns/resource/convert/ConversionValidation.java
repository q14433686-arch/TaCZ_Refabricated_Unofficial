package com.tacz.guns.resource.convert;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DynamicOps;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;

import java.util.Set;

/** Checks target schemas without requiring optional mods to be installed during conversion. */
public final class ConversionValidation {
    private ConversionValidation() { }

    public static <T> void validate(Codec<T> codec, DynamicOps<JsonElement> ops, JsonElement json,
                                   PackConversionPlan report, String path) {
        codec.parse(ops, prepare(json, report, path)).getOrThrow(IllegalArgumentException::new);
    }

    /** The copy is used only by codecs; original identifiers always remain in the output. */
    public static JsonElement prepare(JsonElement json, PackConversionPlan report, String path) {
        if (json.isJsonArray()) {
            JsonArray copy = new JsonArray();
            for (int i = 0; i < json.getAsJsonArray().size(); i++) copy.add(prepare(json.getAsJsonArray().get(i), report, path + "[" + i + "]"));
            return copy;
        }
        if (!json.isJsonObject()) return json.deepCopy();
        JsonObject input = json.getAsJsonObject();
        JsonObject copy = new JsonObject();
        boolean stack = input.has("id") && !input.has("type")
            && (input.has("count") || input.has("components") || Set.of("id", "count", "components").containsAll(input.keySet()));
        boolean lootItem = input.has("type") && input.get("type").isJsonPrimitive() && input.get("type").getAsString().equals("minecraft:item");
        for (var entry : input.entrySet()) {
            String key = entry.getKey();
            JsonElement value = entry.getValue();
            String childPath = path + "." + key;
            if (value.isJsonPrimitive() && value.getAsJsonPrimitive().isString()
                && (key.equals("item") || key.equals("items") || (stack && key.equals("id")) || (lootItem && key.equals("name")))) {
                copy.addProperty(key, validationItem(value.getAsString(), report, childPath));
            } else if (key.equals("items") && value.isJsonArray()) {
                JsonArray items = new JsonArray();
                for (int i = 0; i < value.getAsJsonArray().size(); i++) {
                    JsonElement item = value.getAsJsonArray().get(i);
                    items.add(item.isJsonPrimitive() && item.getAsJsonPrimitive().isString()
                        ? new JsonPrimitive(validationItem(item.getAsString(), report, childPath + "[" + i + "]"))
                        : prepare(item, report, childPath + "[" + i + "]"));
                }
                copy.add(key, items);
            } else if (key.equals("minecraft:custom_data") || key.equals("nbt")) {
                copy.add(key, value.deepCopy());
            } else {
                copy.add(key, prepare(value, report, childPath));
            }
        }
        return copy;
    }

    /** Vanilla recipes embed native ingredients as bare strings/tags or Fabric compound ingredients.
     * Substitute missing items only in the temporary codec input, not in the exported overlay. */
    static JsonObject recipeCopy(JsonObject recipe, PackConversionPlan report) {
        var copy = recipe.deepCopy();
        if (copy.has("key")) copy.getAsJsonObject("key").asMap().replaceAll((k, v) -> ingredientCopy(v, report, "$.key." + k));
        if (copy.has("ingredients")) {
            var args = copy.getAsJsonArray("ingredients");
            for (int i = 0; i < args.size(); ++i) args.set(i, ingredientCopy(args.get(i), report, "$.ingredients[" + i + "]"));
        }
        for (String key : java.util.List.of("ingredient", "base", "addition", "template"))
            if (copy.has(key)) copy.add(key, ingredientCopy(copy.get(key), report, "$." + key));
        return copy;
    }

    static void validateIngredient(DynamicOps<JsonElement> ops, JsonElement json, PackConversionPlan report, String path) {
        net.minecraft.world.item.crafting.Ingredient.CODEC.parse(ops, ingredientCopy(json, report, path)).getOrThrow(IllegalArgumentException::new);
    }

    private static JsonElement ingredientCopy(JsonElement json, PackConversionPlan report, String path) {
        if (json.isJsonPrimitive()) return new JsonPrimitive(validationItem(json.getAsString(), report, path));
        if (json.isJsonArray()) {
            var array = new JsonArray();
            for (var child : json.getAsJsonArray()) array.add(ingredientCopy(child, report, path + "[" + array.size() + "]"));
            return array;
        }
        var copy = json.getAsJsonObject().deepCopy();
        for (String field : java.util.List.of("ingredients", "base", "subtracted"))
            if (copy.has(field)) copy.add(field, ingredientCopy(copy.get(field), report, path + "." + field));
        return copy;
    }

    private static String validationItem(String value, PackConversionPlan report, String path) {
        if (value.startsWith("#")) {
            Identifier.parse(value.substring(1));
            return value;
        }
        Identifier id = Identifier.parse(value);
        if (BuiltInRegistries.ITEM.containsKey(id)) return value;
        if (report != null) report.deferItem(value, path);
        return "minecraft:stone";
    }
}
