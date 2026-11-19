package com.tacz.guns.test;

import com.mojang.blaze3d.vertex.PoseStack;
import com.tacz.guns.GunMod;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.builder.GunItemBuilder;
import com.tacz.guns.api.item.gun.FireMode;
import com.tacz.guns.client.input.ShootKey;
import com.tacz.guns.client.renderer.item.GunItemRendererWrapper;
import com.tacz.guns.entity.EntityKineticBullet;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** Bound fire input → server magazine → tracked entity → real world submission across a turn → stone impact. */
public final class WorldTracerSmoke {
    private static final Identifier GUN = Identifier.parse("port_test:frame_trace");
    private static boolean watching, inside, afterTurn;
    private static int lastTick, frames;
    private static EntityKineticBullet tracer;
    private static final Matrix4f inverseBase = new Matrix4f();
    private static final Vector3f initialOffset = new Vector3f();
    private static final Quaternionf initialCamera = new Quaternionf();
    private static float fade;

    public static void begin(EntityKineticBullet bullet, float tick, PoseStack pose) {
        if (!watching || !GUN.equals(bullet.getGunId())) return;
        var mc = Minecraft.getInstance();
        if (bullet.getOwner() != mc.player) throw new AssertionError("Not the firing player's tracer");
        if (tracer == null) {
            tracer = bullet;
            initialOffset.set(GunItemRendererWrapper.muzzleRenderOffset);
            initialCamera.set(mc.gameRenderer.mainCamera().rotation());
            if (initialOffset.lengthSquared() < .00001f) throw new AssertionError("Degenerate muzzle origin");
        } else if (tracer != bullet) throw new AssertionError("More than one tracked tracer from a semi-auto press");
        double distance = bullet.getPosition(tick).distanceTo(mc.player.getEyePosition(tick));
        fade = (float)Math.max(0, 1 - distance / 50);
        pose.last().pose().invert(inverseBase);
        inside = true;
    }

    public static void model(PoseStack pose) {
        if (!inside) return;
        // -1/2 in longitudinal model space cancels the strip's +length/2 centering, leaving the
        // displayed projectile origin. Invert this frame's renderer entry pose, not the camera pose.
        var actual = new Vector3f(0,0,-.5f).mulPosition(pose.last().pose()).mulPosition(inverseBase);
        var expected = new Vector3f(initialOffset).rotate(initialCamera).mul(fade);
        if (actual.distance(expected) > .001f) throw new AssertionError("World tracer origin: "+actual+" vs frozen "+expected);
        if (lastTick != tracer.tickCount) { frames++; lastTick = tracer.tickCount; }
        var live = Minecraft.getInstance().gameRenderer.mainCamera().rotation();
        if (Math.abs(initialCamera.dot(live)) < .998f) afterTurn = true;
    }
    public static void end() { inside = false; }

    static void run(ClientGameTestContext ctx, TestSingleplayerContext world) {
        var facing = ctx.computeOnClient(mc -> new float[]{mc.player.getYRot(), mc.player.getXRot()});
        var pos = world.getServer().computeOnServer(s -> BlockPos.containing(world.getConnection().getServerPlayer().getEyePosition()).south(15));
        var saved = world.getServer().computeOnServer(s -> {
            var p = world.getConnection().getServerPlayer();
            if (!p.level().isEmptyBlock(pos)) throw new AssertionError("Trace control needs empty space");
            var old = p.getMainHandItem().copy();
            p.level().setBlock(pos, Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
            p.setItemInHand(InteractionHand.MAIN_HAND, GunItemBuilder.create().setId(GUN).setFireMode(FireMode.SEMI)
                    .setAmmoCount(3).setAmmoInBarrel(true).build());
            p.inventoryMenu.broadcastChanges();
            return old;
        });
        try {
            ctx.waitFor(mc -> mc.level.getBlockState(pos).is(Blocks.STONE) && mc.player.getMainHandItem().getItem() instanceof IGun g
                    && GUN.equals(g.getGunId(mc.player.getMainHandItem())));
            ctx.getInput().lookAt(pos); ctx.waitTicks(35);
            ctx.runOnClient(mc -> {
                watching = true; afterTurn = false; lastTick = -1; frames = 0; tracer = null;
                SurfaceFragmentSmoke.watch(pos);
            });
            ctx.getInput().holdKey(ShootKey.SHOOT_KEY);
            ctx.waitFor(mc -> frames >= 2);
            ctx.getInput().releaseKey(ShootKey.SHOOT_KEY);
            ctx.takeScreenshot("tacz_fired_tracer_before_turn");
            ctx.getInput().lookAt(16, -4);
            ctx.waitFor(mc -> afterTurn && frames >= 3);
            ctx.takeScreenshot("tacz_fired_tracer_after_turn");
            ctx.waitFor(mc -> SurfaceFragmentSmoke.receivedFiveStoneFragments());
            world.getServer().runOnServer(s -> {
                var stack = world.getConnection().getServerPlayer().getMainHandItem();
                if (((IGun)stack.getItem()).getCurrentAmmoCount(stack) != 2) throw new AssertionError("Wrong server magazine after shot");
            });
            GunMod.LOGGER.info("PORT_WORLD_TRACER: one funded shot, {} distinct render ticks, frozen origin after a turn, five stone fragments", frames);
        } finally {
            ctx.getInput().releaseKey(ShootKey.SHOOT_KEY);
            ctx.getInput().lookAt(facing[0], facing[1]);
            ctx.runOnClient(mc -> { watching = inside = false; tracer = null; SurfaceFragmentSmoke.watch(null); });
            world.getServer().runOnServer(s -> {
                var p = world.getConnection().getServerPlayer();
                p.level().removeBlock(pos, false); p.setItemInHand(InteractionHand.MAIN_HAND, saved); p.inventoryMenu.broadcastChanges();
            });
        }
    }
}
