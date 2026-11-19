package com.tacz.guns.test;

import cn.sh1rocu.simplebedrockmodel.api.event.RenderTickEvent;
import cn.sh1rocu.tacz.api.LogicalSide;
import com.tacz.guns.GunMod;
import com.tacz.guns.api.event.common.GunFirePostEvent;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.attachment.AttachmentType;
import com.tacz.guns.api.item.builder.GunItemBuilder;
import com.tacz.guns.client.particle.NativeMuzzleEffects;
import com.tacz.guns.client.render.light.SurfaceLights;
import com.tacz.guns.config.client.RenderConfig;
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
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.animal.cow.Cow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.awt.image.BufferedImage;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.UUID;

/** Fixed receivers and a wall between the camera and the negative control. No gun, particles,
 * tracers or visible actors in the scatter A/B. Separately verify a real held beam and actual
 * server -> client detonation packets. Never mix packet generation or screenshot readback into timing. */
public final class SurfaceLightSmoke {
    public static long sampleTime = -1;
    private static final Identifier AK=id("ak47"), RPG=id("rpg7"), M4=id("m4a1"), NGAL=id("laser_ngal");
    private static Identifier id(String path) { return Identifier.fromNamespaceAndPath("tacz",path); }
    private static final Cow[] actors = new Cow[16];
    private static ItemStack rifle;
    private static boolean registered, replay;
    private static long previousTick;
    private static long[] samples;
    private static int n;

