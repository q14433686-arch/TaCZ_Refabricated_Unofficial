package com.tacz.guns.test;

import com.tacz.guns.GunMod;
import com.tacz.guns.api.client.gameplay.IClientPlayerGunOperator;
import com.tacz.guns.api.entity.IGunOperator;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.builder.GunItemBuilder;
import com.tacz.guns.api.item.gun.FireMode;
import com.tacz.guns.client.input.ShootKey;
import com.tacz.guns.client.particle.NativeMuzzleEffects;
import com.tacz.guns.config.client.RenderConfig;
import com.tacz.guns.client.particle.bedrock.particle.world.WorldEmitterManager;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.CameraType;
import net.minecraft.resources.Identifier;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityTypes;

/** Actual input/settled fire -> locator extraction -> collector. Counters prove submissions and
 * single world bursts, NOT pixel parity; screenshots are retained for visual review. */
final class MuzzleEffectsSmoke {
    static void run(ClientGameTestContext ctx, TestSingleplayerContext world) {
        var camera = ctx.computeOnClient(mc -> mc.options.getCameraType());
        boolean smoke = ctx.computeOnClient(mc -> RenderConfig.RENDER_MUZZLE_SMOKE.get());
        try {
            ctx.runOnClient(mc -> { mc.options.setCameraType(CameraType.FIRST_PERSON); RenderConfig.RENDER_MUZZLE_SMOKE.set(true); NativeMuzzleEffects.clear(); });
            equip(ctx, world, "ak47", FireMode.AUTO);
            shoot(ctx, false, "tacz_muzzle_first");
            equip(ctx, world, "ak47", FireMode.AUTO, true);
            shoot(ctx, false, "tacz_muzzle_rc1");
            // Glock has no legacy texture; this catches accidental loader fallback/rejection.
            equip(ctx, world, "glock_17", FireMode.SEMI);
            shoot(ctx, false, "tacz_muzzle_glock");
            ctx.runOnClient(mc -> mc.options.setCameraType(CameraType.THIRD_PERSON_BACK));
            equip(ctx, world, "ak47", FireMode.AUTO);
            shoot(ctx, true, "tacz_muzzle_f5");
            ctx.runOnClient(mc -> { mc.options.setCameraType(CameraType.FIRST_PERSON); RenderConfig.RENDER_MUZZLE_SMOKE.set(false); NativeMuzzleEffects.clear(); });
            equip(ctx, world, "ak47", FireMode.AUTO);
            long bursts = ctx.computeOnClient(mc -> smokeBursts());
            shoot(ctx, false, "tacz_muzzle_smoke_off");
            if (ctx.computeOnClient(mc -> smokeBursts()) != bursts)
                throw new AssertionError("Smoke-off emitted a world burst");
            checkTrackedShooter(ctx, world);
            ctx.runOnClient(mc -> NativeMuzzleEffects.clear());
            ctx.waitTicks(4);
            if (ctx.computeOnClient(mc -> NativeMuzzleEffects.diagnostics().pendingShots() != 0 || NativeMuzzleEffects.diagnostics().liveSmoke() != 0))
                throw new AssertionError("Muzzle teardown retained live state");
            GunMod.LOGGER.info("PORT_MUZZLE: AK47/Glock first-person, F5, tracked shooter, smoke-off flash and teardown submissions passed; pixel parity not asserted");
        } finally {
            ctx.getInput().releaseKey(ShootKey.SHOOT_KEY);
            ctx.runOnClient(mc -> { mc.options.setCameraType(camera); RenderConfig.RENDER_MUZZLE_SMOKE.set(smoke); NativeMuzzleEffects.clear(); });
        }
    }

    private static void equip(ClientGameTestContext ctx, TestSingleplayerContext world, String id, FireMode mode) {
        equip(ctx, world, id, mode, false);
    }
    private static void equip(ClientGameTestContext ctx, TestSingleplayerContext world, String id, FireMode mode, boolean silencer) {
        world.getServer().runOnServer(s -> {
            var p = world.getConnection().getServerPlayer();
            var builder = GunItemBuilder.create().setId(Identifier.parse("tacz:"+id)).setAmmoCount(30).setAmmoInBarrel(true).setFireMode(mode);
            if (silencer) builder.putAttachment(com.tacz.guns.api.item.attachment.AttachmentType.MUZZLE, Identifier.parse("tacz:muzzle_silencer_socom_rc1"));
            p.setItemInHand(InteractionHand.MAIN_HAND, builder.build());
        });
        ctx.waitFor(mc -> mc.player.getMainHandItem().getItem() instanceof IGun gun && gun.getGunId(mc.player.getMainHandItem()).equals(Identifier.parse("tacz:"+id)));
        ctx.waitTicks(25);
        ctx.waitFor(mc -> IGunOperator.fromLivingEntity(mc.player).getSynDrawCoolDown() == 0
                && IClientPlayerGunOperator.fromLocalPlayer(mc.player).getDataHolder().clientBaseTimestamp >= 0);
    }

