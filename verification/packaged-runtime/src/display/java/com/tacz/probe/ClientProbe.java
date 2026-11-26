package com.tacz.probe;

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
    static void aim(ClientGameTestContext ctx, net.minecraft.core.BlockPos pos) {
        ctx.runOnClient(mc -> mc.player.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES,
                net.minecraft.world.phys.Vec3.atCenterOf(pos)));
    }
    private ClientProbe() {}
}
