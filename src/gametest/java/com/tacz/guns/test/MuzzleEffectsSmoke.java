package com.tacz.guns.test;

import com.tacz.guns.GunMod;
import com.tacz.guns.api.DefaultAssets;
import com.tacz.guns.api.client.gameplay.IClientPlayerGunOperator;
import com.tacz.guns.api.entity.IGunOperator;
import com.tacz.guns.api.event.common.GunFirePostEvent;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.attachment.AttachmentType;
import com.tacz.guns.api.item.builder.GunItemBuilder;
import com.tacz.guns.api.item.gun.FireMode;
import com.tacz.guns.client.input.ShootKey;
import com.tacz.guns.client.particle.NativeMuzzleEffects;
import com.tacz.guns.client.particle.bedrock.particle.world.WorldEmitterManager;
import com.tacz.guns.config.client.RenderConfig;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.CameraType;
import net.minecraft.resources.Identifier;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.item.ItemStack;

import java.awt.image.BufferedImage;

/** Survival input -> shot with a verified mounted stack -> actual hand/world billboard edges.
 * Negative cases require a funded shot, not the absence of a click or a missed screenshot. */
final class MuzzleEffectsSmoke {
    private static final Identifier AK = id("tacz:ak47"), RC1 = id("tacz:muzzle_silencer_socom_rc1");
    private static final Identifier RULES = id("port_test:muzzle_rules"), LEGACY = id("port_test:muzzle_legacy");
    private static final Identifier DEFAULTS = id("port_test:muzzle_defaults"), MARKER = MuzzleQuadProbe.MARKER;
    private static final Identifier NONE = DefaultAssets.EMPTY_ATTACHMENT_ID, DISPLAY = DefaultAssets.DEFAULT_GUN_DISPLAY_ID;
    private static Identifier id(String s) { return Identifier.parse(s); }

