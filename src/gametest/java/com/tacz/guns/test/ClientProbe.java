package com.tacz.guns.test;

import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;

final class ClientProbe {
    static BufferedImage read(Path path) {
        try { return ImageIO.read(path.toFile()); } catch (IOException ex) { throw new UncheckedIOException(ex); }
    }
    static void waitForShift(ClientGameTestContext client, TestSingleplayerContext world, boolean pressed) {
        for (int i = 0; i < 100; i++) {
            if (world.getServer().computeOnServer(s -> s.getPlayerList().getPlayers().getFirst().isShiftKeyDown()) == pressed) return;
            client.waitTick();
        }
        throw new AssertionError("Server did not receive crouch=" + pressed);
    }
    // Never pass the integrated ServerPlayer/Level to the client thread.
    static void waitForServer(ClientGameTestContext client, TestSingleplayerContext world,
                              java.util.function.Predicate<net.minecraft.server.MinecraftServer> predicate) {
        for (int i = 0; i < 200; i++) {
            if (world.getServer().computeOnServer(server -> predicate.test(server))) return;
            client.waitTick();
        }
        throw new AssertionError("Timed out waiting for authoritative server state");
    }
    private ClientProbe() {}
}
