package com.tacz.guns.test;

import com.tacz.guns.GunMod;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.init.ModCreativeTabs;
import net.fabricmc.fabric.api.client.creativetab.v1.FabricCreativeModeInventoryScreen;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.ItemStack;

import java.io.IOException;

/** Keep one creative screen and one typed search open across an actual server reload/pack sync. */
final class CreativeReloadSmoke {
    private static final Identifier PROBE = Identifier.parse("reload_smoke:creative_probe");

    static void run(ClientGameTestContext context, TestSingleplayerContext world) throws IOException {
        var screen = context.computeOnClient(mc -> new CreativeModeInventoryScreen(mc.player, mc.player.connection.enabledFeatures(), true));
        context.setScreen(() -> screen);
        context.waitForScreen(CreativeModeInventoryScreen.class);
        var tabs = (FabricCreativeModeInventoryScreen) screen;
        var rifle = ModCreativeTabs.GUN_RIFLE_TAB;
        try {
            context.runOnClient(mc -> tabs.setSelectedTab(rifle));
            context.clickScreenButton("port_test"); // pack_info missing: namespace label and fallback item icon
            context.runOnClient(mc -> {
                assertFiltered(screen, "port_test");
                if (rifle.getDisplayItems().size() <= screen.getMenu().items.size()) throw new AssertionError("Filter mutated the tab");
                tabs.setSelectedTab(ModCreativeTabs.GUN_DMR_TAB);
                if (screen.getMenu().items.stream().noneMatch(s -> hasId(s, Identifier.parse("tacz:mk14"))))
                    throw new AssertionError("Filter from another tab hid the DMR catalog");
                tabs.setSelectedTab(CreativeModeTabs.searchTab());
            });
            context.getInput().typeChars("zzpackreload");
            context.waitFor(mc -> screen.getMenu().items.isEmpty());
            ConversionReloadSmoke.run(context, world);
            context.waitFor(mc -> screen.getMenu().items.size() == 1 && hasId(screen.getMenu().items.getFirst(), PROBE), 600);
            context.runOnClient(mc -> {
                if (mc.gui.screen() != screen) throw new AssertionError("Creative screen was replaced during reload");
                tabs.setSelectedTab(rifle);
                assertFiltered(screen, "port_test");
            });
            context.clickScreenButton("reload_smoke");
            context.runOnClient(mc -> {
                if (screen.getMenu().items.size() != 1 || !hasId(screen.getMenu().items.getFirst(), PROBE))
                    throw new AssertionError("Wrong item for the new pack button");
            });
            context.clickScreenButton("gui.tacz.creative_pack_filter.all");
            context.runOnClient(mc -> {
                if (rifle.getDisplayItems().size() != screen.getMenu().items.size()) throw new AssertionError("All did not restore the tab");
            });
            GunMod.LOGGER.info("PORT_CREATIVE: open search refreshed on sync; per-tab selections, new pack button and All passed");
        } finally {
            context.setScreen(() -> null);
        }
    }

    private static boolean hasId(ItemStack stack, Identifier id) {
        return stack.getItem() instanceof IGun gun && id.equals(gun.getGunId(stack));
    }

    private static void assertFiltered(CreativeModeInventoryScreen screen, String namespace) {
        if (screen.getMenu().items.isEmpty() || screen.getMenu().items.stream().anyMatch(s ->
                !(s.getItem() instanceof IGun gun) || !namespace.equals(gun.getGunId(s).getNamespace())))
            throw new AssertionError("Creative filter did not isolate " + namespace);
    }
}