    static void run(ClientGameTestContext ctx, TestSingleplayerContext world) {
        var camera = ctx.computeOnClient(mc -> mc.options.getCameraType());
        boolean smoke = ctx.computeOnClient(mc -> RenderConfig.RENDER_MUZZLE_SMOKE.get());
        boolean disable = ctx.computeOnClient(mc -> RenderConfig.DEBUG_DISABLE_MUZZLE_FLASH.get());
        var saved = world.getServer().computeOnServer(s -> world.getConnection().getServerPlayer().getMainHandItem().copy());
        GunFirePostEvent.EVENT.register(MuzzleQuadProbe::onShot);
        try {
            ctx.runOnClient(mc -> {
                mc.options.setCameraType(CameraType.FIRST_PERSON); RenderConfig.RENDER_MUZZLE_SMOKE.set(true);
                RenderConfig.DEBUG_DISABLE_MUZZLE_FLASH.set(false); NativeMuzzleEffects.clear();
            });
            equip(ctx, world, AK, NONE, DISPLAY);
            var bare = fire(ctx,world,true,true,false,"tacz_muzzle_first");
            equip(ctx, world, AK, RC1, DISPLAY);
            var rc1 = fire(ctx,world,true,true,false,"tacz_muzzle_rc1");
            MuzzleQuadProbe.reduction("first", bare, rc1);
            if (ctx.computeOnClient(mc -> MuzzleQuadProbe.modelLight) != 3 << 4) throw new AssertionError("RC1 model light");
            // Really uninstall the part on this stack. A fresh bare gun wouldn't catch stale state.
            world.getServer().runOnServer(s -> {
                var p = world.getConnection().getServerPlayer(); var stack = p.getMainHandItem();
                ((IGun)stack.getItem()).unloadAttachment(stack, AttachmentType.MUZZLE); p.inventoryMenu.broadcastChanges();
            });
            settled(ctx,AK,NONE,DISPLAY); fire(ctx,world,true,true,false,null).check(.75f);
            equip(ctx,world,AK,id("tacz:muzzle_brake_cthulhu"),DISPLAY);
            fire(ctx,world,true,true,false,null).check(.75f*.13f); // Not a silencer.
            equip(ctx,world,AK,id("tacz:muzzle_brake_cyclone_d2"),DISPLAY);
            fire(ctx,world,true,true,false,null).check(.75f); // Directional smoke, no flash_hider.
            equip(ctx,world,id("tacz:glock_17"),NONE,DISPLAY);
            fire(ctx,world,true,true,false,"tacz_muzzle_glock").check(.4f);

            ctx.runOnClient(mc -> mc.options.setCameraType(CameraType.THIRD_PERSON_BACK));
            equip(ctx,world,AK,NONE,DISPLAY); var f5 = fire(ctx,world,true,true,false,"tacz_muzzle_f5");
            equip(ctx,world,AK,RC1,DISPLAY); MuzzleQuadProbe.reduction("F5", f5,fire(ctx,world,true,true,false,null));
            ctx.runOnClient(mc -> mc.options.setCameraType(CameraType.FIRST_PERSON));
            checkTrackedShooter(ctx,world);

            // Built-in Cyclone is silent ONLY in this gun's exclusive data (no flash_hider).
            // Mounted less_flash has common 0.2, but exclusive zero; more_flash disables only smoke.
            equip(ctx,world,RULES,NONE,DEFAULTS); fire(ctx,world,false,true,false,null);
            equip(ctx,world,RULES,id("port_test:less_flash"),DEFAULTS); fire(ctx,world,false,true,false,null);
            equip(ctx,world,RULES,id("port_test:more_flash"),DEFAULTS); fire(ctx,world,true,false,false,null).check(.75f*1.2f);
            equip(ctx,world,AK,NONE,DEFAULTS); // Omitted smoke_scale requests the definition's default, not suppression.
            ctx.runOnClient(mc -> RenderConfig.DEBUG_DISABLE_MUZZLE_FLASH.set(true));
            fire(ctx,world,false,true,false,null);
            ctx.runOnClient(mc -> { RenderConfig.DEBUG_DISABLE_MUZZLE_FLASH.set(false); RenderConfig.RENDER_MUZZLE_SMOKE.set(false); });
            fire(ctx,world,true,false,false,"tacz_muzzle_smoke_off").check(.75f);
            ctx.runOnClient(mc -> RenderConfig.RENDER_MUZZLE_SMOKE.set(true));

            // Legacy quad keeps the historical silence switch, NOT the renew fractional policy.
            equip(ctx,world,AK,id("tacz:muzzle_brake_cthulhu"),LEGACY); fire(ctx,world,false,false,true,null);
            equip(ctx,world,AK,RC1,LEGACY); fire(ctx,world,false,false,false,null);
            equip(ctx,world,RULES,NONE,LEGACY); fire(ctx,world,false,false,false,null);
            markerFrames(ctx,world);
            ctx.runOnClient(mc -> mc.options.setCameraType(CameraType.THIRD_PERSON_BACK));
            equip(ctx,world,AK,RC1,MARKER); fire(ctx,world,true,true,false,null);
            ctx.runOnClient(mc -> MuzzleBindingSmoke.check(mc,MuzzleQuadProbe.markerFlash,MuzzleQuadProbe.markerSmoke));
            ctx.waitTicks(32);
            ctx.runOnClient(mc -> {
                var d=NativeMuzzleEffects.diagnostics();
                if (d.pendingShots()!=0 || d.liveSmoke()!=0 || WorldEmitterManager.getInstance().emitterCount()!=0)
                    throw new AssertionError("Surviving muzzle state: " + d);
            });
            GunMod.LOGGER.info("PORT_MUZZLE: verified mounted/unmounted, fractional/zero/default/exclusive, independent layers and legacy silence after real shots; emitters expired");
        } finally {
            ctx.getInput().releaseKey(ShootKey.SHOOT_KEY);
            ctx.runOnClient(mc -> { MuzzleQuadProbe.stop(); mc.options.setCameraType(camera); RenderConfig.RENDER_MUZZLE_SMOKE.set(smoke);
                RenderConfig.DEBUG_DISABLE_MUZZLE_FLASH.set(disable); NativeMuzzleEffects.clear(); WorldEmitterManager.getInstance().clear(); });
            world.getServer().runOnServer(s -> { var p=world.getConnection().getServerPlayer(); p.setItemInHand(InteractionHand.MAIN_HAND,saved); p.inventoryMenu.broadcastChanges(); });
        }
    }

