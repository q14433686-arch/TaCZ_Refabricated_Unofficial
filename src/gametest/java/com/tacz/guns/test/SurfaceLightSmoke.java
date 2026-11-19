package com.tacz.guns.test;

import cn.sh1rocu.simplebedrockmodel.api.event.RenderTickEvent;
import cn.sh1rocu.tacz.api.LogicalSide;
import com.tacz.guns.GunMod;
import com.tacz.guns.api.entity.IGunOperator;
import com.tacz.guns.api.event.common.GunFirePostEvent;
import com.tacz.guns.api.event.server.AmmoHitBlockEvent;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.client.gameplay.IClientPlayerGunOperator;
import com.tacz.guns.client.input.ShootKey;
import com.tacz.guns.test.SurfaceLightFrames.Frame;
import com.tacz.guns.test.SurfaceLightFrames.Trigger;
import com.tacz.guns.test.SurfaceLightFrames.Region;
import com.tacz.guns.api.item.builder.GunItemBuilder;
import com.tacz.guns.api.item.gun.FireMode;
import com.tacz.guns.client.particle.NativeMuzzleEffects;
import com.tacz.guns.client.render.light.SurfaceLights;
import com.tacz.guns.config.client.RenderConfig;
import com.tacz.guns.config.common.AmmoConfig;
import com.tacz.guns.test.mixin.ReimaginedShotControl;
import me.xjqsh.lrtactical.entity.GrenadeEntity;
import me.xjqsh.lrtactical.entity.StunGrenadeEntity;
import me.xjqsh.lrtactical.util.ParticleUtil;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ColorParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.animal.cow.Cow;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.UUID;

/** Same dark lane, camera and actual stock gun in the shot controls. Real-time post-render
 * readback is separate from cost measurements. No projection, pulse or particle is injected
 * for the positive pixel tests. Synthetic shooters are used only for occlusion/load controls. */
public final class SurfaceLightSmoke {
    private static final Identifier AK=id("ak47"), RPG=id("rpg7");
    private static final int PARTITION=5, BACK=30, SIDE=8;
    private static int localShots;
    private static Identifier id(String path) { return Identifier.fromNamespaceAndPath("tacz",path); }
    private static final Cow[] actors = new Cow[16];
    private static ItemStack rifle;
    private static boolean registered, replay;
    private static volatile UUID watchedShooter;
    private static volatile Vec3 serverHit, clientBlast;
    public static void observeExplosion(Vec3 center) {
        if (watchedShooter != null) clientBlast=center;
        SurfaceLightFrames.trigger(Trigger.EXPLOSION);
    }
    private static long previousTick;
    private static long[] samples, submitSamples;
    private static long collectStart, collectNs;
    public static void beginCollect() { if (samples!=null) collectStart=System.nanoTime(); }
    public static void finishTiming() { if (samples!=null) collectNs+=System.nanoTime()-collectStart; }
    public static void endCollect() { finishTiming(); SurfaceLightFrames.afterCollection(); }
    private static int n;

