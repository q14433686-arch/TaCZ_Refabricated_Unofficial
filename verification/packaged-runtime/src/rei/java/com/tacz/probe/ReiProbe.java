package com.tacz.probe;

import cn.sh1rocu.tacz.compat.rei.REIClientPlugin;
import com.tacz.guns.client.compat.RecipeViewerReloadBridge;
import com.tacz.guns.client.gui.GunSmithTableScreen;
import com.tacz.guns.init.ModItems;
import me.shedaniel.rei.api.client.registry.transfer.TransferHandler;
import me.shedaniel.rei.api.client.registry.transfer.TransferHandlerRegistry;
import me.shedaniel.rei.api.common.display.Display;
import me.shedaniel.rei.api.common.entry.comparison.ComparisonContext;
import me.shedaniel.rei.api.common.entry.comparison.ItemComparatorRegistry;
import me.shedaniel.rei.api.common.entry.type.VanillaEntryTypes;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.world.item.ItemStack;
import cn.sh1rocu.tacz.compat.rei.display.GunSmithTableDisplay;
import me.shedaniel.rei.api.client.registry.display.DisplayRegistry;
import me.shedaniel.rei.api.common.category.CategoryIdentifier;
import me.shedaniel.rei.api.common.plugins.PluginManager;
import me.shedaniel.rei.impl.client.registry.display.DisplayRegistryImpl;
import me.shedaniel.rei.plugin.common.BuiltinPlugin;
import net.minecraft.resources.Identifier;

public final class ReiProbe implements ViewerProbe {
    private static final CategoryIdentifier<GunSmithTableDisplay> AMMO = CategoryIdentifier.of("tacz", "plugins/gun_smith_table/tacz_ammo_workbench");
    private static final Identifier TABLE = Identifier.parse("tacz:gun_smith_table");

    private static boolean tableOutput(Display d) {
        return d.getOutputEntries().stream().flatMap(java.util.Collection::stream)
                .anyMatch(e -> e.getType() == VanillaEntryTypes.ITEM && e.<ItemStack>castValue().is(ModItems.GUN_SMITH_TABLE));
    }
    private static Display ordinaryTable() {
        var registry = DisplayRegistry.getInstance();
        return registry.get(BuiltinPlugin.CRAFTING).stream()
                .filter(d -> registry.getDisplayOrigin(d) == DisplayRegistryImpl.SYNCED
                        && d.getDisplayLocation().filter(TABLE::equals).isPresent() && tableOutput(d)).findFirst().orElse(null);
    }
    public void checkInstalled(ClientGameTestContext ctx, Identifier live) {
        // Make the ordinary-first / pack-second ordering explicit. A new SET or a synthetic
        // local recipe with the same output is not the original server display object.
        var original = ctx.computeOnClient(mc -> ordinaryTable());
        var oldPack = ctx.computeOnClient(mc -> DisplayRegistry.getInstance().get(AMMO).stream()
                .filter(d -> live.equals(d.getRecipe().getId())).findFirst().orElseThrow());
        ctx.runOnClient(mc -> RecipeViewerReloadBridge.requestReload());
        ctx.waitFor(mc -> !PluginManager.areAnyReloading() && DisplayRegistry.getInstance().get(AMMO).stream()
                .anyMatch(d -> live.equals(d.getRecipe().getId()) && d != oldPack), 1200);
        ctx.runOnClient(mc -> {
            if (original == null || ordinaryTable() != original) throw new AssertionError("Pack refresh replaced REI's server table");
            ViewerProbe.checkTableKeys(s -> ItemComparatorRegistry.getInstance().hashOf(ComparisonContext.FUZZY, s));
        });
    }
    public boolean select(GunSmithTableScreen screen, Identifier id, boolean commit) {
        var display = DisplayRegistry.getInstance().get(AMMO).stream().filter(d -> id.equals(d.getRecipe().getId())).findFirst().orElseThrow();
        var context = TransferHandler.Context.create(commit, false, screen, display);
        for (var handler : TransferHandlerRegistry.getInstance()) {
            if (!handler.checkApplicable(context).isSuccessful()) continue;
            var result = handler.handle(context);
            if (result.isSuccessful()) return result.isBlocking() && result.isReturningToScreen();
            if (result.isBlocking()) return false;
        }
        return false;
    }
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
        return "crafting displays=" + ordinary.size() + ", synced=" + ordinary.stream()
                .filter(d -> registry.getDisplayOrigin(d) == DisplayRegistryImpl.SYNCED).count()
                + ", matching location=" + ordinary.stream().filter(d -> d.getDisplayLocation().filter(TABLE::equals).isPresent()).count()
                + ", matching output=" + ordinary.stream().filter(ReiProbe::tableOutput).count();
    }
    public boolean hasOrdinaryCrafting() {
        return !PluginManager.areAnyReloading() && ordinaryTable() != null;
    }
}
