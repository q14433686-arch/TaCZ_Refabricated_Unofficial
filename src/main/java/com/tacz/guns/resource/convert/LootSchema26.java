package com.tacz.guns.resource.convert;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

/** Convert only loot-schema positions. In particular never interpret keys inside custom item data as loot fields. */
final class LootSchema26 {
    private LootSchema26() {}

    static void table(JsonObject table) {
        container(table);
        if (table.has("pools")) for (var pool : table.getAsJsonArray("pools")) {
            var json = pool.getAsJsonObject();
            uniform(json, "rolls");
            uniform(json, "bonus_rolls");
            container(json);
            for (var entry : json.getAsJsonArray("entries")) entry(entry.getAsJsonObject());
        }
    }

    private static void entry(JsonObject entry) {
        container(entry);
        if (entry.has("children")) for (var child : entry.getAsJsonArray("children")) entry(child.getAsJsonObject());
    }

    private static void container(JsonObject obj) {
        conditions(obj);
        if (obj.has("functions")) {
            if (obj.has("modifier")) throw new IllegalArgumentException("Both functions and modifier are present");
            obj.add("modifier", modifiers(obj.remove("functions").getAsJsonArray()));
        }
    }

    static JsonElement modifiers(JsonArray old) {
        var out = new JsonArray();
        for (var el : old) {
            var fn = el.getAsJsonObject();
            rename(fn, "function", "type");
            conditions(fn);
            switch (fn.get("type").getAsString()) {
                case "minecraft:set_count" -> uniform(fn, "count");
                case "minecraft:set_damage" -> uniform(fn, "damage");
                case "minecraft:enchant_with_levels" -> uniform(fn, "levels");
            }
            if (fn.get("type").getAsString().equals("minecraft:reference")) {
                if (fn.size() != 2) throw new IllegalArgumentException("Conditional modifier reference requires review");
                out.add(fn.get("name").getAsString());
            }
            else out.add(fn);
        }
        return out.size() == 1 ? out.get(0) : out;
    }

    private static void uniform(JsonObject obj, String field) {
        if (obj.has(field) && obj.get(field).isJsonObject()) {
            var range = obj.getAsJsonObject(field);
            if (!range.has("type") && range.has("min") && range.has("max")) range.addProperty("type", "minecraft:uniform");
        }
    }

    private static void conditions(JsonObject obj) {
        if (!obj.has("conditions")) return;
        if (obj.has("condition")) throw new IllegalArgumentException("Both conditions and condition are present");
        var terms = obj.remove("conditions").getAsJsonArray();
        if (!terms.isEmpty()) obj.add("condition", condition(terms));
    }

    static JsonElement condition(JsonElement old) {
        if (old.isJsonArray()) {
            var terms = new JsonArray();
            for (var el : old.getAsJsonArray()) terms.add(condition(el));
            if (terms.size() == 1) return terms.get(0);
            var all = new JsonObject();
            all.addProperty("type", "minecraft:all_of");
            all.add("terms", terms);
            return all;
        }
        var obj = old.getAsJsonObject();
        rename(obj, "condition", "type");
        switch (obj.get("type").getAsString()) {
            case "minecraft:reference" -> { return new JsonPrimitive(obj.get("name").getAsString()); }
            case "minecraft:block_state_property" -> {
                obj.addProperty("type", "minecraft:match_block");
                rename(obj, "block", "blocks");
                if (obj.has("properties")) rename(obj, "properties", "state");
            }
            case "minecraft:alternative" -> obj.addProperty("type", "minecraft:any_of");
        }
        if (obj.has("term")) obj.add("term", condition(obj.get("term")));
        if (obj.has("terms")) {
            var terms = obj.getAsJsonArray("terms");
            for (int i = 0; i < terms.size(); i++) terms.set(i, condition(terms.get(i)));
        }
        return obj;
    }

    static void advancement(JsonObject advancement) {
        for (var value : advancement.getAsJsonObject("criteria").asMap().values()) {
            var criterion = value.getAsJsonObject();
            if (!criterion.has("conditions")) continue;
            var fields = criterion.getAsJsonObject("conditions");
            if ("minecraft:recipe_unlocked".equals(criterion.get("trigger").getAsString()) && fields.has("recipe")) rename(fields, "recipe", "recipes");
            // 1.20.1 accepts a list of loot conditions as a context-aware player predicate.
            if (fields.has("player")) {
                if (!fields.get("player").isJsonArray()) throw new IllegalArgumentException("Legacy entity player predicate requires review");
                fields.add("player", condition(fields.get("player")));
            }
        }
    }

    private static void rename(JsonObject obj, String old, String replacement) {
        if (obj.has(replacement) || !obj.has(old)) throw new IllegalArgumentException("Missing " + old + " or conflicting " + replacement);
        obj.add(replacement, obj.remove(old));
    }
}
