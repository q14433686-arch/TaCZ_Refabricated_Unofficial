package com.tacz.probe;

import cn.sh1rocu.tacz.compat.rei.REIClientPlugin;
import cn.sh1rocu.tacz.compat.rei.display.GunSmithTableDisplay;
import me.shedaniel.rei.api.client.registry.display.DisplayRegistry;
import me.shedaniel.rei.api.common.category.CategoryIdentifier;
import me.shedaniel.rei.api.common.plugins.PluginManager;
import me.shedaniel.rei.impl.client.registry.display.DisplayRegistryImpl;
import me.shedaniel.rei.plugin.common.BuiltinPlugin;
import net.minecraft.resources.Identifier;

public final class ReiProbe implements ViewerProbe {
    private static final CategoryIdentifier<GunSmithTableDisplay> AMMO = CategoryIdentifier.of("tacz", "plugins/gun_smith_table/tacz_ammo_workbench");
    public boolean hasGun(Identifier id) {
        return !PluginManager.areAnyReloading() && DisplayRegistry.getInstance().get(REIClientPlugin.GUN_AMMO_QUERY).stream()
                .anyMatch(d -> ViewerProbe.gun(d.getEntry().getGunStack(), id));
    }
    public boolean hasWorkbenchRecipe(Identifier id) {
        return !PluginManager.areAnyReloading() && DisplayRegistry.getInstance().get(AMMO).stream().anyMatch(d -> id.equals(d.getRecipe().getId()));
    }
    public String detail() {
        if (PluginManager.areAnyReloading()) return "REI reloading";
        var registry = DisplayRegistry.getInstance();
        var ordinary = registry.get(BuiltinPlugin.CRAFTING);
        var table = Identifier.parse("tacz:gun_smith_table");
        return "crafting displays=" + ordinary.size() + ", synced=" + ordinary.stream()
                .filter(d -> registry.getDisplayOrigin(d) == DisplayRegistryImpl.SYNCED).count()
                + ", matching location=" + ordinary.stream().filter(d -> d.getDisplayLocation().filter(table::equals).isPresent()).count();
    }
    public boolean hasOrdinaryCrafting() {
        var displays = DisplayRegistry.getInstance();
        return !PluginManager.areAnyReloading() && displays.get(BuiltinPlugin.CRAFTING).stream()
                .anyMatch(d -> displays.getDisplayOrigin(d) == DisplayRegistryImpl.SYNCED && d.getDisplayLocation()
                        .filter(Identifier.parse("tacz:gun_smith_table")::equals).isPresent());
    }
}
