package com.tacz.guns.industry.recipe;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Data declaration for one built-in gun's industrial terminal assembly.
 *
 * <p>Files map directly to gun ids under
 * {@code data/<namespace>/industry/assembly/<gun-path>.json}. They are read by
 * {@link com.tacz.guns.resource.manager.TableRecipeManager}; the resulting
 * rewritten table recipe is what gets synchronised to every client.</p>
 */
public final class IndustryAssemblyDefinition {
    private final String platform;
    private final String blueprintDisplayName;
    private final List<Component> components;
    private final List<Material> materials;

    private IndustryAssemblyDefinition(String platform, String blueprintDisplayName,
                                       List<Component> components, List<Material> materials) {
        this.platform = platform;
        this.blueprintDisplayName = blueprintDisplayName;
        this.components = List.copyOf(components);
        this.materials = List.copyOf(materials);
    }

    public String getPlatform() {
        return platform;
    }

    public String getBlueprintDisplayName() {
        return blueprintDisplayName;
    }

    public List<Component> getComponents() {
        return components;
    }

    public List<Material> getMaterials() {
        return materials;
    }

    public boolean isValid() {
        return !platform.isBlank() && !blueprintDisplayName.isBlank() && !components.isEmpty();
    }

    public static IndustryAssemblyDefinition fromJson(JsonElement raw) {
        if (raw == null || !raw.isJsonObject()) {
            return null;
        }
        JsonObject object = raw.getAsJsonObject();
        String platform = string(object, "platform");
        String blueprint = string(object, "blueprint_display_name");
        List<Component> components = new ArrayList<>();
        if (object.has("components") && object.get("components").isJsonArray()) {
            for (JsonElement entry : object.getAsJsonArray("components")) {
                if (!entry.isJsonObject()) continue;
                JsonObject component = entry.getAsJsonObject();
                String kind = string(component, "kind");
                String display = string(component, "display_name");
                if (!kind.isBlank() && !display.isBlank()) {
                    components.add(new Component(kind, display));
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
        IndustryAssemblyDefinition definition = new IndustryAssemblyDefinition(platform, blueprint, components, materials);
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

    public record Component(String kind, String displayName) {
    }

    public record Material(String itemId, int count) {
    }
}
