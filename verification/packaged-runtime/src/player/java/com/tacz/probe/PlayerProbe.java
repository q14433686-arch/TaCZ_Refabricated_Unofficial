package com.tacz.probe;

import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.builder.GunItemBuilder;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.InteractionHand;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/** Real production mod classpath, one optional integration per launch. */
abstract class PlayerProbe implements FabricClientGameTest {
    abstract void exercise(ClientGameTestContext ctx, TestSingleplayerContext world);

    @Override public final void runTest(ClientGameTestContext ctx) {
        try { PackagedConnectionProbe.requireProduct(); } catch (IOException e) { throw new UncheckedIOException(e); }
        var loader = FabricLoader.getInstance();
        String suite = System.getProperty("tacz.probe.player");
        boolean iris = suite.endsWith("-iris");
        String id = switch (suite) { case "platform-zoomify" -> "zoomify"; case "pal" -> "player_animation_library"; case "controller" -> "controllable"; case "display", "optics", "receiver", "platform" -> ""; case "optics-iris", "receiver-iris", "user-unbound-iris" -> "iris"; default -> throw new AssertionError(suite); };
        var loaded = Stream.of("iris", "sodium", "jei", "roughlyenoughitems", "player_animation_library", "controllable", "zoomify").filter(loader::isModLoaded).collect(Collectors.toSet());
        var expected = iris ? Set.of("iris", "sodium") : id.isEmpty() ? Set.of() : Set.of(id);
        String version = id.isEmpty() ? "bare" : loader.getModContainer(id).orElseThrow().getMetadata().getVersion().getFriendlyString();
        String wanted = iris ? version.split("\\+", 2)[0] :  suite.equals("platform-zoomify") ? "2.15.2" : suite.equals("pal") ? "1.1.9" : suite.equals("controller") ? "0.25.7" : "bare";
        if (!loaded.equals(expected) || !version.split("\\+", 2)[0].equals(wanted)) throw new AssertionError("Wrong optional combination: " + loaded + " / " + version);
        try (var world = ctx.worldBuilder().create()) {
            var ak = Identifier.parse("tacz:ak47");
            ctx.waitFor(mc -> mc.player != null && com.tacz.guns.api.TimelessAPI.getClientGunIndex(ak).isPresent(), 1200);
            world.getServer().runOnServer(s -> {
                var p = s.getPlayerList().getPlayers().getFirst();
                p.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
                p.setItemInHand(InteractionHand.MAIN_HAND, GunItemBuilder.create().setId(ak).build());
                p.inventoryMenu.broadcastChanges();
            });
            ctx.waitFor(mc -> mc.player.onGround() && mc.player.getMainHandItem().getItem() instanceof IGun);
            world.getClientWorld().waitForChunksRender(); ctx.waitTicks(35);
            exercise(ctx, world);
        }
        System.out.println("PORT_PLAYER: " + suite + " " + version + " in 1.21.11 intermediary client");
    }

    static void turn(ClientGameTestContext ctx, float yaw, float pitch) {
        ctx.runOnClient(mc -> { mc.player.setYRot(yaw); mc.player.setXRot(pitch); });
    }
    static void waitForServer(ClientGameTestContext ctx, TestSingleplayerContext world, Predicate<MinecraftServer> predicate) {
        for (int i = 0; i < 200; i++) {
            if (world.getServer().computeOnServer(predicate::test)) return;
            ctx.waitTick();
        }
        throw new AssertionError("Authoritative inventory did not settle");
    }
}
