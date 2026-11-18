package com.tacz.guns.test;

import com.tacz.guns.GunMod;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.resource.network.CommonNetworkCache;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

import java.io.IOException;
import java.io.UncheckedIOException;

/** Each lane installs one real viewer. Never call the TACZ registration methods directly. */
interface ViewerSmoke {
    Identifier EXISTING = Identifier.parse("tacz:ak47");
    Identifier NEW = Identifier.parse("reload_smoke:creative_probe");

    boolean forward(Identifier id);
    boolean reverse(Identifier id);
    void open(Identifier gun);
    boolean isOpen(Screen screen);

    static boolean isGun(ItemStack stack, Identifier id) {
        return stack.getItem() instanceof IGun gun && id.equals(gun.getGunId(stack));
    }

    static void run(ClientGameTestContext ctx, String viewer) {
        var loader = FabricLoader.getInstance();
        if (loader.isModLoaded("jei") != viewer.equals("jei") || loader.isModLoaded("roughlyenoughitems") != viewer.equals("rei"))
            throw new AssertionError("Expected only the " + viewer + " runtime");
        ViewerSmoke api = switch (viewer) {
            case "jei" -> new JeiViewerSmoke();
            case "rei" -> new ReiViewerSmoke();
            default -> throw new IllegalArgumentException(viewer);
        };
        try (var world = ctx.worldBuilder().create()) {
            ctx.waitFor(mc -> CommonNetworkCache.INSTANCE.getGunIndex(EXISTING) != null && api.forward(EXISTING) && api.reverse(EXISTING), 1200);
            ctx.runOnClient(mc -> {
                if (api.forward(NEW) || api.reverse(NEW)) throw new AssertionError("New gun already in viewer before conversion");
            });
            ConversionReloadSmoke.run(ctx, world);
            ctx.waitFor(mc -> api.forward(NEW) && api.reverse(NEW), 1200);
            ctx.runOnClient(mc -> api.open(NEW));
            ctx.waitFor(mc -> api.isOpen(mc.gui.screen()));
            ctx.waitTicks(4);
            ctx.takeScreenshot("tacz_viewer_" + viewer);
            ctx.setScreen(() -> null);
            GunMod.LOGGER.info("PORT_VIEWER: {} showed the reloaded gun; both query directions changed", viewer);
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }
}
