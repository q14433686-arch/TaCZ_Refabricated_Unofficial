package com.tacz.guns.industry.recipe;

import com.google.gson.JsonElement;
import com.tacz.guns.GunMod;
import com.tacz.guns.industry.IndustryProfileManager;
import com.tacz.guns.resource.CommonAssetsManager;
import com.tacz.guns.resource.manager.CommonDataManager;
import com.tacz.guns.resource.network.DataType;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.profiling.ProfilerFiller;

import java.util.Map;

/** Loads data-driven recipes for the dedicated cartridge assembly machine. */
public final class CartridgeAssemblyRecipeManager extends CommonDataManager<CartridgeAssemblyDefinition> {
    public CartridgeAssemblyRecipeManager() {
        super(DataType.CARTRIDGE_ASSEMBLY, CartridgeAssemblyDefinition.class, CommonAssetsManager.GSON,
                "industry/cartridge_assembly", "CartridgeAssemblyLoader");
    }

    @Override
    protected void apply(Map<Identifier, JsonElement> objects, ResourceManager resourceManager, ProfilerFiller profiler) {
        if (!IndustryProfileManager.isCreateFlyProfileActive()) {
            super.apply(Map.<Identifier, JsonElement>of(), resourceManager, profiler);
            return;
        }
        super.apply(objects, resourceManager, profiler);
    }

    @Override
    protected CartridgeAssemblyDefinition parseJson(JsonElement element) {
        CartridgeAssemblyDefinition definition = super.parseJson(element);
        if (definition == null || !definition.isValid()) {
            GunMod.LOGGER.error("Invalid dedicated cartridge assembly definition: {}", element);
            return null;
        }
        return definition;
    }
}
