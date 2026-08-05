package com.tacz.guns.industry.recipe;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;

/**
 * Data declaration which replaces one legacy gun-table terminal recipe with a
 * real, single-workpiece Create sequenced-assembly process.
 *
 * <p>Files map directly to legacy gun-table recipe ids under
 * {@code data/<namespace>/industry/assembly/<gun-path>.json}. The declaration
 * deliberately names its actual {@code create:sequenced_assembly} recipe: the
 * table recipe is hidden only after that process is present and passes the
 * one-workpiece validation in {@link IndustrialRecipeTransformer}. This keeps
 * a malformed/forgotten data-pack process from making a gun unobtainable.</p>
 */
public final class IndustryAssemblyDefinition {
    private final String platform;
    private final String blueprintDisplayName;
    private final Identifier terminalProcess;
    private final List<Component> components;
    private final List<Material> materials;

    private IndustryAssemblyDefinition(String platform, String blueprintDisplayName, Identifier terminalProcess,
                                       List<Component> components, List<Material> materials) {
        this.platform = platform;
        this.blueprintDisplayName = blueprintDisplayName;
        this.terminalProcess = terminalProcess;
        this.components = List.copyOf(components);
        this.materials = List.copyOf(materials);
    }

    public String getPlatform() {
        return platform;
    }

    public String getBlueprintDisplayName() {
        return blueprintDisplayName;
    }

    /** Actual Create recipe id, e.g. {@code tacz:create/industry/assemble_ak47}. */
    public Identifier getTerminalProcess() {
        return terminalProcess;
    }

    public List<Component> getComponents() {
        return components;
    }

    public List<Material> getMaterials() {
        return materials;
    }

    public boolean isValid() {
        return !platform.isBlank() && !blueprintDisplayName.isBlank() && terminalProcess != null && !components.isEmpty();
    }

    public static IndustryAssemblyDefinition fromJson(JsonElement raw) {
        if (raw == null || !raw.isJsonObject()) {
            return null;
        }
        JsonObject object = raw.getAsJsonObject();
        String platform = string(object, "platform");
        String blueprint = string(object, "blueprint_display_name");
        Identifier terminalProcess = Identifier.tryParse(string(object, "terminal_process"));
        List<Component> components = new ArrayList<>();
        if (object.has("components") && object.get("components").isJsonArray()) {
            for (JsonElement entry : object.getAsJsonArray("components")) {
                if (!entry.isJsonObject()) continue;
                JsonObject component = entry.getAsJsonObject();
                String structural = string(component, "structural");
                String kind = string(component, "kind");
                String display = string(component, "display_name");
                if (!kind.isBlank() && !display.isBlank()) {
                    components.add(new Component(structural, kind, display));
                }
            }
        }
        List<Material> materials = new ArrayList<>();
        if (object.has("materials") && object.get("materials").isJsonArray()) {
            for (JsonElement entry : object.getAsJsonArray("materials")) {
                if (!entry.isJsonObject()) continue;
                JsonObject material = entry.getAsJsonObject();
                String item = string(material, "item");
                int count = integer(material, "count", 1);
                if (!item.isBlank()) {
                    materials.add(new Material(item, Math.max(1, count)));
                }
            }
        }
        IndustryAssemblyDefinition definition = new IndustryAssemblyDefinition(
                platform, blueprint, terminalProcess, components, materials
        );
        return definition.isValid() ? definition : null;
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

    /**
     * {@code structural} is the neutral blank class (receiver, bolt, barrel,
     * trigger or recoil); {@code kind} is the platform-specific finished part.
     * Older third-party declarations may omit structural and remain readable,
     * but cannot claim a particular blank during safe industrial salvage.
     */
    public record Component(String structural, String kind, String displayName) {
    }

    public record Material(String itemId, int count) {
    }
}
