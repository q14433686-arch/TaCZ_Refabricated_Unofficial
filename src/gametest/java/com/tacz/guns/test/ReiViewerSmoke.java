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
    private static me.shedaniel.rei.api.common.display.Display crafting(String id) {
        return DisplayRegistry.getInstance().get(me.shedaniel.rei.plugin.common.BuiltinPlugin.CRAFTING).stream()
                .filter(d -> d.getDisplayLocation().filter(Identifier.parse(id)::equals).isPresent()).findFirst().orElse(null);
    }

    void checkLateSync(net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext ctx) {
        // An integrated server can resend when REI reloads both sides. Require the SAME live synced
        // display objects, not simply a nonempty list some later packet could have recreated.
        ctx.waitFor(mc -> !PluginManager.areAnyReloading() && crafting("tacz:gun_smith_table") != null
                && crafting("minecraft:crafting_table") != null && !DisplayRegistry.getInstance().get(REIClientPlugin.GUN_AMMO_QUERY).isEmpty(), 1200);
        var before = ctx.computeOnClient(mc -> java.util.List.of(crafting("tacz:gun_smith_table"), crafting("minecraft:crafting_table"),
                DisplayRegistry.getInstance().get(REIClientPlugin.GUN_AMMO_QUERY).getFirst()));
        ctx.runOnClient(mc -> com.tacz.guns.client.compat.RecipeViewerReloadBridge.requestReload());
        ctx.waitFor(mc -> !PluginManager.areAnyReloading() && !DisplayRegistry.getInstance().get(REIClientPlugin.GUN_AMMO_QUERY).contains(before.get(2))
                && forward(EXISTING), 1200);
        ctx.runOnClient(mc -> {
            if (crafting("tacz:gun_smith_table") != before.get(0) || crafting("minecraft:crafting_table") != before.get(1))
                throw new AssertionError("Gun-pack refresh replaced authoritative vanilla crafting displays");
        });
    }

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
