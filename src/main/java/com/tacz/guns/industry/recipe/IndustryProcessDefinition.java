package com.tacz.guns.industry.recipe;

import com.google.gson.JsonArray;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Parsed view of one TACZ-owned Create Fly recipe for the recipe-viewer bridge. */
public final class IndustryProcessDefinition {
    private final IndustryProcessMachine machine;
    private final List<IndustryStackDefinition> inputs;
    private final List<IndustryStackDefinition> outputs;
    private final int processingTime;
    private final boolean keepHeldItem;
    /** Identity keys of non-consumed Deployer-held tools/templates. */
    private final Set<String> reusableInputIdentityKeys;
    /** True when inputs are consumed serially by one flowing transitional workpiece. */
    private final boolean sequencedAssembly;

    public IndustryProcessDefinition(IndustryProcessMachine machine, List<IndustryStackDefinition> inputs,
                                     List<IndustryStackDefinition> outputs, int processingTime, boolean keepHeldItem) {
        this(machine, inputs, outputs, processingTime, keepHeldItem, Set.of(), false);
    }

    private IndustryProcessDefinition(IndustryProcessMachine machine, List<IndustryStackDefinition> inputs,
                                      List<IndustryStackDefinition> outputs, int processingTime, boolean keepHeldItem,
                                      Set<String> reusableInputIdentityKeys, boolean sequencedAssembly) {
        this.machine = machine;
        this.inputs = List.copyOf(inputs);
        this.outputs = List.copyOf(outputs);
        this.processingTime = Math.max(0, processingTime);
        this.keepHeldItem = keepHeldItem;
        this.reusableInputIdentityKeys = Set.copyOf(reusableInputIdentityKeys);
        this.sequencedAssembly = sequencedAssembly;
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

    /** Whether the input at this displayed index is a retained held tool/template. */
    public boolean isInputReusable(int index) {
        return index >= 0 && index < inputs.size()
                && reusableInputIdentityKeys.contains(inputs.get(index).identityKey());
    }

    /**
     * A sequenced assembly never asks for several stacks on one Depot. Its
     * displayed ingredients are supplied one station at a time to the same
     * moving transitional workpiece.
     */
    public boolean isSequencedAssembly() {
        return sequencedAssembly;
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
        if ("create:sequenced_assembly".equals(type)) {
            return fromSequencedAssembly(recipe);
        }
        if ("create:mechanical_crafting".equals(type)) {
            return fromMechanicalCrafting(recipe);
        }

        IndustryProcessMachine machine = IndustryProcessMachine.fromCreateRecipe(type, string(recipe, "heat_requirement"));
        if (machine == null) {
            return null;
        }

        List<IndustryStackDefinition> inputs = new ArrayList<>();
        Set<String> reusable = new LinkedHashSet<>();
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
                // In an ordinary deploying recipe this is specifically the
                // Deployer-held stack, not a second Depot stack.
                if (bool(recipe, "keep_held_item", false)) {
                    reusable.add(input.identityKey());
                }
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

        List<IndustryStackDefinition> outputs = parseOutputs(recipe.get("results"));
        if (inputs.isEmpty() || outputs.isEmpty()) {
            return null;
        }
        return new IndustryProcessDefinition(machine, compressInputs(inputs), outputs,
                integer(recipe, "processing_time", 0), bool(recipe, "keep_held_item", false), reusable, false);
    }

    /**
     * Mechanical crafters are a genuine multi-slot mechanism.  Parse their
     * key/pattern pair rather than flattening it into a fictitious Depot
     * recipe, so REI can show the calibrated-gauge routes used by default ammo
     * families that have no bundled firearm chamber reference.
     */
    private static IndustryProcessDefinition fromMechanicalCrafting(JsonObject recipe) {
        if (!recipe.has("key") || !recipe.get("key").isJsonObject()
                || !recipe.has("pattern") || !recipe.get("pattern").isJsonArray()) {
            return null;
        }
        JsonObject key = recipe.getAsJsonObject("key");
        List<IndustryStackDefinition> inputs = new ArrayList<>();
        for (JsonElement rawRow : recipe.getAsJsonArray("pattern")) {
            if (rawRow == null || !rawRow.isJsonPrimitive() || !rawRow.getAsJsonPrimitive().isString()) {
                return null;
            }
            String row = rawRow.getAsString();
            for (int index = 0; index < row.length(); index++) {
                char symbol = row.charAt(index);
                if (symbol == ' ') {
                    continue;
                }
                JsonElement rawIngredient = key.get(String.valueOf(symbol));
                IndustryStackDefinition ingredient = parseInput(rawIngredient);
                if (ingredient == null) {
                    return null;
                }
                inputs.add(ingredient);
            }
        }
        IndustryStackDefinition result = parseOutput(recipe.get("result"));
        if (inputs.isEmpty() || result == null) {
            return null;
        }
        return new IndustryProcessDefinition(
                IndustryProcessMachine.MECHANICAL_CRAFTING,
                compressInputs(inputs), List.of(result), integer(recipe, "processing_time", 0), false
        );
    }

    /**
     * Project one actual Create sequenced-assembly recipe into REI. The first
     * input is the sole workpiece placed on a Depot/belt; each following input
     * is read from one nested station's held ingredient. Repeated consumables
     * are compressed into a count, while a {@code keep_held_item} blueprint is
     * retained and marked with infinity by the category renderer.
     */
    private static IndustryProcessDefinition fromSequencedAssembly(JsonObject recipe) {
        IndustryStackDefinition workpiece = parseInput(recipe.get("ingredient"));
        if (workpiece == null || !recipe.has("sequence") || !recipe.get("sequence").isJsonArray()) {
            return null;
        }
        JsonArray sequence = recipe.getAsJsonArray("sequence");
        if (sequence.size() < 2) {
            return null;
        }

        List<IndustryStackDefinition> inputs = new ArrayList<>();
        inputs.add(workpiece);
        Set<String> reusable = new LinkedHashSet<>();
        for (JsonElement rawStep : sequence) {
            if (!rawStep.isJsonObject()) {
                return null;
            }
            JsonObject step = rawStep.getAsJsonObject();
            String type = string(step, "type");
            if ("create:deploying".equals(type)) {
                // A valid deployment has target="$ingredient" (the workpiece)
                // and one separate held ingredient. Do not display the
                // placeholder as though it were a real additional input.
                if (!isIngredientPlaceholder(step.get("target"))) {
                    return null;
                }
                IndustryStackDefinition held = parseInput(step.get("ingredient"));
                if (held == null) {
                    return null;
                }
                inputs.add(held);
                if (bool(step, "keep_held_item", false)) {
                    reusable.add(held.identityKey());
                }
            } else if ("create:pressing".equals(type) || "create:filling".equals(type)) {
                // These are still one-workpiece operations. They add no held
                // item to the ingredient list.
                if (!isIngredientPlaceholder(step.get("ingredient")) || step.has("target")) {
                    return null;
                }
            } else {
                // Do not show a partial/incorrect tree for sequence step types
                // the bridge cannot describe faithfully yet.
                return null;
            }
        }

        IndustryStackDefinition output = parseOutput(recipe.get("result"));
        if (output == null) {
            return null;
        }
        return new IndustryProcessDefinition(
                IndustryProcessMachine.SEQUENCED_ASSEMBLY,
                compressInputs(inputs), List.of(output), integer(recipe, "processing_time", 0),
                !reusable.isEmpty(), reusable, true
        );
    }

    private static List<IndustryStackDefinition> parseOutputs(JsonElement rawOutputs) {
        List<IndustryStackDefinition> outputs = new ArrayList<>();
        if (rawOutputs != null && rawOutputs.isJsonArray()) {
            for (JsonElement output : rawOutputs.getAsJsonArray()) {
                IndustryStackDefinition stack = parseOutput(output);
                if (stack != null) {
                    outputs.add(stack);
                }
            }
        }
        return outputs;
    }

    private static IndustryStackDefinition parseOutput(JsonElement output) {
        if (output != null && output.isJsonObject()) {
            return IndustryStackDefinition.fromOutput(output.getAsJsonObject());
        }
        if (output != null && output.isJsonPrimitive() && output.getAsJsonPrimitive().isString()) {
            return IndustryStackDefinition.fromInput(output.getAsString());
        }
        return null;
    }

    private static List<IndustryStackDefinition> compressInputs(List<IndustryStackDefinition> inputs) {
        LinkedHashMap<String, IndustryStackDefinition> compressed = new LinkedHashMap<>();
        for (IndustryStackDefinition input : inputs) {
            IndustryStackDefinition previous = compressed.get(input.identityKey());
            compressed.put(input.identityKey(), previous == null ? input : previous.withCount(previous.getCount() + input.getCount()));
        }
        return List.copyOf(compressed.values());
    }

    private static IndustryStackDefinition parseInput(JsonElement input) {
        if (input == null || isIngredientPlaceholder(input) || isResultPlaceholder(input)) {
            return null;
        }
        if (input.isJsonPrimitive() && input.getAsJsonPrimitive().isString()) {
            return IndustryStackDefinition.fromInput(input.getAsString());
        }
        if (input.isJsonObject()) {
            JsonObject object = input.getAsJsonObject();
            // Direct Fabric form of TACZ's registered forge:partial_nbt
            // ingredient. It is exactly what the calibre/type-specific ammo
            // loading recipes and the platform-specific assembly steps use.
            if ("forge:partial_nbt".equals(string(object, "fabric:type"))
                    && object.has("items") && object.get("items").isJsonArray()
                    && object.getAsJsonArray("items").size() > 0) {
                JsonElement first = object.getAsJsonArray("items").get(0);
                if (first.isJsonPrimitive() && first.getAsJsonPrimitive().isString()) {
                    JsonObject components = new JsonObject();
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

    private static boolean isIngredientPlaceholder(JsonElement element) {
        return isPlaceholder(element, "$ingredient");
    }

    private static boolean isResultPlaceholder(JsonElement element) {
        return isPlaceholder(element, "$result");
    }

    private static boolean isPlaceholder(JsonElement element, String value) {
        return element != null && element.isJsonPrimitive()
                && element.getAsJsonPrimitive().isString()
                && value.equals(element.getAsString());
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
