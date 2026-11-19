package com.tacz.guns.test;

import com.tacz.guns.client.particle.BulletHoleParticle;
import com.tacz.guns.config.client.RenderConfig;
import com.tacz.guns.particles.BulletHoleOption;
import net.fabricmc.fabric.api.client.gametest.v1.context.*;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.*;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

/** Count actual provider output inside a single client-thread call, excluding the separate server effect. */
public final class SurfaceFragmentSmoke {
    private static boolean observing;
    private static int blocks, lava;
    public static void created(ParticleOptions options) {
        if (!observing) return;
        if (options.getType() == ParticleTypes.BLOCK) blocks++;
        if (options.getType() == ParticleTypes.LAVA) lava++;
    }
    static void run(ClientGameTestContext ctx, TestSingleplayerContext world) {
        var pos = world.getServer().computeOnServer(s -> {
            var p = world.getConnection().getServerPlayer();
            var at = p.blockPosition().north(3).above(3);
            p.level().setBlock(at, Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
            return at;
        });
        ctx.waitFor(mc -> mc.level.getBlockState(pos).is(Blocks.STONE));
        try {
            ctx.runOnClient(mc -> {
                boolean enabled = RenderConfig.HIT_PARTICLE_ENABLE.get();
                var provider = new BulletHoleParticle.Provider();
                try {
                    for (boolean on : new boolean[]{true, false}) for (boolean hot : new boolean[]{true, false}) for (int n : new int[]{0, 5}) {
                        RenderConfig.HIT_PARTICLE_ENABLE.set(on);
                        var option = new BulletHoleOption(Direction.UP, pos, "port_test:fragments_" + n, "tacz:ak47", "port_test:quiet_impact", hot);
                        lava = blocks = 0;
                        observing = true;
                        var decal = provider.createParticle(option, mc.level, pos.getX()+.5, pos.getY()+1, pos.getZ()+.5, 0,0,0, mc.level.getRandom());
                        observing = false;
                        if (decal != null || lava != (on && hot ? n : 0) || blocks != (on && !hot ? n : 0))
                            throw new AssertionError("on=" + on + " hot=" + hot + " count=" + n + ": blocks/lava=" + blocks + "/" + lava);
                    }
                } finally { observing = false; RenderConfig.HIT_PARTICLE_ENABLE.set(enabled); }
            });
        } finally { world.getServer().runOnServer(s -> world.getConnection().getServerPlayer().level().removeBlock(pos, false)); }
    }
}
