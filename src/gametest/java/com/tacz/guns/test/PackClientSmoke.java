package com.tacz.guns.test;

import com.tacz.guns.GunMod;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.block.entity.GunPegboardBlockEntity;
import com.tacz.guns.block.entity.GunDisplayCaseBlockEntity;
import com.tacz.guns.init.ModBlocks;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.attachment.AttachmentType;
import com.tacz.guns.network.message.ClientMessageSelectRefitOption;
import com.tacz.guns.util.DefaultGunAttachments;
import com.tacz.guns.api.item.builder.GunItemBuilder;
import com.tacz.guns.resource.network.CommonNetworkCache;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.world.level.GameType;
import net.minecraft.resources.Identifier;
import net.minecraft.world.InteractionHand;

/** A real join, cache sync and held-item render; the screenshot is evidence, not a pixel oracle. */
public class PackClientSmoke implements FabricClientGameTest {
    @Override public void runTest(ClientGameTestContext context) {
        String viewer = System.getProperty("tacz.test.viewer");
        if (viewer != null) {
            ViewerSmoke.run(context, viewer);
            return;
        }
        var id = Identifier.parse("tacz:ak47");
        GunMod.LOGGER.info("PORT_SMOKE_CLIENT: creating world");
        WorldReturnSmoke.Saved saved;
        try (var world = context.worldBuilder().create()) {
            context.waitFor(client -> CommonNetworkCache.INSTANCE.getGunIndex(id) != null
                    && TimelessAPI.getClientGunIndex(id).isPresent());
            GunMod.LOGGER.info("PORT_SMOKE_CLIENT: default client index and sync installed");
            context.runOnClient(mc -> DefaultClientCatalogSmoke.client());
            world.getServer().runOnServer(server -> world.getConnection().getServerPlayer().setItemInHand(
                    InteractionHand.MAIN_HAND, GunItemBuilder.create().setId(id).setAmmoCount(3).build()));
            context.waitFor(client -> client.player.getMainHandItem().getItem() instanceof IGun);
            world.getConnection().waitForChunksRender();
            context.waitTicks(30);
            saveFrame(context, "tacz_held_default_gun");
            if (Boolean.getBoolean("tacz.test.controller")) { ControllerSmoke.run(context, world); return; }
            if (!Boolean.getBoolean("tacz.test.iris")) RackPreviewSmoke.run(context, world, id);
            PortInputSmoke.run(context, world);
            ScopeUserRegressionSmoke.run(context, world);
            if (Boolean.getBoolean("tacz.test.iris")) {
                context.runOnClient(mc -> {
                    if (!com.tacz.guns.compat.iris.IrisCompat.isUsingRenderPack())
                        throw new AssertionError("Iris runtime must have a shader pack active, not just be installed");
                    GunMod.LOGGER.info("PORT_IRIS: active shader pack on {}", com.mojang.blaze3d.systems.RenderSystem.getDevice());
                });
                ScopeLensSmoke.run(context, world);
                ThermalOverlaySmoke.run(context, world);
                RangefinderSmoke.run(context, world);
                ImpactMarkerSmoke.run(context, world);
                ShotConsequencesSmoke.run(context, world);
                MovementRulesSmoke.run(context, world);
                MuzzleEffectsSmoke.run(context, world);
                PlayerFeedbackSmoke.run(context, world);
                CoreCompletionSmoke.run(context, world);
                SnowstormSmoke.run(context, world);
                RemoteParticleSmoke.run(context, world);
                ConfigPanelSmoke.run(context);
                OpticInstallSmoke.run(context, world);
                GunMod.LOGGER.info("PORT_IRIS: thermal and new-item regression passed");
                return;
            }
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
            saveFrame(context, "tacz_held_variant");
            DefaultRefitSmoke.run(context, world);
            saveFrame(context, "tacz_default_refit");
            RangefinderSmoke.run(context, world);
            ImpactMarkerSmoke.run(context, world);
            ScopeLensSmoke.run(context, world);
            TrackingActionSmoke.run(context, world);
            ShotConsequencesSmoke.run(context, world);
            MovementRulesSmoke.run(context, world);
            MuzzleEffectsSmoke.run(context, world);
            PlayerFeedbackSmoke.run(context, world);
            ConfigPanelSmoke.run(context);
            OpticInstallSmoke.run(context, world);
            CoreCompletionSmoke.run(context, world);
            SnowstormSmoke.run(context, world);
                RemoteParticleSmoke.run(context, world);
            world.getServer().runOnServer(server -> world.getConnection().getServerPlayer().setGameMode(GameType.CREATIVE));
            context.waitFor(client -> client.player.isCreative());
            var rack = checkRacks(context, world, id);
            var oldDisplay = ExternalRigSmoke.check(context);
            try { CreativeReloadSmoke.run(context, world); }
            catch (java.io.IOException e) { throw new java.io.UncheckedIOException(e); }
            ExternalRigSmoke.afterReload(context, oldDisplay);
            SessionClockSmoke.pauseAndResume(context);
            saved = WorldReturnSmoke.prepare(context, world, rack);
            GunMod.LOGGER.info("PORT_SMOKE_CLIENT: save prepared; closing first integrated world");
        }
        GunMod.LOGGER.info("PORT_SMOKE_CLIENT: first integrated world closed; checking logout and reopening save");
        WorldReturnSmoke.reopen(context, saved);
    }