    private static ItemStack stack(Identifier id, Identifier part, Identifier display) {
        var builder=GunItemBuilder.create().setId(id).setAmmoCount(12).setAmmoInBarrel(true).setFireMode(FireMode.SEMI);
        if (!NONE.equals(part)) builder.putAttachment(AttachmentType.MUZZLE,part);
        var stack=builder.build(); var gun=(IGun)stack.getItem();
        if (!part.equals(gun.getAttachmentId(stack,AttachmentType.MUZZLE))) throw new AssertionError("Rejected mount: " + id + " / " + part);
        if (!display.equals(DISPLAY)) gun.setGunDisplayId(stack,display);
        return stack;
    }
    private static void equip(ClientGameTestContext ctx, TestSingleplayerContext world, Identifier id, Identifier part, Identifier display) {
        world.getServer().runOnServer(s -> {
            var p=world.getConnection().getServerPlayer(); p.setItemInHand(InteractionHand.MAIN_HAND,stack(id,part,display)); p.inventoryMenu.broadcastChanges();
        });
        settled(ctx,id,part,display);
    }
    private static void settled(ClientGameTestContext ctx, Identifier id, Identifier part, Identifier display) {
        ctx.waitFor(mc -> {
            var held=mc.player.getMainHandItem(); var gun=IGun.getIGunOrNull(held);
            return gun!=null && id.equals(gun.getGunId(held)) && part.equals(gun.getAttachmentId(held,AttachmentType.MUZZLE)) && display.equals(gun.getGunDisplayId(held));
        });
        ctx.waitTicks(25);
        ctx.waitFor(mc -> IGunOperator.fromLivingEntity(mc.player).getSynDrawCoolDown()==0
                && IClientPlayerGunOperator.fromLocalPlayer(mc.player).getDataHolder().clientBaseTimestamp>=0);
    }
    private static MuzzleQuadProbe.Edges fire(ClientGameTestContext ctx, TestSingleplayerContext world, boolean flash, boolean smoke, boolean legacy, String screenshot) {
        int ammo=world.getServer().computeOnServer(s -> { var held=world.getConnection().getServerPlayer().getMainHandItem(); return ((IGun)held.getItem()).getCurrentAmmoCount(held); });
        ctx.runOnClient(mc -> MuzzleQuadProbe.begin(mc.player.getId(),mc.options.getCameraType().isFirstPerson()));
        ctx.getInput().holdKey(ShootKey.SHOOT_KEY);
        try { ctx.waitFor(mc -> MuzzleQuadProbe.shots>0); } finally { ctx.getInput().releaseKey(ShootKey.SHOOT_KEY); }
        world.getServer().waitFor(s -> { var held=world.getConnection().getServerPlayer().getMainHandItem(); return ((IGun)held.getItem()).getCurrentAmmoCount(held)==ammo-1; });
        ctx.waitTicks(4); // Include the next simulation tick and deferred draw, even for a negative.
        var edges=ctx.computeOnClient(mc -> {
            if (MuzzleQuadProbe.shots!=1 || (MuzzleQuadProbe.last!=null)!=flash || (MuzzleQuadProbe.smokeQuads>0)!=smoke || (MuzzleQuadProbe.legacyTasks>0)!=legacy)
                throw new AssertionError(((IGun)mc.player.getMainHandItem().getItem()).getGunId(mc.player.getMainHandItem()) + "/" + ((IGun)mc.player.getMainHandItem().getItem()).getGunDisplayId(mc.player.getMainHandItem())
                        + "/" + ((IGun)mc.player.getMainHandItem().getItem()).getAttachmentId(mc.player.getMainHandItem(),AttachmentType.MUZZLE) + " expected flash/smoke/legacy " + flash+"/"+smoke+"/"+legacy
                        + " got " + MuzzleQuadProbe.last+"/"+MuzzleQuadProbe.smokeQuads+"/"+MuzzleQuadProbe.legacyTasks+" shots="+MuzzleQuadProbe.shots);
            if (!flash && MuzzleQuadProbe.modelLight!=0) throw new AssertionError("Hidden flash still lit model");
            return MuzzleQuadProbe.last;
        });
        if (screenshot!=null) ctx.takeScreenshot(screenshot);
        return edges;
    }

