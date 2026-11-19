package com.tacz.guns.test;

import com.tacz.guns.GunMod;
import com.tacz.guns.api.entity.IGunOperator;
import com.tacz.guns.api.event.common.GunFireEvent;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.attachment.AttachmentType;
import com.tacz.guns.api.item.builder.GunItemBuilder;
import com.tacz.guns.api.item.gun.AbstractGunItem;
import com.tacz.guns.api.item.gun.FireMode;
import com.tacz.guns.config.common.GunConfig;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Real survival connection. Invoke the shipped gun's server shoot path (including Lua), never the
 * velocity packet or the anger handler. Return to the normal world tick to observe actual motion. */
final class ShotConsequencesSmoke {
    private static ServerPlayer cancelShot; // server thread only
    static {
        GunFireEvent.CALLBACK.register(e -> {
            if (e.getLogicalSide().isServer() && e.getShooter() == cancelShot) e.setCanceled(true);
        });
    }

    static void run(ClientGameTestContext ctx, TestSingleplayerContext world) {
        var server = world.getServer();
        server.runOnServer(s -> world.getConnection().getServerPlayer().setGameMode(GameType.SURVIVAL));
        ctx.waitFor(mc -> !mc.player.isCreative() && mc.player.onGround());
        double before = server.computeOnServer(s -> {
            var p = world.getConnection().getServerPlayer();
            equip(p, "db_short", null, null);
            if (!p.onGround()) throw new AssertionError("Expected grounded shooter");
            impulse(p, 0, false);
            equip(p, "db_short", AttachmentType.EXTENDED_MAG, "ammo_mod_overpressure_recoil");
            impulse(p, 0, true);
            double z = p.getZ();
            impulse(p, .112, false);
            return z;
        });
        ctx.waitFor(mc -> mc.player.getZ() < before - .04);
        server.waitFor(s -> world.getConnection().getServerPlayer().getZ() < before - .04);
        ctx.waitTicks(12);
        var jump = ctx.computeOnClient(mc -> mc.options.keyJump);
        ctx.getInput().holdKey(jump);
        double[] airStart = {0};
        try {
            server.waitFor(s -> {
                var p = world.getConnection().getServerPlayer();
                if (p.onGround()) return false;
                airStart[0] = p.getZ();
                impulse(p, .56, false);
                return true;
            });
        } finally { ctx.getInput().releaseKey(jump); }
        ctx.waitFor(mc -> mc.player.getZ() < airStart[0] - .25 && mc.player.onGround());
        server.waitFor(s -> world.getConnection().getServerPlayer().getZ() < airStart[0] - .25);
        server.runOnServer(s -> checkAnger(world.getConnection().getServerPlayer()));
        GunMod.LOGGER.info("PORT_SHOT: DB-2 bare/cancel/ground/air impulses and both positions; hostile/neutral, radius, silent, disabled, cancelled and existing-target controls passed");
    }

    private static void equip(ServerPlayer p, String gun, AttachmentType slot, String accessory) {
        var builder = GunItemBuilder.create().setId(id(gun)).setAmmoCount(2).setAmmoInBarrel(true).setFireMode(FireMode.SEMI);
        if (slot != null) builder.putAttachment(slot, id(accessory));
        ItemStack stack = builder.build();
        if (!(stack.getItem() instanceof IGun g) || slot != null && !id(accessory).equals(g.getAttachmentId(stack, slot)))
            throw new AssertionError("Default pack rejected " + gun + " / " + accessory);
        p.setItemInHand(InteractionHand.MAIN_HAND, stack);
        IGunOperator.fromLivingEntity(p).draw(p::getMainHandItem);
    }

    private static Identifier id(String path) { return Identifier.fromNamespaceAndPath("tacz", path); }

    private static void fire(ServerPlayer p, float pitch, boolean cancel) {
        var stack = p.getMainHandItem();
        var gun = (AbstractGunItem) stack.getItem();
        int before = gun.getCurrentAmmoCount(stack);
        if (before <= 0) throw new AssertionError("Unfunded control shot");
        try {
            if (cancel) cancelShot = p;
            gun.shoot(IGunOperator.fromLivingEntity(p).getDataHolder(), stack, () -> pitch, () -> 0f, p);
        } finally { cancelShot = null; }
        if (gun.getCurrentAmmoCount(stack) != before - (cancel ? 0 : 1)) throw new AssertionError("Shot did not settle as expected");
    }

    private static void impulse(ServerPlayer p, double expected, boolean cancel) {
        Vec3 before = p.getDeltaMovement();
        fire(p, 0, cancel);
        Vec3 delta = p.getDeltaMovement().subtract(before);
        if (delta.distanceTo(new Vec3(0, 0, -expected)) > .0001)
            throw new AssertionError("Wrong recoil impulse " + delta + ", expected -Z " + expected + ", grounded=" + p.onGround());
    }

    private static Mob spawn(ServerPlayer p, EntityType<? extends Mob> type, double x, List<Mob> cleanup) {
        var mob = Objects.requireNonNull(type.create(p.level(), EntitySpawnReason.COMMAND));
        mob.snapTo(p.position().add(x, 0, 0));
        if (!p.level().addFreshEntity(mob)) throw new AssertionError("Could not spawn sound observer");
        cleanup.add(mob);
        return mob;
    }

    private static void checkAnger(ServerPlayer p) {
        // One server task: no vanilla sight/retaliation AI tick can intervene. No shot intersects a mob.
        List<Mob> cleanup = new ArrayList<>();
        boolean enabled = GunConfig.ENABLE_GUN_SOUND_ANGER.get();
        try {
            var near = spawn(p, EntityTypes.ZOMBIE, 31, cleanup);
            var far = spawn(p, EntityTypes.ZOMBIE, 33, cleanup);
            var wolf = spawn(p, EntityTypes.WOLF, 23, cleanup);
            var outside = spawn(p, EntityTypes.WOLF, 25, cleanup);
            var busy = spawn(p, EntityTypes.ZOMBIE, 10, cleanup);
            busy.setTarget(wolf);
            equip(p, "ak47", AttachmentType.MUZZLE, "muzzle_silencer_socom_rc1");
            fire(p, -90, false);
            quiet(near, wolf);
            equip(p, "ak47", null, null);
            GunConfig.ENABLE_GUN_SOUND_ANGER.set(false);
            fire(p, -90, false);
            quiet(near, wolf);
            GunConfig.ENABLE_GUN_SOUND_ANGER.set(true);
            fire(p, -90, true);
            quiet(near, wolf);
            fire(p, -90, false);
            if (near.getTarget() != p || wolf.getLastHurtByMob() != p || busy.getTarget() != wolf)
                throw new AssertionError("Gunfire failed to assign/preserve mob targets");
            quiet(far, outside);
        } finally {
            GunConfig.ENABLE_GUN_SOUND_ANGER.set(enabled);
            cleanup.forEach(Mob::discard);
        }
    }

    private static void quiet(Mob hostile, Mob neutral) {
        if (hostile.getTarget() != null || neutral.getLastHurtByMob() != null) throw new AssertionError("Unexpected gunfire anger");
    }
}