    private static void shoot(ClientGameTestContext ctx, boolean third, String screenshot) {
        long before = ctx.computeOnClient(mc -> flashes(third));
        long geometry = ctx.computeOnClient(mc -> WorldEmitterManager.getInstance().rendered());
        long nativeBefore = ctx.computeOnClient(mc -> nativeFlashes(third));
        boolean smokeEnabled = ctx.computeOnClient(mc -> RenderConfig.RENDER_MUZZLE_SMOKE.get());
        long bursts = ctx.computeOnClient(mc -> smokeBursts());
        // Semi fires once per press, automatic holds through several render frames.
        for (int attempt=0; attempt<5; attempt++) {
            ctx.getInput().holdKey(ShootKey.SHOOT_KEY);
            try { ctx.waitTicks(3); } finally { ctx.getInput().releaseKey(ShootKey.SHOOT_KEY); }
            ctx.waitTicks(1);
            if (ctx.computeOnClient(mc -> (flashes(third)) > before)) {
                if (smokeEnabled && ctx.computeOnClient(mc -> smokeBursts()) <= bursts)
                    throw new AssertionError("Flash submitted without a detached smoke burst: " + screenshot);
                // Runtime admissions alone are not evidence of a render. Native backend
                // already counts actual submits; the new backend must also submit geometry.
                if(ctx.computeOnClient(mc -> nativeFlashes(third)) == nativeBefore)
                    ctx.waitFor(mc -> WorldEmitterManager.getInstance().rendered() > geometry);
                ctx.takeScreenshot(screenshot);
                return;
            }
        }
        throw new AssertionError("No muzzle emission/render geometry for " + screenshot + ": " + ctx.computeOnClient(mc -> NativeMuzzleEffects.diagnostics()));
    }

    private static long nativeFlashes(boolean third) {
        var d=NativeMuzzleEffects.diagnostics();
        return third?d.thirdFlashSubmits():d.firstFlashSubmits();
    }
    private static long flashes(boolean third) {
        var d=NativeMuzzleEffects.diagnostics();
        return nativeFlashes(third)+(third?d.runtimeThirdFlashes():d.runtimeFirstFlashes());
    }
    private static long smokeBursts() {
        var d=NativeMuzzleEffects.diagnostics();
        return d.smokeBursts()+d.runtimeSmokeBursts();
    }

    private static void checkTrackedShooter(ClientGameTestContext ctx, TestSingleplayerContext world) {
        var mob = world.getServer().computeOnServer(s -> {
            var p = world.getConnection().getServerPlayer();
            var entity = EntityTypes.HUSK.create(p.level(), net.minecraft.world.entity.EntitySpawnReason.COMMAND);
            if (entity == null) throw new AssertionError("Cannot create tracked shooter");
            entity.snapTo(p.position().add(0,0,3));
            entity.setNoAi(true);
            entity.setItemInHand(InteractionHand.MAIN_HAND, GunItemBuilder.create().setId(Identifier.parse("tacz:ak47"))
                    .setAmmoCount(30).setAmmoInBarrel(true).setFireMode(FireMode.SEMI).build());
            p.level().addFreshEntity(entity);
            IGunOperator.fromLivingEntity(entity).initialData();
            return entity;
        });
        try {
            world.getConnection().waitForClientboundEntityUpdates(EntityTypes.HUSK);
            ctx.getInput().lookAt(net.minecraft.core.BlockPos.containing(mob.position().add(0,1,0)));
            ctx.runOnClient(mc -> RenderConfig.RENDER_MUZZLE_SMOKE.set(true));
            long before = ctx.computeOnClient(mc -> flashes(true));
            long geometry = ctx.computeOnClient(mc -> WorldEmitterManager.getInstance().rendered());
            long nativeBefore = ctx.computeOnClient(mc -> nativeFlashes(true));
            for (int i=0; i<6; i++) {
                world.getServer().runOnServer(s -> {
                    var api = ScriptGuardSmoke.bind(mob);
                    api.setPitchSupplier(() -> -90f);
                    api.shootOnce(true);
                });
                ctx.waitTicks(2);
            }
            if (ctx.computeOnClient(mc -> flashes(true)) <= before)
                throw new AssertionError("Tracked shooter's main-hand locator did not submit a flash");
            if(ctx.computeOnClient(mc -> nativeFlashes(true)) == nativeBefore)
                ctx.waitFor(mc -> WorldEmitterManager.getInstance().rendered() > geometry);
            ctx.takeScreenshot("tacz_muzzle_tracked");
        } finally { world.getServer().runOnServer(s -> mob.discard()); }
    }
}
