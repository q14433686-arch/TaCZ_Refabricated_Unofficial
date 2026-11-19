package com.tacz.guns.test;

import com.google.gson.Gson;
import com.tacz.guns.GunMod;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.config.client.RenderConfig;
import com.tacz.guns.config.common.AmmoConfig;
import com.tacz.guns.entity.EntityKineticBullet;
import com.tacz.guns.particles.BulletHoleOption;
import com.tacz.guns.resource.pojo.data.gun.BulletData;
import net.fabricmc.fabric.api.client.gametest.v1.context.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.*;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/** An actual server-ticked projectile collides with stone. Count successful client fragments
 * only within its decal provider so independent server lava, sound and particles cannot pass this test. */
public final class SurfaceFragmentSmoke {
    private static BlockPos target;
    private static boolean inside, hot;
    private static int blocks, lava, impacts, decals;
    public static void begin(ParticleOptions options) {
        if (target != null && options instanceof BulletHoleOption hit && target.equals(hit.getPos())) {
            inside = true;
            hot = hit.isIncendiary();
        }
    }
    public static void created(ParticleOptions options, boolean created) {
        if (!inside) return;
        if (options instanceof BulletHoleOption) { impacts++; if (created) decals++; inside = false; }
        else if (created && options.getType() == ParticleTypes.BLOCK) blocks++;
        else if (created && options.getType() == ParticleTypes.LAVA) lava++;
    }
    static void run(ClientGameTestContext ctx, TestSingleplayerContext world) {
        var pos = world.getServer().computeOnServer(s -> world.getConnection().getServerPlayer().blockPosition().north(3).above(3));
        boolean enabled = ctx.computeOnClient(mc -> RenderConfig.HIT_PARTICLE_ENABLE.get());
        boolean fire = world.getServer().computeOnServer(s -> AmmoConfig.IGNITE_BLOCK.get());
        var previous = world.getServer().computeOnServer(s -> world.getConnection().getServerPlayer().level().getBlockState(pos));
        world.getServer().runOnServer(s -> {
            AmmoConfig.IGNITE_BLOCK.set(false);
            world.getConnection().getServerPlayer().level().setBlock(pos, Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
        });
        try {
            ctx.waitFor(mc -> mc.level.getBlockState(pos).is(Blocks.STONE));
            for (boolean on : new boolean[]{true,false}) for (boolean incendiary : new boolean[]{true,false}) for (int n : new int[]{0,5}) {
                ctx.runOnClient(mc -> { target = pos; impacts = decals = lava = blocks = 0; RenderConfig.HIT_PARTICLE_ENABLE.set(on); });
                world.getServer().runOnServer(s -> {
                    var p = world.getConnection().getServerPlayer();
                    var gun = Identifier.parse("tacz:ak47");
                    var data = TimelessAPI.getCommonGunIndex(gun).orElseThrow().getGunData();
                    // Deliberately opposite: a server block spark must not be interpreted as hot surface fragments.
                    var config = new Gson().fromJson("{\"ignite\":{\"entity\":"+incendiary+",\"block\":"+!incendiary+"}}", BulletData.class);
                    var entity = new EntityKineticBullet(p.level(),p,p.getMainHandItem(),Identifier.parse("port_test:fragments_"+n),
                            gun,Identifier.parse("port_test:quiet_impact"), false,data,config);
                    entity.setPos(Vec3.atCenterOf(pos).add(0,1.1,0)); entity.setDeltaMovement(0,-1.5,0);
                    if (!p.level().addFreshEntity(entity)) throw new AssertionError("Couldn't spawn impact probe");
                });
                ctx.waitFor(mc -> impacts > 0); ctx.waitTicks(2);
                ctx.runOnClient(mc -> {
                    if (impacts != 1 || decals != 0 || hot != incendiary || lava != (on && hot ? n : 0) || blocks != (on && !hot ? n : 0))
                        throw new AssertionError("on="+on+", hot="+incendiary+", n="+n+": impacts/decals/blocks/lava="+impacts+"/"+decals+"/"+blocks+"/"+lava);
                });
            }
            GunMod.LOGGER.info("PORT_FRAGMENTS: eight authoritative stone collisions; independent ignite bits, gate and 0/5 counts matched on client");
        } finally {
            ctx.runOnClient(mc -> { target=null; inside=false; RenderConfig.HIT_PARTICLE_ENABLE.set(enabled); });
            world.getServer().runOnServer(s -> { AmmoConfig.IGNITE_BLOCK.set(fire); world.getConnection().getServerPlayer().level().setBlock(pos,previous,Block.UPDATE_ALL); });
        }
    }
}
