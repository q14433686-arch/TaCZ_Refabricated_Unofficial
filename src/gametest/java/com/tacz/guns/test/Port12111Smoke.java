package com.tacz.guns.test;

import com.tacz.guns.GunMod;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.item.builder.GunItemBuilder;
import com.tacz.guns.resource.network.CommonNetworkCache;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.resources.Identifier;
import net.minecraft.world.InteractionHand;

/** A real client and integrated server, rather than a constructed resource manager. */
public final class Port12111Smoke implements FabricClientGameTest {
    private static final Identifier GUN = Identifier.parse("tacz:qr_24");

    @Override
    public void runTest(ClientGameTestContext context) {
        try (var world = context.worldBuilder().create()) {
            context.waitFor(client -> client.level != null && client.player != null
                    && TimelessAPI.getClientGunIndex(GUN).isPresent()
                    && CommonNetworkCache.INSTANCE.getGunIndex(GUN) != null
                    && CommonNetworkCache.INSTANCE.getSlotAdapterIndex(Identifier.parse("tacz:riser_pistol_high")) != null,
                    1200);
            context.runOnClient(client -> client.getSingleplayerServer().execute(() -> {
                var player = client.getSingleplayerServer().getPlayerList().getPlayers().getFirst();
                player.setItemInHand(InteractionHand.MAIN_HAND, GunItemBuilder.create().setId(GUN).build());
            }));
            context.waitFor(client -> client.player.getMainHandItem().getItem() instanceof com.tacz.guns.api.item.IGun gun
                    && GUN.equals(gun.getGunId(client.player.getMainHandItem())), 300);
            context.waitTicks(30);
            context.takeScreenshot("tacz_12111_catalog_and_viewmodel");
        }
        GunMod.LOGGER.info("PORT_12111_CLIENT: live world, extended pack snapshot and held model passed");
    }
}
