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

    public IndustryProcessDefinition(IndustryProcessMachine machine, List<IndustryStackDefinition> inputs,
                                     List<IndustryStackDefinition> outputs, int processingTime) {
        this.machine = machine;
        this.inputs = List.copyOf(inputs);
        this.outputs = List.copyOf(outputs);
        this.processingTime = Math.max(0, processingTime);
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
        return new IndustryProcessDefinition(machine, inputs, outputs, integer(recipe, "processing_time", 0));
    }

    private static IndustryStackDefinition parseInput(JsonElement input) {
        if (input.isJsonPrimitive() && input.getAsJsonPrimitive().isString()) {
            return IndustryStackDefinition.fromInput(input.getAsString());
        }
        // Object/custom ingredients require a semantic adapter. They are left
        // to the native Create viewer, while direct ids and #item_tag strings
        // are represented losslessly by the TACZ REI bridge.
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
