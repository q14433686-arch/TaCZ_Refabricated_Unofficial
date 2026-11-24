package com.tacz.guns.resource.convert;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

/** 26.1.2 still uses conditions/functions arrays and the condition/function discriminators.
 * Only walk schema positions: item components may themselves contain arbitrary JSON. */
final class LootSchema26 {
    private LootSchema26() {}

    static void table(JsonObject table) {
        container(table);
        if (table.has("pools")) for (var element : table.getAsJsonArray("pools")) {
            var pool = element.getAsJsonObject();
            uniform(pool, "rolls");
            uniform(pool, "bonus_rolls");
            container(pool);
            for (var entry : pool.getAsJsonArray("entries")) entry(entry.getAsJsonObject());
        }
    }

    private static void entry(JsonObject entry) {
        container(entry);
        if (entry.has("children")) for (var child : entry.getAsJsonArray("children")) entry(child.getAsJsonObject());
    }

    private static void container(JsonObject obj) {
        if (obj.has("conditions")) condition(obj.get("conditions"));
        if (obj.has("functions")) modifiers(obj.getAsJsonArray("functions"));
    }

    static JsonElement modifiers(JsonArray list) {
        for (var el : list) {
            var fn = el.getAsJsonObject();
            if (fn.has("conditions")) condition(fn.get("conditions"));
            switch (fn.get("function").getAsString()) {
                case "minecraft:set_count" -> uniform(fn, "count");
                case "minecraft:set_damage" -> uniform(fn, "damage");
                case "minecraft:enchant_with_levels" -> uniform(fn, "levels");
            }
        }
        return list;
    }

    private static void uniform(JsonObject obj, String field) {
        if (obj.has(field) && obj.get(field).isJsonObject()) {
            var range = obj.getAsJsonObject(field);
            if (!range.has("type") && range.has("min") && range.has("max")) range.addProperty("type", "minecraft:uniform");
        }
    }

    static JsonElement condition(JsonElement condition) {
        if (condition.isJsonArray()) {
            for (var term : condition.getAsJsonArray()) condition(term);
        } else {
            var obj = condition.getAsJsonObject();
            if ("minecraft:alternative".equals(obj.get("condition").getAsString())) obj.addProperty("condition", "minecraft:any_of");
            if (obj.has("term")) condition(obj.get("term"));
            if (obj.has("terms")) condition(obj.get("terms"));
        }
        return condition;
    }

    static void advancement(JsonObject advancement) {
        for (var value : advancement.getAsJsonObject("criteria").asMap().values()) {
            var criterion = value.getAsJsonObject();
            if (!criterion.has("conditions")) continue;
            var fields = criterion.getAsJsonObject("conditions");
            if (fields.has("player")) {
                if (!fields.get("player").isJsonArray()) throw new IllegalArgumentException("Legacy entity player predicate requires review");
                condition(fields.get("player"));
            }
            // recipe_unlocked still uses the singular recipe field on this Minecraft line.
        }
    }
}