    static void run(ClientGameTestContext ctx, TestSingleplayerContext world, boolean measureLoad) {
        var opt = ctx.computeOnClient(mc -> new boolean[]{RenderConfig.MUZZLE_SURFACE_LIGHT.get(),RenderConfig.LEGACY_MUZZLE_LIGHT.get(),
                RenderConfig.EXPLOSION_SURFACE_LIGHT.get(),mc.options.bobView().get()});
        var chat=ctx.computeOnClient(mc->mc.options.chatVisibility().get());
        var saved = world.getServer().computeOnServer(s -> world.getConnection().getServerPlayer().getMainHandItem().copy());
        var facing = ctx.computeOnClient(mc -> new float[]{mc.player.getYRot(),mc.player.getXRot()});
        BlockPos origin=ctx.computeOnClient(mc->BlockPos.containing(mc.player.getEyePosition()));
        var concrete=Blocks.CONCRETE.pick(DyeColor.GRAY);
        var old=world.getServer().computeOnServer(s -> {
            var p=world.getConnection().getServerPlayer(); var level=p.level(); p.setItemInHand(InteractionHand.MAIN_HAND,ItemStack.EMPTY);
            var states=new LinkedHashMap<BlockPos,BlockState>();
            for(int x=-SIDE;x<=SIDE;x++) for(int y=-2;y<=5;y++) for(int z=-3;z<=BACK;z++) {
                var pos=origin.offset(x,y,z);
                var block = Math.abs(x)==SIDE || y==-2 || y==5 || z==-3 || z==BACK || z==PARTITION ? concrete : Blocks.AIR;
                var prev=level.getBlockState(pos);
                if (prev.is(block) && z!=PARTITION) continue;
                states.put(pos,prev); level.setBlock(pos,block.defaultBlockState(),Block.UPDATE_ALL);
            }
            return states;
        });
        try {
            ctx.getInput().lookAt(0,0);
            ctx.waitFor(mc->mc.player.getMainHandItem().isEmpty() && mc.level.getBlockState(origin.south(BACK)).is(concrete));
            world.getConnection().waitForChunksRender(); ctx.waitTicks(22);
            ctx.runOnClient(mc->{
                setup(mc,origin); mc.options.bobView().set(false);
                // The real first shot grants an advancement; its fading chat banner crosses the floor ROI.
                mc.options.chatVisibility().set(net.minecraft.world.entity.player.ChatVisiblity.HIDDEN); RenderConfig.MUZZLE_SURFACE_LIGHT.set(false);
                RenderConfig.LEGACY_MUZZLE_LIGHT.set(true); RenderConfig.EXPLOSION_SURFACE_LIGHT.set(true);
                // Locatorless RPG exposure belongs to the held gun, not to its eventual explosion.
                var launcher=GunItemBuilder.create().setId(RPG).build();
                ReimaginedShotControl.taczTest$shot(new GunFirePostEvent(mc.player,launcher,LogicalSide.CLIENT));
                if (NativeMuzzleEffects.flashLight(launcher,0)!=10<<4 || NativeMuzzleEffects.flashLight(rifle,0)!=0) throw new AssertionError("Locatorless RPG exposure / different gun");
            });
            muzzleFrames(ctx,world,origin);
            if (measureLoad) {
                ctx.runOnClient(mc->{overlapControl(origin);SurfaceLights.clear();position(mc,origin,false);previousTick=-1;replay=true;});
                for (boolean enable : new boolean[]{false,true,true,false}) timing(ctx,enable,"PORT_SURFACE");
            }
            ctx.runOnClient(mc->{replay=false;SurfaceLights.clear();RenderConfig.MUZZLE_SURFACE_LIGHT.set(true);});
            world.getServer().runOnServer(s->{var level=world.getConnection().getServerPlayer().level();
                for (int x=1-SIDE;x<SIDE;x++) for (int y=-1;y<5;y++) level.removeBlock(origin.offset(x,y,PARTITION),false);
            });
            ctx.waitFor(mc->mc.level.getBlockState(origin.south(PARTITION)).isAir());
            world.getConnection().waitForChunksRender(); ctx.waitTicks(12);
            blastFrames(ctx,world,origin);
            ObjectLightSmoke.run(ctx, world, origin, measureLoad);
            packets(ctx,world);
        } finally {
            ctx.runOnClient(mc->{ SurfaceLightFrames.clear();samples=submitSamples=null;replay=false;Arrays.fill(actors,null);rifle=null;NativeMuzzleEffects.clear();
                RenderConfig.MUZZLE_SURFACE_LIGHT.set(opt[0]);RenderConfig.LEGACY_MUZZLE_LIGHT.set(opt[1]);
                RenderConfig.EXPLOSION_SURFACE_LIGHT.set(opt[2]);mc.options.bobView().set(opt[3]);mc.options.chatVisibility().set(chat); });
            world.getServer().runOnServer(s->{var p=world.getConnection().getServerPlayer();old.forEach((pos,state)->p.level().setBlock(pos,state,Block.UPDATE_ALL));p.setItemInHand(InteractionHand.MAIN_HAND,saved);p.inventoryMenu.broadcastChanges();});
            ctx.getInput().lookAt(facing[0],facing[1]);
            world.getConnection().waitForChunksRender();ctx.waitTicks(5);
        }
    }

