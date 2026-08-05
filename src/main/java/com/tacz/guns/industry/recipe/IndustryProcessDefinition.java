package com.tacz.guns.industry.recipe;

import com.google.gson.JsonArray;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

/** Parsed view of one TACZ-owned Create Fly recipe for the recipe-viewer bridge. */
public final class IndustryProcessDefinition {
    private final IndustryProcessMachine machine;
    private final List<IndustryStackDefinition> inputs;
    private final List<IndustryStackDefinition> outputs;
    private final int processingTime;
    private final boolean keepHeldItem;

    public IndustryProcessDefinition(IndustryProcessMachine machine, List<IndustryStackDefinition> inputs,
                                     List<IndustryStackDefinition> outputs, int processingTime, boolean keepHeldItem) {
        this.machine = machine;
        this.inputs = List.copyOf(inputs);
        this.outputs = List.copyOf(outputs);
        this.processingTime = Math.max(0, processingTime);
        this.keepHeldItem = keepHeldItem;
    }

    public IndustryProcessMachine getMachine() {
        return machine;
    }

    public List<IndustryStackDefinition> getInputs() {
        return inputs;
    }

    public List<IndustryStackDefinition> getOutputs() {
        return outputs;
    }

    public int getProcessingTime() {
        return processingTime;
    }

    public boolean keepsHeldItem() {
        return keepHeldItem;
    }

    /**
     * Reads the subset of Create recipe JSON used by TACZ's industrial recipes.
     * Unsupported recipe types return null instead of polluting the REI bridge.
     */
    public static IndustryProcessDefinition fromCreateRecipe(JsonElement raw) {
        if (raw == null || !raw.isJsonObject()) {
            return null;
        }
        JsonObject recipe = raw.getAsJsonObject();
        String type = string(recipe, "type");
        IndustryProcessMachine machine = IndustryProcessMachine.fromCreateRecipe(type, string(recipe, "heat_requirement"));
        if (machine == null) {
            return null;
        }

        List<IndustryStackDefinition> inputs = new ArrayList<>();
        if (recipe.has("target")) {
            IndustryStackDefinition input = parseInput(recipe.get("target"));
            if (input != null) {
                inputs.add(input);
            }
        }
        if (recipe.has("ingredient")) {
            IndustryStackDefinition input = parseInput(recipe.get("ingredient"));
            if (input != null) {
                inputs.add(input);
            }
        }
        if (recipe.has("ingredients") && recipe.get("ingredients").isJsonArray()) {
            for (JsonElement input : recipe.getAsJsonArray("ingredients")) {
                IndustryStackDefinition stack = parseInput(input);
                if (stack != null) {
                    inputs.add(stack);
                }
            }
        }

        List<IndustryStackDefinition> outputs = new ArrayList<>();
        if (recipe.has("results") && recipe.get("results").isJsonArray()) {
            for (JsonElement output : recipe.getAsJsonArray("results")) {
                if (output.isJsonObject()) {
                    IndustryStackDefinition stack = IndustryStackDefinition.fromOutput(output.getAsJsonObject());
                    if (stack != null) {
                        outputs.add(stack);
                    }
                }
            }
        }
        if (inputs.isEmpty() || outputs.isEmpty()) {
            return null;
        }
        return new IndustryProcessDefinition(machine, compressInputs(inputs), outputs,
                integer(recipe, "processing_time", 0), bool(recipe, "keep_held_item", false));
    }

    private static List<IndustryStackDefinition> compressInputs(List<IndustryStackDefinition> inputs) {
        java.util.LinkedHashMap<String, IndustryStackDefinition> compressed = new java.util.LinkedHashMap<>();
        for (IndustryStackDefinition input : inputs) {
            IndustryStackDefinition previous = compressed.get(input.identityKey());
            compressed.put(input.identityKey(), previous == null ? input : previous.withCount(previous.getCount() + input.getCount()));
        }
        return List.copyOf(compressed.values());
    }

    private static IndustryStackDefinition parseInput(JsonElement input) {
        if (input.isJsonPrimitive() && input.getAsJsonPrimitive().isString()) {
            return IndustryStackDefinition.fromInput(input.getAsString());
        }
        if (input.isJsonObject()) {
            JsonObject object = input.getAsJsonObject();
            // Direct Fabric form of TACZ's registered forge:partial_nbt
            // ingredient. It is exactly what the calibre/type-specific ammo
            // loading recipes use, and can be rendered faithfully in REI.
            if ("forge:partial_nbt".equals(string(object, "fabric:type"))
                    && object.has("items") && object.get("items").isJsonArray()
                    && object.getAsJsonArray("items").size() > 0) {
                JsonElement first = object.getAsJsonArray("items").get(0);
                if (first.isJsonPrimitive() && first.getAsJsonPrimitive().isString()) {
                    com.google.gson.JsonObject components = new com.google.gson.JsonObject();
                    if (object.has("nbt") && object.get("nbt").isJsonObject()) {
                        components.add("minecraft:custom_data", object.getAsJsonObject("nbt").deepCopy());
                    }
                    net.minecraft.resources.Identifier id = net.minecraft.resources.Identifier.tryParse(first.getAsString());
                    return id == null ? null : new IndustryStackDefinition(id, false, 1, components);
                }
            }
        }
        // Object/custom ingredients not explicitly understood here are left to
        // the native Create viewer; showing a wrong representative is worse
        // than omitting an unsupported bridge entry.
        return null;
    }

    private static String string(JsonObject object, String key) {
        return object.has(key) && object.get(key).isJsonPrimitive() ? object.get(key).getAsString() : "";
    }

    private static int integer(JsonObject object, String key, int fallback) {
        try {
            return object.has(key) && object.get(key).isJsonPrimitive() ? object.get(key).getAsInt() : fallback;
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static boolean bool(JsonObject object, String key, boolean fallback) {
        try {
            return object.has(key) && object.get(key).isJsonPrimitive() ? object.get(key).getAsBoolean() : fallback;
        } catch (RuntimeException ignored) {
            return fallback;
        }
    }

    /** Allows the same raw Create JSON to be sent through CommonNetworkCache. */
    public static final class Deserializer implements JsonDeserializer<IndustryProcessDefinition> {
        @Override
        public IndustryProcessDefinition deserialize(JsonElement json, Type typeOfT,
                                                     JsonDeserializationContext context) throws JsonParseException {
            IndustryProcessDefinition definition = fromCreateRecipe(json);
            if (definition == null) {
                throw new JsonParseException("Unsupported TACZ industry Create recipe");
            }
            return definition;
        }
    }
}
