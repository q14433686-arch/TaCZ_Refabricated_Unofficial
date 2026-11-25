package com.tacz.guns.test;

import com.tacz.guns.GunMod;
import com.tacz.guns.api.client.gameplay.IClientPlayerGunOperator;
import com.tacz.guns.client.input.AimKey;
import com.tacz.guns.client.render.scope.ScopeLensFrame;
import com.tacz.guns.client.render.scope.ScopePipRerender;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

import java.util.UUID;

/** A nonempty camera matrix alone doesn't prove the second renderLevel left the wide world
 * intact. Change an off-axis panel, move a real entity in front of it and change the sky in a
 * fixed high-altitude scene. Read only the main framebuffer, outside the lens bounding box. */
final class WideSceneSmoke {
    static void run(ClientGameTestContext ctx, TestSingleplayerContext world, BlockPos base, UUID slime) {
        var panel = base.north(20).west(15).above(2);
        var inFront = base.north(17).west(13).above();
        ctx.getInput().lookAt(180, 0);
        try {
            for (boolean aimed : new boolean[]{true, false}) {
                if (!aimed) ctx.getInput().releaseKey(AimKey.AIM_KEY);
                ctx.waitFor(mc -> aimed ? ScopePipRerender.hasScene() && ScopeLensFrame.currentBounds() != null :
                        !ScopePipRerender.hasScene() && IClientPlayerGunOperator.fromLocalPlayer(mc.player).getClientAimingProgress(1) < .001f);
                var bounds = ctx.computeOnClient(mc -> aimed ? ScopeLensFrame.currentBounds() : null);
                String mode = aimed ? "narrow" : "released";
                ShaderOpticSmoke.move(ctx, world, slime, base.north(16).east(12));
                panel(ctx, world, panel, Blocks.MAGENTA_CONCRETE);
                var pink = ShaderOpticSmoke.frame(ctx, "wide_" + mode + "_panel_a");
                panel(ctx, world, panel, Blocks.LIGHT_BLUE_CONCRETE);
                var blue = ShaderOpticSmoke.frame(ctx, "wide_" + mode + "_panel_b");
                ShaderOpticSmoke.move(ctx, world, slime, inFront);
                ctx.waitTicks(8);
                var entity = ShaderOpticSmoke.frame(ctx, "wide_" + mode + "_entity_day");
                world.getServer().runCommand("time set 18000"); ctx.waitTicks(30);
                var night = ShaderOpticSmoke.frame(ctx, "wide_" + mode + "_entity_night");
                world.getServer().runCommand("time set 6000"); ctx.waitTicks(30);
                int w = blue.getWidth(), h = blue.getHeight(), terrain = 0, body = 0, sky = 0, skyArea = 0;
                for (int y = 0; y < h; y++) for (int x = w/25; x < w/3; x++) {
                    float nx = (x+.5f)*2/w-1, ny = 1-(y+.5f)*2/h;
                    if (bounds != null && nx >= bounds[0] && nx <= bounds[2] && ny >= bounds[1] && ny <= bounds[3]) continue;
                    int a = pink.getRGB(x,y), b = blue.getRGB(x,y), c = entity.getRGB(x,y);
                    if (y < h/6) {
                        skyArea++;
                        if (luma(c) > luma(night.getRGB(x,y)) + 20) sky++;
                    } else if (y > h/3 && y < 2*h/3 && (red(a)-green(a)) - (red(b)-green(b)) > 45) {
                        terrain++; // the actual panel, not arbitrary pixels of green terrain
                        if ((green(c)-(c&255)) - (green(b)-(b&255)) > 35) body++;
                    }
                }
                GunMod.LOGGER.info("OPTIC_WIDE: {} main frame: switched panel={}, exposed slime={}, day vs night sky={}/{}", mode, terrain, body, sky, skyArea);
                if (terrain < 200 || body < 40 || skyArea < 200 || sky < skyArea/2)
                    throw new AssertionError("Missing wide scene in " + mode + ": terrain=" + terrain + ", slime=" + body + ", sky=" + sky + "/" + skyArea);
            }
        } finally {
            world.getServer().runCommand("time set 6000");
            panel(ctx, world, panel, Blocks.AIR);
        }
    }

    private static int red(int v) { return v >>> 16 & 255; }
    private static int green(int v) { return v >>> 8 & 255; }
    private static int luma(int v) { return (red(v) + 2*green(v) + (v&255))/4; }

    private static void panel(ClientGameTestContext ctx, TestSingleplayerContext world, BlockPos at, Block block) {
        world.getServer().runOnServer(s -> {
            var level = s.getPlayerList().getPlayers().getFirst().level();
            for (int x=-3; x<=3; x++) for (int y=-2; y<=2; y++) level.setBlock(at.offset(x,y,0), block.defaultBlockState(), Block.UPDATE_ALL);
        });
        ctx.waitFor(mc -> mc.level.getBlockState(at).is(block));
        world.getClientLevel().waitForChunksRender(); ctx.waitTicks(8);
    }
    private WideSceneSmoke() {}
}
