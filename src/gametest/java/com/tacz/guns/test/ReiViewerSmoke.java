package com.tacz.guns.test;

import cn.sh1rocu.tacz.compat.rei.REIClientPlugin;
import com.tacz.guns.GunMod;
import com.tacz.guns.client.compat.RecipeViewerReloadBridge;
import me.shedaniel.rei.api.client.registry.entry.EntryRegistry;
import me.shedaniel.rei.api.common.display.Display;
import me.shedaniel.rei.api.common.entry.type.VanillaEntryTypes;
import me.shedaniel.rei.impl.client.registry.display.DisplayRegistryImpl;
import me.shedaniel.rei.plugin.common.BuiltinPlugin;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import me.shedaniel.rei.api.client.gui.screen.DisplayScreen;
import me.shedaniel.rei.api.client.registry.display.DisplayRegistry;
import me.shedaniel.rei.api.client.view.ViewSearchBuilder;
import me.shedaniel.rei.api.common.plugins.PluginManager;
import me.shedaniel.rei.api.common.util.EntryStacks;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

import java.util.List;

final class ReiViewerSmoke implements ViewerSmoke {
    private static Display syncedCrafting(String id) {
        var registry = DisplayRegistry.getInstance();
        var location = Identifier.parse(id);
        return registry.get(BuiltinPlugin.CRAFTING).stream()
                .filter(d -> d.getDisplayLocation().filter(location::equals).isPresent() && registry.getDisplayOrigin(d) == DisplayRegistryImpl.SYNCED)
                .findFirst().orElse(null);
    }

    void checkLateSync(ClientGameTestContext ctx, boolean reloaded) {
        // An integrated server can resend when REI reloads both sides. Require the SAME live SET
        // display objects, not simply a nonempty list some later packet could have recreated.
        var ids = reloaded ? List.of("tacz:gun_smith_table", "minecraft:crafting_table", "reload_smoke:paper")
                : List.of("tacz:gun_smith_table", "minecraft:crafting_table");
        ctx.waitFor(mc -> !PluginManager.areAnyReloading() && ids.stream().allMatch(id -> syncedCrafting(id) != null)
                && !DisplayRegistry.getInstance().get(REIClientPlugin.GUN_AMMO_QUERY).isEmpty(), 1200);
        var before = ctx.computeOnClient(mc -> ids.stream().map(ReiViewerSmoke::syncedCrafting).toList());
        var oldQuery = ctx.computeOnClient(mc -> DisplayRegistry.getInstance().get(REIClientPlugin.GUN_AMMO_QUERY).getFirst());
        ctx.runOnClient(mc -> RecipeViewerReloadBridge.requestReload());
        ctx.waitFor(mc -> !PluginManager.areAnyReloading() && !DisplayRegistry.getInstance().get(REIClientPlugin.GUN_AMMO_QUERY).contains(oldQuery)
                && forward(EXISTING), 1200);
        ctx.runOnClient(mc -> {
            for (int i = 0; i < ids.size(); i++) if (syncedCrafting(ids.get(i)) != before.get(i))
                throw new AssertionError("Gun-pack refresh replaced server crafting display " + ids.get(i));
            if (reloaded && EntryRegistry.getInstance().getEntryStacks().noneMatch(e -> e.getType() == VanillaEntryTypes.ITEM
                    && ViewerSmoke.isGun(e.<ItemStack>castValue(), NEW))) throw new AssertionError("Reloaded gun missing from REI sidebar");
            GunMod.LOGGER.info("PORT_REI_CRAFTING: same server-synced crafting displays before/after late pack refresh: {}", ids);
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
