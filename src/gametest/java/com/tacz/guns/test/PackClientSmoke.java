package com.tacz.guns.test;

import java.io.IOException;
import java.io.UncheckedIOException;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.resource.network.CommonNetworkCache;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.resources.Identifier;

public final class PackClientSmoke implements FabricClientGameTest {
    @Override public void runTest(ClientGameTestContext context) {
        if (System.getProperty("tacz.test.player") != null) {
            // This optional test source set (never the mod) may link PAL/SDL's test APIs.
            try { Class.forName("com.tacz.guns.test.PlayerSuites").asSubclass(FabricClientGameTest.class)
                    .getConstructor().newInstance().runTest(context); }
            catch (ReflectiveOperationException e) { throw new AssertionError("Player suite not built", e); }
            return;
        }
        var id = Identifier.parse("tacz:ak47");
        try (var world = context.worldBuilder().create()) {
            context.waitFor(mc -> CommonNetworkCache.INSTANCE.getGunIndex(id) != null && TimelessAPI.getClientGunIndex(id).isPresent());
            var optics = System.getProperty("tacz.test.optics");
            if (optics != null) { ShaderOpticSmoke.run(context, world, optics); return; }
            if (Boolean.getBoolean("tacz.test.iris")) {
                world.getClientLevel().waitForChunksRender(); context.waitTicks(25);
                if (System.getProperty("tacz.test.irisColor") == null) {
                    context.runOnClient(mc -> ShaderOpticSmoke.requirePack("tacz-regression"));
                    ScopeUserRegressionSmoke.run(context, world);
                    SnowstormMaterialSmoke.run(context, world);
                } else {
                    ReimaginedSmoke.run(context, world); // checks the actual loaded pack, options and shadow texture
                    SurfaceLightSmoke.run(context, world, false);
                }
                com.tacz.guns.GunMod.LOGGER.info("PORT_IRIS: material suite complete");
                return;
            }
            var render = System.getProperty("tacz.test.render");
            if (render != null) {
                if (net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("iris"))
                    throw new AssertionError("These baselines must exercise the native unshaded backend");
                world.getClientLevel().waitForChunksRender(); context.waitTicks(25);
                switch (render) {
                    case "muzzle" -> MuzzleEffectsSmoke.run(context, world);
                    case "tracers" -> {
                        world.getServer().runOnServer(s -> s.getPlayerList().getPlayers().getFirst().setItemInHand(
                                net.minecraft.world.InteractionHand.MAIN_HAND, com.tacz.guns.api.item.builder.GunItemBuilder.create().setId(id).build()));
                        ClientProbe.waitForServer(context, world, s -> com.tacz.guns.api.entity.IGunOperator.fromLivingEntity(
                                s.getPlayerList().getPlayers().getFirst()).getCacheProperty() != null);
                        context.waitFor(mc -> mc.player.getMainHandItem().getItem() instanceof com.tacz.guns.api.item.IGun && mc.player.onGround());
                        TracerFrameSmoke.run(context);
                        SurfaceFragmentSmoke.run(context, world);
                        WorldTracerSmoke.run(context, world);
                        PlayerFeedbackSmoke.run(context, world);
                    }
                    case "displays" -> {
                        context.waitFor(mc -> mc.player.onGround());
                        context.runOnClient(TargetPoseSmoke::run);
                        RackPreviewSmoke.run(context, world, id);
                    }
                    case "particles" -> { SnowstormSmoke.run(context, world); RemoteParticleSmoke.run(context, world); }
                    case "readouts" -> {
                        RangefinderSmoke.run(context, world);
                        ImpactMarkerSmoke.run(context, world);
                        AdsTransitionSmoke.run(context, world);
                    }
                    case "receivers" -> { SurfaceLightSmoke.run(context, world, true); RackLoadSmoke.run(context, world); }
                    default -> throw new IllegalArgumentException("Unknown render suite " + render);
                }
                com.tacz.guns.GunMod.LOGGER.info("PORT_RENDER: {} complete", render);
                return;
            }
            context.runOnClient(mc -> DefaultClientCatalogSmoke.client());
            ConversionReloadSmoke.run(context, world);
            context.runOnClient(mc -> DefaultClientCatalogSmoke.client());
        } catch (IOException ex) { throw new UncheckedIOException(ex); }
    }
}
