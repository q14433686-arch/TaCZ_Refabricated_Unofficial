package com.tacz.guns.test;

import com.tacz.guns.GunMod;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.builder.GunItemBuilder;
import com.tacz.guns.resource.network.CommonNetworkCache;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.resources.Identifier;
import net.minecraft.world.InteractionHand;

import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/** Each invocation starts a fresh client/save; a bare-classpath or two-mod pass cannot stand
 * in for either optional API. Run with -PtaczClientSmoke -PtaczPlayerSuite=<lane>. */
public final class PlayerSuites implements FabricClientGameTest {
    @Override public void runTest(ClientGameTestContext ctx) {
        String suite = System.getProperty("tacz.test.player");
        var loader = FabricLoader.getInstance();
        var actual = Stream.of("iris", "sodium", "jei", "roughlyenoughitems", "controllable", "player_animation_library")
                .filter(loader::isModLoaded).collect(Collectors.toSet());
        if (!actual.equals(switch (suite) { case "pal" -> Set.of("player_animation_library"); case "controller" -> Set.of("controllable"); default -> Set.of(); }))
            throw new AssertionError("Wrong combination for " + suite + ": " + actual);
        try (var world = ctx.worldBuilder().create()) {
            var ak = Identifier.parse("tacz:ak47");
            ctx.waitFor(mc -> CommonNetworkCache.INSTANCE.getGunIndex(ak) != null && TimelessAPI.getClientGunIndex(ak).isPresent());
            world.getServer().runOnServer(s -> s.getPlayerList().getPlayers().getFirst().setItemInHand(InteractionHand.MAIN_HAND,
                    GunItemBuilder.create().setId(ak).setAmmoCount(3).build()));
            ctx.waitFor(mc -> mc.player.getMainHandItem().getItem() instanceof IGun);
            world.getClientLevel().waitForChunksRender();
            ctx.waitTicks(30);
            switch (suite) {
                case "hud" -> { ViewTuningSmoke.run(ctx, world); SessionClockSmoke.pauseAndResume(ctx); }
                case "actions" -> {
                    PortInputSmoke.run(ctx, world); MovementRulesSmoke.run(ctx, world); ShotConsequencesSmoke.run(ctx, world);
                    ConfigPanelSmoke.run(ctx); CoreCompletionSmoke.run(ctx, world); DefaultRefitSmoke.run(ctx, world);
                }
                case "pal" -> PalRuntimeSmoke.run(ctx);
                case "controller" -> ControllerSmoke.run(ctx, world);
                default -> throw new AssertionError("Unknown player suite " + suite);
            }
            GunMod.LOGGER.info("PORT_PLAYER: {} complete, optional={}", suite, actual);
        }
    }

    static void frame(ClientGameTestContext ctx, String name) {
        var image = ClientProbe.read(ctx.takeScreenshot(name));
        if (image == null || image.getWidth() < 1) throw new AssertionError("Empty frame: " + name);
    }
}