    private static void muzzleFrames(ClientGameTestContext ctx,TestSingleplayerContext world,BlockPos origin) {
        world.getServer().runOnServer(s->{var p=world.getConnection().getServerPlayer();
            p.setItemInHand(InteractionHand.MAIN_HAND,GunItemBuilder.create().setId(AK).setAmmoCount(8).setAmmoInBarrel(true).setFireMode(FireMode.SEMI).build());
            p.inventoryMenu.broadcastChanges();});
        ctx.waitFor(mc->mc.player.getMainHandItem().getItem() instanceof IGun); ctx.waitTicks(28);
        ctx.waitFor(mc->IGunOperator.fromLivingEntity(mc.player).getSynDrawCoolDown()==0 && IClientPlayerGunOperator.fromLocalPlayer(mc.player).getDataHolder().clientBaseTimestamp>=0);
        var eye=ctx.computeOnClient(mc->mc.player.getEyePosition());
        double wall=origin.getZ()+PARTITION;
        Region[] points={Region.wall(new Vec3(eye.x+1.8,eye.y+.75,wall)),Region.wall(new Vec3(eye.x-1.8,eye.y+.75,wall)),
                Region.floor(new Vec3(eye.x+1.35,origin.getY()-1,wall-.95))};
        var rest=SurfaceLightFrames.still(ctx,"muzzle_rest");
        var off=fireLocal(ctx,world,false,"ak"); var on=fireLocal(ctx,world,true,"ak");
        ctx.waitFor(mc->SurfaceLights.diagnostics().active()==0); ctx.waitTicks(20); ctx.getInput().lookAt(0,0);ctx.waitTicks(4);
        var expired=SurfaceLightFrames.still(ctx,"muzzle_expired");
        var spread=on.deltas(off,points,.16); var residue=expired.deltas(rest,points,.16);
        world.getServer().runOnServer(s->{var p=world.getConnection().getServerPlayer();p.setItemInHand(InteractionHand.MAIN_HAND,ItemStack.EMPTY);p.inventoryMenu.broadcastChanges();});
        ctx.waitFor(mc->mc.player.getMainHandItem().isEmpty());ctx.waitTicks(15);
        var empty=SurfaceLightFrames.still(ctx,"empty");
        ctx.runOnClient(mc->{SurfaceLights.clear();position(mc,origin,true);SurfaceLightFrames.arm("muzzle_behind",Trigger.STILL);
            ReimaginedShotControl.taczTest$shot(new GunFirePostEvent(actors[0],rifle,LogicalSide.CLIENT));});
        var leak=SurfaceLightFrames.read(ctx).deltas(empty,points,.16);
        GunMod.LOGGER.info("PORT_SURFACE: live AK wall left/right + forward floor mean red ON-OFF={}, expired={}, behind wall={}",Arrays.toString(spread),Arrays.toString(residue),Arrays.toString(leak));
        checkSamples("live AK wall left/right + forward floor",spread,10,residue,leak);
    }
    static Frame fireLocal(ClientGameTestContext ctx,TestSingleplayerContext world,boolean enabled,String label) {
        ctx.getInput().lookAt(0,0);ctx.waitTicks(12);
        ctx.runOnClient(mc->{RenderConfig.MUZZLE_SURFACE_LIGHT.set(enabled);SurfaceLightFrames.arm(label+"_"+(enabled?"on":"off"),Trigger.SHOT);localShots=0;});
        int ammo=world.getServer().computeOnServer(s->{var g=world.getConnection().getServerPlayer().getMainHandItem();return ((IGun)g.getItem()).getCurrentAmmoCount(g);});
        ctx.getInput().holdKey(ShootKey.SHOOT_KEY);
        try {ctx.waitFor(mc->localShots>0);} finally {ctx.getInput().releaseKey(ShootKey.SHOOT_KEY);}
        var frame=SurfaceLightFrames.read(ctx);
        world.getServer().waitFor(s->{var g=world.getConnection().getServerPlayer().getMainHandItem();return ((IGun)g.getItem()).getCurrentAmmoCount(g)==ammo-1;});
        return frame;
    }
    private static void checkSamples(String label,int[] lit,int required,int[]... controls) {
        for (int i=0;i<lit.length;i++) {
            if (lit[i]<required) throw new AssertionError(label+" too dim: "+Arrays.toString(lit));
            for (int[] c:controls) if (Math.abs(c[i])>6) throw new AssertionError(label+" false positive: "+Arrays.toString(c)+", lit="+Arrays.toString(lit));
        }
    }
    private static void setup(Minecraft mc, BlockPos origin) {
        rifle=GunItemBuilder.create().setId(AK).build();
        for(int i=0;i<actors.length;i++) {
            actors[i]=new Cow(EntityTypes.COW,mc.level);
            // 26.3 client-only entities have no ID until a spawn packet assigns one.
            actors[i].setId(100000000+i);
        }
        position(mc,origin,false);
        if (registered) return; registered=true;
        AmmoHitBlockEvent.CALLBACK.register(e->{
            var owner=e.getAmmo().getOwner();
            if (owner!=null && owner.getUUID().equals(watchedShooter)) serverHit=e.getHitResult().getLocation();
        });
        GunFirePostEvent.EVENT.register(e->{
            if(e.getLogicalSide().isClient() && e.getShooter()==mc.player) {localShots++; SurfaceLightFrames.trigger(Trigger.SHOT);}
        });
        RenderTickEvent.EVENT.register(e->{
            if (e.phase==RenderTickEvent.Phase.END) SurfaceLightFrames.renderEnd(mc);
            if (e.phase!=RenderTickEvent.Phase.START) return;
            if (samples!=null && n<samples.length) {
                samples[n]=mc.getFrameTimeNs();submitSamples[n++]=collectNs;collectNs=0;
            }
            if (!replay) return;
            long tick=mc.level.getGameTime();
            if (previousTick!=tick) {
                previousTick=tick;
                for(int i=(int)(tick&1);i<actors.length;i+=2)
                    ReimaginedShotControl.taczTest$shot(new GunFirePostEvent(actors[i],rifle,LogicalSide.CLIENT));
            }
        });
    }
    private static void position(Minecraft mc, BlockPos origin, boolean behind) {
        var eye=mc.player.getEyePosition();
        for(int i=0;i<actors.length;i++) {
            var cow=actors[i];
            cow.snapTo(new Vec3(eye.x+(i==0?0:(i%4-1.5)*.32),eye.y-cow.getEyeHeight()+.12,
                    origin.getZ()+(behind?PARTITION+1.5:1.0)+(i/4)*.05),0,0);
        }
    }
    private static void overlapControl(BlockPos origin) {
        SurfaceLights.clear();
        var before=SurfaceLights.diagnostics();
        for (int i=0;i<3;i++) {
            var cow=actors[i];
            cow.snapTo(Vec3.atCenterOf(origin.south(1)).add(i==2?3:i*.10,-cow.getEyeHeight()+.12,-.65),0,0);
            ReimaginedShotControl.taczTest$shot(new GunFirePostEvent(cow,rifle,LogicalSide.CLIENT));
            var d=SurfaceLights.diagnostics();
            if (d.active()!=(i==2?2:1) || d.coincident()-before.coincident()!=(i==0?0:1)) throw new AssertionError("Coincident vs separated muzzles: "+d);
        }
        GunMod.LOGGER.info("PORT_SURFACE: two aligned muzzles in one air cell share one footprint; a third 3 blocks away retains its own");
    }
    static void timeOccupiedScene(ClientGameTestContext ctx, BlockPos origin) {
        ctx.runOnClient(mc->{SurfaceLights.clear();position(mc,origin,false);previousTick=-1;replay=true;});
        try { for (boolean enabled : new boolean[]{false,true,true,false}) timing(ctx,enabled,"PORT_RECEIVER_COST"); }
        finally { ctx.runOnClient(mc->{replay=false;SurfaceLights.clear();RenderConfig.MUZZLE_SURFACE_LIGHT.set(true);}); }
    }
    private static void timing(ClientGameTestContext ctx, boolean enable, String label) {
        ctx.runOnClient(mc->RenderConfig.MUZZLE_SURFACE_LIGHT.set(enable));ctx.waitTicks(10);
        var before=ctx.computeOnClient(mc->SurfaceLights.diagnostics());
        var t=frameTimes(ctx);
        var d=ctx.computeOnClient(mc->SurfaceLights.diagnostics());
        if(d.active()>2 || (d.projections()>before.projections())!=enable) throw new AssertionError("Muzzle budget/control: "+d);
        GunMod.LOGGER.info("{}: 16 sources/600 RPM, spill={}, 64 renderFrame samples median/p95/p99={} / {} / {} us, prepare + terrain submit p95={} us, admitted projections={}, overlapping offers={}",label,enable,t.frame[32]/1000,t.frame[60]/1000,t.frame[63]/1000,t.submit[60]/1000,d.projections()-before.projections(),d.coincident()-before.coincident());
    }
    private record Timings(long[] frame,long[] submit) {}
    private static Timings frameTimes(ClientGameTestContext ctx) {
        ctx.runOnClient(mc->{n=0;collectNs=0;samples=new long[64];submitSamples=new long[64];});ctx.waitFor(mc->n==samples.length);
        return ctx.computeOnClient(mc->{var t=new Timings(samples,submitSamples);samples=submitSamples=null;
            Arrays.sort(t.frame);Arrays.sort(t.submit);return t;});
    }
    private record Impact(Frame frame, Vec3 pos) {}
    private static void blastFrames(ClientGameTestContext ctx, TestSingleplayerContext world, BlockPos origin) {
        var target=origin.south(BACK);
        var before=SurfaceLightFrames.still(ctx,"range_rest");
        var off=rocket(ctx,world,target,false);ctx.waitTicks(24);
        var on=rocket(ctx,world,target,true);
        ctx.waitFor(mc->SurfaceLights.diagnostics().active()==0); ctx.waitTicks(24);
        var early=SurfaceLightFrames.still(ctx,"blast_early_expired");
        // Inspect the flashing surface before AND after native RPG debris has dispersed.
        // Do not turn off particles, freeze the clock, or relax the control thresholds.
        ctx.waitTicks(100);
        var expired=SurfaceLightFrames.still(ctx,"blast_expired");
        // Four to five blocks from the actual hit, not the particle core or shooter. Each
        // explosion uses its own server-confirmed centre (stock RPG spread is not disabled).
        Region[] a=blastPoints(off.pos,origin), b=blastPoints(on.pos,origin);
        int[] lit=on.frame.deltas(before,b,.40), dark=off.frame.deltas(before,a,.40), residue=expired.deltas(before,b,.40);
        ctx.runOnClient(mc->SurfaceLightFrames.arm("blast_behind",Trigger.EXPLOSION));
        world.getServer().runOnServer(s->{var level=world.getConnection().getServerPlayer().level();
            level.explode(null,on.pos.x,on.pos.y,target.getZ()+1.5,3f,false,net.minecraft.world.level.Level.ExplosionInteraction.NONE);});
        var leak=SurfaceLightFrames.read(ctx).deltas(before,b,.40);
        GunMod.LOGGER.info("PORT_SURFACE: live RPG7 wall +/-5 blocks and forward floor, mean red over rest ON={}, OFF={}, expired={}, behind wall={}, observer={} blocks",Arrays.toString(lit),Arrays.toString(dark),Arrays.toString(residue),Arrays.toString(leak),on.pos.distanceTo(ctx.computeOnClient(mc->mc.player.getEyePosition())));
        // Compare the two physical hits after subtracting each piece of wall's own rest
        // colour. This also excludes any native flash/bloom a pack already gives the OFF shot.
        int[] added=lit.clone(); for (int i=0;i<added.length;i++) added[i]-=dark[i];
        int[] earlyResidual=early.medianRedDeltas(before,b,.40);
        GunMod.LOGGER.info("PORT_SURFACE: early expiry paired median={} (native particles still allowed)",Arrays.toString(earlyResidual));
        checkSamples("live RPG7 beyond particle core",added,14,earlyResidual,residue,leak);
    }
    private static Region[] blastPoints(Vec3 hit,BlockPos origin) {
        return new Region[]{Region.wall(hit.add(5,.35,0)),Region.wall(hit.add(-5,.35,0)),Region.floor(new Vec3(hit.x+3.5,origin.getY()-1,hit.z-4))};
    }
    /** Tracked stock RPG-7 shoots towards the visible backstop, not outside the camera room. */
    private static Impact rocket(ClientGameTestContext ctx, TestSingleplayerContext world, BlockPos target,boolean enabled) {
        var server=world.getServer();
        var destroy=server.computeOnServer(s->AmmoConfig.EXPLOSIVE_AMMO_DESTROYS_BLOCK.get());
        var before=ctx.computeOnClient(mc->SurfaceLights.diagnostics());
        try {
            var shooter=server.computeOnServer(s->{var level=world.getConnection().getServerPlayer().level();
                var mob=EntityTypes.HUSK.create(level,EntitySpawnReason.COMMAND);
                if(mob==null) throw new AssertionError("No tracked launcher holder");
                mob.snapTo(Vec3.atCenterOf(target).add(0,-mob.getEyeHeight(),-10),0,0);mob.setNoGravity(true);mob.setNoAi(true);
                mob.setItemInHand(InteractionHand.MAIN_HAND,GunItemBuilder.create().setId(RPG).setAmmoCount(1).setAmmoInBarrel(true).setFireMode(FireMode.SEMI).build());
                level.addFreshEntity(mob);IGunOperator.fromLivingEntity(mob).initialData();return mob;
            });
            watchedShooter=shooter.getUUID();serverHit=clientBlast=null;
            world.getConnection().waitForClientboundEntityUpdates(EntityTypes.HUSK);
            ctx.waitFor(mc->mc.level.getEntity(watchedShooter)!=null,100);
            ctx.runOnClient(mc->{RenderConfig.EXPLOSION_SURFACE_LIGHT.set(enabled);SurfaceLightFrames.arm("rpg_"+(enabled?"on":"off"),Trigger.EXPLOSION);});
            server.runOnServer(s->{AmmoConfig.EXPLOSIVE_AMMO_DESTROYS_BLOCK.set(false);
                var api=ScriptGuardSmoke.bind(shooter);api.setPitchSupplier(()->0f);api.setYawSupplier(()->0f);api.shootOnce(true);
            });
            ctx.waitFor(mc->serverHit!=null && clientBlast!=null,120);
            var frame=SurfaceLightFrames.read(ctx);
            var d=ctx.computeOnClient(mc->SurfaceLights.diagnostics());
            if (d.muzzles()!=before.muzzles()+1 || d.explosions()!=before.explosions()+(enabled?1:0) || serverHit.distanceToSqr(clientBlast)>1e-8
                    || Math.abs(serverHit.z-target.getZ())>.01 || Math.abs(serverHit.x-(target.getX()+.5))>2 || Math.abs(serverHit.y-(target.getY()+.5))>2)
                throw new AssertionError("RPG launch/impact/ON-OFF: "+before+" -> "+d+", target "+target+", "+serverHit+"/"+clientBlast);
            GunMod.LOGGER.info("PORT_SURFACE: tracked RPG7 enabled={} hit {}, position error={} blocks",enabled,clientBlast,serverHit.distanceTo(clientBlast));
            return new Impact(frame,clientBlast);
        } finally {
            server.runOnServer(s->{var level=world.getConnection().getServerPlayer().level();
                if(watchedShooter!=null && level.getEntity(watchedShooter)!=null) level.getEntity(watchedShooter).discard();
                AmmoConfig.EXPLOSIVE_AMMO_DESTROYS_BLOCK.set(destroy);});
            watchedShooter=null;serverHit=clientBlast=null;
        }
    }
    private static void packets(ClientGameTestContext ctx, TestSingleplayerContext world) {
        var before=ctx.computeOnClient(mc->SurfaceLights.diagnostics());
        UUID stun=world.getServer().computeOnServer(s->{var p=world.getConnection().getServerPlayer();var level=p.level();var pos=p.getEyePosition().add(12,0,0);
            var frag=new GrenadeEntity(p,level,200);frag.setPos(pos);frag.setDamage(0);frag.setRadius(1);level.addFreshEntity(frag);frag.onDeath(null);
            // A vanilla FLASH packet with the same count and no LR stun source must not light the scene.
            ParticleUtil.sendParticle(level, ColorParticleOption.create(ParticleTypes.FLASH,0xffffff),pos.x,pos.y,pos.z,12,.2,.2,.2,8,true);
            var g=new StunGrenadeEntity(p,level,200);g.setPos(pos.add(4,0,0));g.setThrowableGravity(0);level.addFreshEntity(g);return g.getUUID();});
        ctx.waitFor(mc->mc.level.getEntity(stun)!=null && SurfaceLights.diagnostics().explosions()==before.explosions()+1,100);
        if(ctx.computeOnClient(mc->SurfaceLights.diagnostics().stuns())!=before.stuns()) throw new AssertionError("Frag sprites/unrelated FLASH misclassified as a stun");
        world.getServer().runOnServer(s->((StunGrenadeEntity)world.getConnection().getServerPlayer().level().getEntity(stun)).onDeath(null));
        ctx.waitFor(mc->SurfaceLights.diagnostics().stuns()==before.stuns()+1,100);
        GunMod.LOGGER.info("PORT_SURFACE: server LR frag -> 1 blast, LR stun -> 1 white pulse, 0 unrelated FLASH pulses");
    }
    private SurfaceLightSmoke() {}
}
