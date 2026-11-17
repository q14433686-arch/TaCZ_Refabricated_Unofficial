package com.tacz.guns.test;

import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.builder.GunItemBuilder;
import com.tacz.guns.resource.network.CommonNetworkCache;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.resources.Identifier;
import net.minecraft.world.InteractionHand;

/** A real join, cache sync and held-item render; the screenshot is evidence, not a pixel oracle. */
public class PackClientSmoke implements FabricClientGameTest {
    @Override public void runTest(ClientGameTestContext context) {
        var id = Identifier.parse("tacz:ak47");
        try (var world = context.worldBuilder().create()) {
            context.waitFor(client -> CommonNetworkCache.INSTANCE.getGunIndex(id) != null
                    && TimelessAPI.getClientGunIndex(id).isPresent());
            world.getServer().runOnServer(server -> world.getConnection().getServerPlayer().setItemInHand(
                    InteractionHand.MAIN_HAND, GunItemBuilder.create().setId(id).setAmmoCount(3).build()));
            context.waitFor(client -> client.player.getMainHandItem().getItem() instanceof IGun);
            world.getConnection().waitForChunksRender();
            context.waitTicks(30);
            context.takeScreenshot("tacz_held_default_gun");
        }
    }
}
