package com.tacz.guns.industry.recipe;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.tacz.guns.GunMod;
import com.tacz.guns.industry.IndustryProfileManager;
import com.tacz.guns.industry.ammo.CartridgeStandardDefinition;
import com.tacz.guns.industry.ammo.CartridgeStandardService;
import com.tacz.guns.resource.CommonAssetsManager;
import com.tacz.guns.resource.manager.CommonDataManager;
import com.tacz.guns.resource.network.DataType;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.profiling.ProfilerFiller;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

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
        Map<Identifier, JsonElement> effective = new LinkedHashMap<>(objects);
        Set<String> explicitlyDeclaredAmmo = new HashSet<>();
        for (JsonElement value : objects.values()) {
            if (value != null && value.isJsonObject()) {
                JsonObject object = value.getAsJsonObject();
                if (object.has("ammo") && object.get("ammo").isJsonPrimitive()) {
                    explicitlyDeclaredAmmo.add(object.get("ammo").getAsString());
                }
            }
        }
        var tableManager = CommonAssetsManager.getInstance() == null ? null
                : CommonAssetsManager.getInstance().getTableRecipeManager();
        if (tableManager != null) {
            int added = 0;
            for (Map.Entry<Identifier, JsonElement> entry : tableManager.getSurveyedCartridgeDefinitions().entrySet()) {
                JsonElement value = entry.getValue();
                if (value == null || !value.isJsonObject()) {
                    continue;
                }
                JsonObject definition = value.getAsJsonObject();
                String ammo = definition.has("ammo") && definition.get("ammo").isJsonPrimitive()
                        ? definition.get("ammo").getAsString() : "";
                // A gun-pack author/data pack may provide an exact cartridge
                // definition for the same AmmoId. It always wins over our
                // generic surveyed definition, even if ids differ.
                if (ammo.isBlank() || explicitlyDeclaredAmmo.contains(ammo) || effective.containsKey(entry.getKey())) {
                    continue;
                }
                effective.put(entry.getKey(), value);
                added++;
            }
            if (added > 0) {
                GunMod.LOGGER.info("Added {} surveyed third-party cartridge assembly definition(s).", added);
            }
        }
        super.apply(effective, resourceManager, profiler);
    }

    @Override
    protected CartridgeAssemblyDefinition parseJson(JsonElement element) {
        CartridgeAssemblyDefinition definition = super.parseJson(element);
        if (definition == null || !definition.isValid()) {
            GunMod.LOGGER.error("Invalid dedicated cartridge assembly definition: {}", element);
            return null;
        }
        CartridgeStandardDefinition standard = CartridgeStandardService.getStandard(definition.getAmmo());
        if (standard != null && (!standard.getCartridgeCaliber().equals(definition.getCaseCaliber())
                || !standard.getCartridgeCaliber().equals(definition.getProjectileCaliber()))) {
            GunMod.LOGGER.error(
                    "Ignoring cartridge assembly definition for {}: case/projectile calibre must match cartridge standard {} ({}).",
                    definition.getAmmo(), standard.getCanonicalAmmo(), standard.getCartridgeCaliber()
            );
            return null;
        }
        return definition;
    }
}
