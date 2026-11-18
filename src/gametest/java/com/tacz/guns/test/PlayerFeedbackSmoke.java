package com.tacz.guns.test;

import com.tacz.guns.GunMod;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.builder.GunItemBuilder;
import com.tacz.guns.config.client.RenderConfig;
import com.tacz.guns.config.sync.SyncConfig;
import com.tacz.guns.network.message.ServerMessageBulletHitBlock;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.client.CameraType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.world.InteractionHand;
import java.util.concurrent.atomic.AtomicInteger;

/** Rendering observers do not replace meshes or inputs. Packet tests exercise the broadcast and
 * real connection, not a simulated second player or actual long-distance projectile trajectory. */
public final class PlayerFeedbackSmoke {
    public static final AtomicInteger impactPackets = new AtomicInteger();
    public static final AtomicInteger incendiaryPackets = new AtomicInteger();
    private static int offhand,hotbar,tilts;
    private static boolean observing;
    public static void backSubmit(int salt) { if(observing) { if(salt==100)offhand++; else hotbar++; } }
    public static void tilt(float angle,float direction,double multiplier,float result) {
        if (!observing || Math.abs(angle) < .0001f) return;
        float expected=angle*(float)multiplier*(1-.5f*Math.abs((float)Math.sin(Math.toRadians(direction))));
        if(Math.abs(expected-result)>.0001f)throw new AssertionError("Held-gun tilt differs from configured directional rule");
        tilts++;
    }
    static void run(ClientGameTestContext ctx,TestSingleplayerContext world) {
        var camera=ctx.computeOnClient(mc->mc.options.getCameraType());
        boolean hidden=ctx.computeOnClient(mc->RenderConfig.DISABLE_THIRD_PERSON_OFFHAND_AND_HOTBAR_GUN_RENDER.get());
        double multiplier=ctx.computeOnClient(mc->RenderConfig.GUN_HURT_BOB_MULTIPLIER.get());
        try {
            world.getServer().runOnServer(s->{
                var p=world.getConnection().getServerPlayer();
                p.getInventory().setSelectedSlot(1);
                p.setItemInHand(InteractionHand.MAIN_HAND,GunItemBuilder.create().setId(Identifier.parse("tacz:ak47")).build());
                p.setItemInHand(InteractionHand.OFF_HAND,p.getMainHandItem().copy());
                p.getInventory().setItem(0,p.getMainHandItem().copy());
            });
            ctx.waitFor(mc->mc.player.getMainHandItem().getItem() instanceof IGun && mc.player.getOffhandItem().getItem() instanceof IGun);
            ctx.runOnClient(mc->{ mc.player.getInventory().setSelectedSlot(1); observing=true;offhand=hotbar=tilts=0;mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);RenderConfig.DISABLE_THIRD_PERSON_OFFHAND_AND_HOTBAR_GUN_RENDER.set(false); });
            ctx.waitFor(mc->offhand>0&&hotbar>0);
            ctx.runOnClient(mc->{RenderConfig.DISABLE_THIRD_PERSON_OFFHAND_AND_HOTBAR_GUN_RENDER.set(true);offhand=hotbar=0;});
            ctx.waitTicks(5);
            ctx.runOnClient(mc->{if(offhand!=0||hotbar!=0)throw new AssertionError("Hidden back-mounted gun still submitted");mc.options.setCameraType(CameraType.FIRST_PERSON);});
            for(double configured : new double[]{0D,.37D,1D}) {
                ctx.waitTicks(21); // ordinary damage immunity window, no private timer edits
                ctx.runOnClient(mc->{RenderConfig.GUN_HURT_BOB_MULTIPLIER.set(configured);tilts=0;});
                world.getServer().runOnServer(s->{var p=world.getConnection().getServerPlayer();
                    if(!p.hurtServer(p.level(),p.damageSources().generic(),1))throw new AssertionError("Ordinary damage fixture was not accepted");});
                ctx.waitFor(mc->tilts>0); // observer ignores zero-angle frames; must see real hurt
            }
            checkImpactPackets(ctx,world);
            GunMod.LOGGER.info("PORT_FEEDBACK: back-mounted toggle, actual ordinary-hurt render hook and same-level impact delivery controls passed");
        } finally {
            ctx.runOnClient(mc->{observing=false;mc.options.setCameraType(camera);RenderConfig.DISABLE_THIRD_PERSON_OFFHAND_AND_HOTBAR_GUN_RENDER.set(hidden);RenderConfig.GUN_HURT_BOB_MULTIPLIER.set(multiplier);});
            world.getServer().runOnServer(s->{var p=world.getConnection().getServerPlayer();p.setItemInHand(InteractionHand.OFF_HAND,net.minecraft.world.item.ItemStack.EMPTY);p.getInventory().setItem(0,net.minecraft.world.item.ItemStack.EMPTY);});
        }
    }
    private static void checkImpactPackets(ClientGameTestContext ctx,TestSingleplayerContext world) {
        world.getServer().waitFor(s->ServerPlayNetworking.canSend(world.getConnection().getServerPlayer(),ServerMessageBulletHitBlock.TYPE));
        int saved=world.getServer().computeOnServer(s->SyncConfig.BULLET_HIT_BLOCK_EFFECT_SEND_DISTANCE.get());
        try {
            for(int scenario=0;scenario<5;scenario++) {
                int mode=scenario;
                int before=impactPackets.get(), hot=incendiaryPackets.get();
                world.getServer().runOnServer(s->{
                    var p=world.getConnection().getServerPlayer();
                    SyncConfig.BULLET_HIT_BLOCK_EFFECT_SEND_DISTANCE.set(mode==3?0:8);
                    var hit=p.position().add(mode==2 || mode==4?4:20,0,0);
                    new ServerMessageBulletHitBlock(hit,Direction.UP,BlockPos.containing(hit),"tacz:762x39","tacz:ak47","tacz:ak47").broadcast(p.level(),mode==0?p:null,mode==4);
                });
                if(mode!=1)ctx.waitFor(mc->impactPackets.get()>before);
                ctx.waitTicks(3);
                if(incendiaryPackets.get()!=hot+(mode==4?1:0))throw new AssertionError("Wrong fragment flag for "+mode);
                if(impactPackets.get()!=before+(mode==1?0:1))throw new AssertionError("Impact range control "+mode+" received wrong count");
            }
        } finally { world.getServer().runOnServer(s->SyncConfig.BULLET_HIT_BLOCK_EFFECT_SEND_DISTANCE.set(saved)); }
    }
}
