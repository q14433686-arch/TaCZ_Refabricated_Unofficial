package com.tacz.guns.test;

import com.tacz.guns.GunMod;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.attachment.AttachmentType;
import com.tacz.guns.network.message.ClientMessageSelectRefitOption;
import com.tacz.guns.util.DefaultGunAttachments;
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
        GunMod.LOGGER.info("PORT_SMOKE_CLIENT: creating world");
        try (var world = context.worldBuilder().create()) {
            context.waitFor(client -> CommonNetworkCache.INSTANCE.getGunIndex(id) != null
                    && TimelessAPI.getClientGunIndex(id).isPresent());
            GunMod.LOGGER.info("PORT_SMOKE_CLIENT: default client index and sync installed");
            world.getServer().runOnServer(server -> world.getConnection().getServerPlayer().setItemInHand(
                    InteractionHand.MAIN_HAND, GunItemBuilder.create().setId(id).setAmmoCount(3).build()));
            context.waitFor(client -> client.player.getMainHandItem().getItem() instanceof IGun);
            world.getConnection().waitForChunksRender();
            context.waitTicks(30);
            context.takeScreenshot("tacz_held_default_gun");
            GunMod.LOGGER.info("PORT_SMOKE_CLIENT: saved default frame");
            // Remote clients get the derived capability, never the server's logic file.
            context.waitFor(client -> {
                var index = CommonNetworkCache.INSTANCE.getGunIndex(Identifier.parse("port_test:scripted"));
                return index != null && index.hasUnloadAmmoScript() && index.getScript() == null;
            });
            var oem = Identifier.parse("port_test:oem");
            var variant = Identifier.parse("port_test:optic");
            context.waitFor(client -> CommonNetworkCache.INSTANCE.getAttachmentVariantIndex(variant) != null
                    && TimelessAPI.getClientAttachmentVariantIndex(variant).isPresent());
            GunMod.LOGGER.info("PORT_SMOKE_CLIENT: variant and script capability synced");
            world.getServer().runOnServer(server -> {
                var gun = GunItemBuilder.create().setId(oem).putAllVirtualAttachment(DefaultGunAttachments.defaults(oem)).build();
                if (!new ClientMessageSelectRefitOption(0, AttachmentType.SCOPE, ClientMessageSelectRefitOption.Kind.VARIANT, variant).apply(gun))
                    throw new AssertionError("Server rejected fixture variant");
                world.getConnection().getServerPlayer().setItemInHand(InteractionHand.MAIN_HAND, gun);
            });
            context.waitFor(client -> {
                var held = client.player.getMainHandItem();
                return held.getItem() instanceof IGun gun && oem.equals(gun.getGunId(held)) && gun.getAimingZoom(held) == 1.75f
                        && TimelessAPI.getClientAttachmentVariantIndex(gun.getAttachment(held, AttachmentType.SCOPE))
                        .map(index -> index.getAttachmentModel() != null).orElse(false);
            });
            context.waitTicks(30);
            context.takeScreenshot("tacz_held_variant");
            GunMod.LOGGER.info("PORT_SMOKE_CLIENT: saved variant frame");
        }
    }
}