    static void run(ClientGameTestContext ctx, TestSingleplayerContext world) {
        var opt = ctx.computeOnClient(mc -> new boolean[]{RenderConfig.LASER_IMPACT_SPOT.get(),RenderConfig.MUZZLE_SURFACE_LIGHT.get(),
                RenderConfig.LEGACY_MUZZLE_LIGHT.get(),RenderConfig.EXPLOSION_SURFACE_LIGHT.get()});
        var saved = world.getServer().computeOnServer(s -> world.getConnection().getServerPlayer().getMainHandItem().copy());
        var facing = ctx.computeOnClient(mc -> new float[]{mc.player.getYRot(), mc.player.getXRot()});
        BlockPos origin=ctx.computeOnClient(mc->BlockPos.containing(mc.player.getEyePosition()));
        var old=world.getServer().computeOnServer(s -> {
            var p=world.getConnection().getServerPlayer(); var level=p.level(); p.setItemInHand(InteractionHand.MAIN_HAND,ItemStack.EMPTY);
            var states=new LinkedHashMap<BlockPos,BlockState>();
            for(int x=-4;x<=4;x++) for(int y=-2;y<=2;y++) for(int z=-3;z<=3;z++) {
                var pos=origin.offset(x,y,z);
                var block = Math.abs(x)==4 || Math.abs(y)==2 || Math.abs(z)==3 ? Blocks.COAL_BLOCK : Blocks.AIR;
                states.put(pos,level.getBlockState(pos)); level.setBlock(pos,block.defaultBlockState(),Block.UPDATE_ALL);
            }
            return states;
        });
        StringBuilder failures = new StringBuilder();
        try {
            ctx.getInput().lookAt(0,0);
            ctx.waitFor(mc->mc.player.getMainHandItem().isEmpty() && mc.level.getBlockState(origin.south(3)).is(Blocks.COAL_BLOCK));
            world.getConnection().waitForChunksRender(); ctx.waitTicks(22);
            ctx.runOnClient(mc->{
                setup(mc,origin); RenderConfig.MUZZLE_SURFACE_LIGHT.set(false); RenderConfig.LEGACY_MUZZLE_LIGHT.set(true);
                RenderConfig.EXPLOSION_SURFACE_LIGHT.set(true); RenderConfig.LASER_IMPACT_SPOT.set(false);
                // RPG7 really omits muzzle_flash and has no locator. A launch is NOT an explosion notification.
                var launcher=GunItemBuilder.create().setId(RPG).build();
                ReimaginedShotControl.taczTest$shot(new GunFirePostEvent(mc.player,launcher,LogicalSide.CLIENT));
                if (NativeMuzzleEffects.flashLight(launcher,0)!=10<<4 || NativeMuzzleEffects.flashLight(rifle,0)!=0) throw new AssertionError("Locatorless RPG exposure / different gun");
                sampleShot();
            });
            ctx.waitTicks(3);
            var off=frame(ctx,"off");
            ctx.runOnClient(mc->{RenderConfig.MUZZLE_SURFACE_LIGHT.set(true);sampleShot();}); ctx.waitTicks(4);
            int lit=redDelta(off,frame(ctx,"front"));
            var front=ctx.computeOnClient(mc->SurfaceLights.diagnostics());
            ctx.runOnClient(mc->{SurfaceLights.clear();position(mc,origin,true);sampleShot();}); ctx.waitTicks(4);
            var rear=frame(ctx,"behind");
            int leak=redDelta(off,rear);
            GunMod.LOGGER.info("PORT_SURFACE: central wall red delta front/behind over off={}/{}, front {}",lit,leak,front);
            if (lit<8 || Math.abs(leak)>Math.max(4,lit/5)) failures.append(" wall front/behind=").append(lit).append("/").append(leak);
            ctx.runOnClient(mc->sampleTime=-1);
            ctx.waitFor(mc->SurfaceLights.diagnostics().active()==0,80); // Release the clock; a single pulse really expires.
            ctx.runOnClient(mc->{SurfaceLights.clear();position(mc,origin,false);previousTick=-1;replay=true;});
            timing(ctx,false); timing(ctx,true);
            ctx.runOnClient(mc->{replay=false;SurfaceLights.clear();});
            try { packets(ctx,world); } catch(AssertionError e) { failures.append(" packets: ").append(e.getMessage()).append(" ").append(ctx.computeOnClient(mc->SurfaceLights.diagnostics())); }
            try { laser(ctx,world); } catch(AssertionError e) { failures.append(" laser: ").append(e.getMessage()); }
            if (!failures.isEmpty()) throw new AssertionError("Near-field controls:"+failures);
        } finally {
            ctx.runOnClient(mc->{ sampleTime=-1;samples=null;replay=false; Arrays.fill(actors,null);rifle=null;NativeMuzzleEffects.clear();
                RenderConfig.LASER_IMPACT_SPOT.set(opt[0]);RenderConfig.MUZZLE_SURFACE_LIGHT.set(opt[1]);
                RenderConfig.LEGACY_MUZZLE_LIGHT.set(opt[2]);RenderConfig.EXPLOSION_SURFACE_LIGHT.set(opt[3]); });
            world.getServer().runOnServer(s->{var p=world.getConnection().getServerPlayer(); old.forEach((pos,state)->p.level().setBlock(pos,state,Block.UPDATE_ALL));p.setItemInHand(InteractionHand.MAIN_HAND,saved);p.inventoryMenu.broadcastChanges();});
            ctx.getInput().lookAt(facing[0],facing[1]);
            world.getConnection().waitForChunksRender();ctx.waitTicks(5);
        }
    }
    private static void sampleShot() {
        ReimaginedShotControl.taczTest$shot(new GunFirePostEvent(actors[0],rifle,LogicalSide.CLIENT));
        sampleTime=com.tacz.guns.util.time.GunTime.nowMillis();
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
        RenderTickEvent.EVENT.register(e->{
            if (!replay || e.phase!=RenderTickEvent.Phase.START) return;
            long tick=mc.level.getGameTime();
            if (previousTick!=tick) {
                previousTick=tick;
                for(int i=(int)(tick&1);i<actors.length;i+=2)
                    ReimaginedShotControl.taczTest$shot(new GunFirePostEvent(actors[i],rifle,LogicalSide.CLIENT));
            }
            if (samples!=null && n<samples.length) samples[n++]=mc.getFrameTimeNs();
        });
    }
    private static void position(Minecraft mc, BlockPos origin, boolean behind) {
        var eye=mc.player.getEyePosition();
        for(int i=0;i<actors.length;i++) {
            var cow=actors[i];
            cow.snapTo(new Vec3(eye.x+(i==0?0:(i%4-1.5)*.32),eye.y-cow.getEyeHeight()+.12,
                    origin.getZ()+(behind?4.25:1.65)+(i/4)*.05),0,0);
        }
    }
    private static BufferedImage frame(ClientGameTestContext c,String name) { return SnowstormMaterialSmoke.read(c.takeScreenshot("tacz_surface_"+name)); }
    private static int redDelta(BufferedImage a,BufferedImage b) {
        int cx=a.getWidth()/2,cy=a.getHeight()/2,sum=0,n=0;
        for (int x=-25;x<25;x++) for(int y=-25;y<25;y++) if(Math.abs(x)>8||Math.abs(y)>8) {
            sum+=(b.getRGB(cx+x,cy+y)>>16&255)-(a.getRGB(cx+x,cy+y)>>16&255);n++;
        }
        return sum/n;
    }
    private static void timing(ClientGameTestContext ctx, boolean enable) {
        ctx.runOnClient(mc->RenderConfig.MUZZLE_SURFACE_LIGHT.set(enable));ctx.waitTicks(10);
        var before=ctx.computeOnClient(mc->SurfaceLights.diagnostics());
        ctx.runOnClient(mc->{n=0;samples=new long[64];});ctx.waitFor(mc->n==samples.length);
        ctx.runOnClient(mc->{var t=samples;samples=null;Arrays.sort(t);var d=SurfaceLights.diagnostics();
            if(d.active()>4 || (d.projections()>before.projections())!=enable) throw new AssertionError("Pulse budget/control: "+d);
            GunMod.LOGGER.info("PORT_SURFACE: 16 sources/600 RPM, spill={}, 64 renderFrame samples median/p95/p99={} / {} / {} us, admitted projections={}",enable,t[32]/1000,t[60]/1000,t[63]/1000,d.projections()-before.projections());});
    }
    private static void packets(ClientGameTestContext ctx, TestSingleplayerContext world) {
        var before=ctx.computeOnClient(mc->SurfaceLights.diagnostics());
        UUID stun=world.getServer().computeOnServer(s->{var p=world.getConnection().getServerPlayer();var level=p.level();var pos=p.getEyePosition().add(12,0,0);
            com.tacz.guns.util.ExplodeUtil.createExplosion(p,p,0,2,false,false,pos); // Same operation as an explosive bullet on hit.
            var frag=new GrenadeEntity(p,level,200);frag.setPos(pos);frag.setDamage(0);frag.setRadius(1);level.addFreshEntity(frag);frag.onDeath(null);
            // A vanilla FLASH packet with the same count and no LR stun source must not light the scene.
            ParticleUtil.sendParticle(level, ColorParticleOption.create(ParticleTypes.FLASH,0xffffff),pos.x,pos.y,pos.z,12,.2,.2,.2,8,true);
            var g=new StunGrenadeEntity(p,level,200);g.setPos(pos.add(4,0,0));g.setThrowableGravity(0);level.addFreshEntity(g);return g.getUUID();});
        ctx.waitFor(mc->mc.level.getEntity(stun)!=null && SurfaceLights.diagnostics().explosions()==before.explosions()+2,100);
        if(ctx.computeOnClient(mc->SurfaceLights.diagnostics().stuns())!=before.stuns()) throw new AssertionError("Frag sprites/unrelated FLASH misclassified as a stun");
        world.getServer().runOnServer(s->((StunGrenadeEntity)world.getConnection().getServerPlayer().level().getEntity(stun)).onDeath(null));
        ctx.waitFor(mc->SurfaceLights.diagnostics().stuns()==before.stuns()+1,100);
        GunMod.LOGGER.info("PORT_SURFACE: server TaCZ explosion + LR frag -> 2 blasts, LR stun -> 1 white pulse, 0 duplicate sprite bursts");
    }
    private static void laser(ClientGameTestContext ctx,TestSingleplayerContext world) {
        world.getServer().runOnServer(s->{var p=world.getConnection().getServerPlayer();p.setItemInHand(InteractionHand.MAIN_HAND,GunItemBuilder.create().setId(M4).setAmmoCount(5).putAttachment(AttachmentType.LASER,NGAL).build());p.inventoryMenu.broadcastChanges();});
        ctx.waitFor(mc->{var stack=mc.player.getMainHandItem();return stack.getItem() instanceof IGun gun && NGAL.equals(gun.getAttachmentId(stack,AttachmentType.LASER));});
        ctx.waitTicks(26);var off=frame(ctx,"laser_off");ctx.runOnClient(mc->RenderConfig.LASER_IMPACT_SPOT.set(true));
        try { ctx.waitFor(mc->SurfaceLights.diagnostics().beamHits()>0,100); }
        catch(AssertionError e) { throw new AssertionError("Held NGAL did not project: "+ctx.computeOnClient(mc->SurfaceLights.diagnostics()),e); }ctx.waitTicks(4);var on=frame(ctx,"laser_on");
        int added=0;
        for(int y=on.getHeight()/5;y<on.getHeight()*3/4;y++) for(int x=on.getWidth()/5;x<on.getWidth()*4/5;x++) {
            int a=off.getRGB(x,y),b=on.getRGB(x,y);
            int ca=Math.min(a>>8&255,a&255)-(a>>16&255), cb=Math.min(b>>8&255,b&255)-(b>>16&255);
            if(cb>30 && cb-ca>25) added++;
        }
        if (added<6) throw new AssertionError("No colored NGAL impact in wall frame: "+added);
        GunMod.LOGGER.info("PORT_SURFACE: held NGAL cyan wall spot: {} added cyan pixels with the beam present in both frames",added);
    }
    private SurfaceLightSmoke() {}
}
