package com.tacz.guns.test;

import cn.sh1rocu.tacz.compat.rei.REIClientPlugin;
import me.shedaniel.rei.api.client.gui.screen.DisplayScreen;
import me.shedaniel.rei.api.client.registry.display.DisplayRegistry;
import me.shedaniel.rei.api.client.view.ViewSearchBuilder;
import me.shedaniel.rei.api.common.plugins.PluginManager;
import me.shedaniel.rei.api.common.util.EntryStacks;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.resources.Identifier;

final class ReiViewerSmoke implements ViewerSmoke {
    @Override public boolean forward(Identifier id) {
        return !PluginManager.areAnyReloading() && DisplayRegistry.getInstance().get(REIClientPlugin.GUN_AMMO_QUERY).stream()
                .anyMatch(display -> ViewerSmoke.isGun(display.getEntry().getGunStack(), id));
    }
    @Override public boolean reverse(Identifier id) {
        return !PluginManager.areAnyReloading() && DisplayRegistry.getInstance().get(REIClientPlugin.AMMO_QUERY).stream()
                .flatMap(display -> display.getEntry().getGunStacks().stream()).anyMatch(stack -> ViewerSmoke.isGun(stack, id));
    }
    @Override public void open(Identifier gun) {
        var matches = DisplayRegistry.getInstance().get(REIClientPlugin.GUN_AMMO_QUERY).stream()
                .filter(display -> ViewerSmoke.isGun(display.getEntry().getGunStack(), gun)).toList();
        if (matches.size() != 1 || !ViewSearchBuilder.builder().filterCategory(REIClientPlugin.GUN_AMMO_QUERY)
                .addRecipesFor(EntryStacks.of(matches.getFirst().getEntry().getGunStack())).open())
            throw new AssertionError("REI couldn't open one gun→ammo query for " + gun);
    }
    @Override public boolean isOpen(Screen screen) {
        return screen instanceof DisplayScreen display && REIClientPlugin.GUN_AMMO_QUERY.equals(display.getCurrentCategoryId());
    }
}
