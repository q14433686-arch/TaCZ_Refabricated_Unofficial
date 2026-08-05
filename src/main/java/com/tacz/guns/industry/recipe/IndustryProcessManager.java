package com.tacz.guns.industry.recipe;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.tacz.guns.industry.IndustryProfileManager;
import com.tacz.guns.resource.CommonAssetsManager;
import com.tacz.guns.resource.manager.CommonDataManager;
import com.tacz.guns.resource.network.DataType;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.profiling.ProfilerFiller;

import java.util.LinkedHashMap;
import java.util.Map;

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
        super.apply(ours, resourceManager, profiler);
    }
}