    private static BlockPos checkRacks(ClientGameTestContext context, TestSingleplayerContext world, Identifier gunId) {
        var pos = context.computeOnClient(mc -> mc.player.blockPosition().south(3).above());
        world.getServer().runOnServer(server -> {
            var player = world.getConnection().getServerPlayer();
            var level = player.level();
            var gun = GunItemBuilder.create().setId(gunId).build();
            player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            level.setBlock(pos, ModBlocks.GLOW_GUN_PEGBOARD.defaultBlockState(), Block.UPDATE_ALL);
            level.setBlock(pos.east(), ModBlocks.GUN_DISPLAY_CASE.defaultBlockState(), Block.UPDATE_ALL);
            var board = (GunPegboardBlockEntity) level.getBlockEntity(pos);
            var cabinet = (GunDisplayCaseBlockEntity) level.getBlockEntity(pos.east());
            board.setColor(0x4088bb, false);
            var mesh = gun.copy();
            ((IGun) mesh.getItem()).setGunDisplayId(mesh, ExternalRigSmoke.MESH);
            if (!board.setItem(6, gun) || !board.rotateItem(6) || !cabinet.setItem(9, mesh)) throw new AssertionError("Couldn't populate display racks");
        });
        context.waitFor(mc -> mc.level.getBlockEntity(pos) instanceof GunPegboardBlockEntity board && board.getRenderData() == 0x4088bb
                && board.getRotation(6) == 1 && board.getItem(6).getItem() instanceof IGun g && g.getGunId(board.getItem(6)).equals(gunId)
                && mc.level.getBlockEntity(pos.east()) instanceof GunDisplayCaseBlockEntity cabinet && !cabinet.getItem(9).isEmpty());
        context.runOnClient(mc -> {
            var ammo = TimelessAPI.getClientAmmoIndex(Identifier.parse("tacz:300blk")).orElseThrow();
            if (ammo.getShellModel() == null || ammo.getHitSurfaceParticles() == null || ammo.getHitSurfaceParticles().getFirst().getCountMax() != 3)
                throw new AssertionError("Default ammo shell/display did not resolve");
            // The top face of the cabinet has a stable collision surface, independent of terrain.
            var cabinet = pos.east();
            var provider = new com.tacz.guns.client.particle.BulletHoleParticle.Provider();
            for (String display : java.util.List.of("tacz:ak47_display", "port_test:quiet_impact")) {
                var option = new com.tacz.guns.particles.BulletHoleOption(net.minecraft.core.Direction.UP, cabinet, "tacz:300blk", "tacz:ak47", display);
                var decal = provider.createParticle(option, mc.level, cabinet.getX() + 0.5, cabinet.getY() + 1, cabinet.getZ() + 0.5, 0, 0, 0, mc.level.getRandom());
                if ((decal != null) != display.startsWith("tacz:")) throw new AssertionError("Wrong decal for " + display);
            }
        });
        context.getInput().lookAt(pos);
        world.getConnection().waitForChunksRender();
        context.waitTicks(20);
        saveFrame(context, "tacz_display_wall");
        return pos;
    }

    static void saveFrame(ClientGameTestContext context, String name) {
        var path = context.takeScreenshot(name);
        try {
            long bytes = java.nio.file.Files.size(path);
            if (bytes <= 0) throw new AssertionError("Empty screenshot: " + path);
            GunMod.LOGGER.info("PORT_SMOKE_CLIENT: saved {} ({} bytes)", path, bytes);
        } catch (java.io.IOException e) {
            throw new AssertionError("Unreadable screenshot: " + path, e);
        }
    }
}