    private static void markerFrames(ClientGameTestContext ctx, TestSingleplayerContext world) {
        // Same pack variable, but a longer-lived uniquely colored quad. The stock 20 ms strip's
        // random UV/rotation and recoil make a screenshot taken after waitTicks a poor pixel oracle.
        // Face the grass rather than saturating an additive marker against a white sky. Keep
        // a separate flash-off reference for each stack; exclude both the ammo HUD and hotbar.
        ctx.getInput().lookAt(0,25);
        int[] pixels = new int[2]; int i=0;
        for (var part : new Identifier[]{NONE,RC1}) {
            equip(ctx,world,AK,part,MARKER);
            String label=part.equals(NONE)?"bare":"rc1";
            var wall=SnowstormMaterialSmoke.read(ctx.takeScreenshot("tacz_muzzle_marker_"+label+"_off"));
            var edges=fire(ctx,world,true,false,false,null);
            float expected=.8f * (part.equals(NONE) ? .75f : .15f);
            if(!(Math.abs(edges.width()/edges.spawnScale()-expected)<.001f && Math.abs(edges.height()/edges.spawnScale()-expected)<.001f))
                throw new AssertionError("Custom definition creation_expression: expected " + expected + " got " + edges);
            int light=ctx.computeOnClient(mc -> MuzzleQuadProbe.modelLight);
            if (light != (part.equals(NONE)?10:3)<<4) throw new AssertionError("Configured flash ID model light="+light);
            var lit=SnowstormMaterialSmoke.read(ctx.takeScreenshot("tacz_muzzle_marker_"+label+"_on"));
            pixels[i++]=pink(wall,lit);
        }
        if (!(pixels[1]>=12 && pixels[0]>pixels[1]*5)) throw new AssertionError("Custom flash visible pink pixels bare/RC1="+pixels[0]+"/"+pixels[1]);
        GunMod.LOGGER.info("PORT_MUZZLE_PIXELS: custom flash bare/RC1={}/{} added pink pixels vs own controls; model block light 10/3",pixels[0],pixels[1]);
    }
    private static int pink(BufferedImage off, BufferedImage on) {
        int n=0;
        for(int y=on.getHeight()/5;y<on.getHeight()*3/4;y++) for(int x=on.getWidth()/5;x<on.getWidth()*4/5;x++) {
            int p=on.getRGB(x,y), q=off.getRGB(x,y);
            int a=Math.min(p>>16&255,p&255)-(p>>8&255), b=Math.min(q>>16&255,q&255)-(q>>8&255);
            if(a>60 && a-b>50)n++;
        }
        return n;
    }

    private static void checkTrackedShooter(ClientGameTestContext ctx, TestSingleplayerContext world) {
        var mob=world.getServer().computeOnServer(s -> {
            var p=world.getConnection().getServerPlayer(); var entity=EntityTypes.HUSK.create(p.level(),net.minecraft.world.entity.EntitySpawnReason.COMMAND);
            if(entity==null)throw new AssertionError("Cannot create tracked shooter");
            entity.snapTo(p.position().add(0,0,3)); entity.setNoAi(true); entity.setItemInHand(InteractionHand.MAIN_HAND,stack(AK,RC1,DISPLAY));
            p.level().addFreshEntity(entity); IGunOperator.fromLivingEntity(entity).initialData(); return entity;
        });
        try {
            world.getConnection().waitForClientboundEntityUpdates(EntityTypes.HUSK);
            ctx.getInput().lookAt(net.minecraft.core.BlockPos.containing(mob.position().add(0,1,0)));
            ctx.waitFor(mc -> mc.level.getEntity(mob.getId()) instanceof net.minecraft.world.entity.LivingEntity living
                    && living.getMainHandItem().getItem() instanceof IGun gun && RC1.equals(gun.getAttachmentId(living.getMainHandItem(),AttachmentType.MUZZLE)));
            ctx.runOnClient(mc -> MuzzleQuadProbe.begin(mob.getId(),false));
            for(int i=0;i<6 && ctx.computeOnClient(mc -> MuzzleQuadProbe.last==null);i++) {
                world.getServer().runOnServer(s -> { var api=ScriptGuardSmoke.bind(mob); api.setPitchSupplier(() -> -90f); api.shootOnce(true); });
                ctx.waitTicks(3);
            }
            ctx.runOnClient(mc -> {
                if(MuzzleQuadProbe.last==null || MuzzleQuadProbe.shots<1 || MuzzleQuadProbe.smokeQuads==0)throw new AssertionError("Missing tracked owner muzzle geometry");
                MuzzleQuadProbe.last.check(.75f*.2f);
                GunMod.LOGGER.info("PORT_MUZZLE_SCALE: tracked RC1 {} detached smoke draws={}",MuzzleQuadProbe.last,MuzzleQuadProbe.smokeQuads);
            });
            ctx.takeScreenshot("tacz_muzzle_tracked");
        } finally { world.getServer().runOnServer(s -> mob.discard()); }
    }
}
