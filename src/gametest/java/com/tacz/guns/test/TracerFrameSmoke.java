package com.tacz.guns.test;

import com.mojang.blaze3d.vertex.PoseStack;
import com.tacz.guns.client.renderer.entity.EntityBulletRenderer;
import com.tacz.guns.client.renderer.item.GunItemRendererWrapper;
import com.tacz.guns.entity.EntityKineticBullet;
import com.tacz.guns.init.ModEntities;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeStorage;
import org.joml.*;

/** Sample the pose submitted for the same stationary test tracer either side of a camera turn. */
public final class TracerFrameSmoke {
    private static boolean observing;
    private static Matrix4f submitted;
    public static void observe(PoseStack pose) { if (observing) submitted = new Matrix4f(pose.last().pose()); }
    private static Matrix4f submit(Minecraft mc, EntityKineticBullet bullet) {
        var renderer = (EntityBulletRenderer)(Object)mc.getEntityRenderDispatcher().getRenderer(bullet);
        submitted = null; observing = true;
        try {
            renderer.renderTracerAmmo(bullet, new float[]{1,1,1}, 1, new PoseStack(), new SubmitNodeStorage(), 0xf000f0);
            if (submitted == null) throw new AssertionError("No tracer geometry submitted");
            return submitted;
        } finally { observing = false; }
    }
    static void run(ClientGameTestContext ctx) {
        var camera = ctx.computeOnClient(mc -> mc.options.getCameraType());
        var bullet = ctx.computeOnClient(mc -> {
            mc.options.setCameraType(CameraType.FIRST_PERSON);
            var b = new EntityKineticBullet(ModEntities.BULLET, mc.level);
            b.setOwner(mc.player); b.setPos(mc.player.getEyePosition().add(0,0,3)); b.setDeltaMovement(0,0,.25); b.tickCount = 5;
            return b;
        });
        try {
            ctx.getInput().lookAt(0,0); ctx.waitTicks(2);
            var first = ctx.computeOnClient(mc -> {
                var rot = mc.gameRenderer.mainCamera().rotation();
                var original = new Quaternionf(rot);
                var old = new Vector3f(GunItemRendererWrapper.muzzleRenderOffset);
                try {
                    rot.rotateZ(.6f); GunItemRendererWrapper.muzzleRenderOffset.set(.75f,.2f,.15f);
                    var initial = submit(mc,bullet);
                    if (!rot.equals(bullet.getFirstPersonCameraRotation(), 1e-5f)) throw new AssertionError("Lost camera roll");
                    return initial;
                } finally { rot.set(original); GunItemRendererWrapper.muzzleRenderOffset.set(old); }
            });
            ctx.getInput().lookAt(95,26); ctx.waitTicks(3);
            ctx.runOnClient(mc -> {
                if (!first.equals(submit(mc,bullet), .001f)) throw new AssertionError("Tracer followed the new camera/muzzle");
                bullet.setCameraYRot(90);
                if (bullet.getFirstPersonCameraRotation() != null || first.equals(submit(mc,bullet), .001f)) throw new AssertionError("Legacy Euler override ignored");
                mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
                var third = submit(mc,bullet);
                bullet.setFirstPersonRenderOffset(new Vector3f(9,9,9));
                if (!third.equals(submit(mc,bullet), .001f)) throw new AssertionError("First person offset leaked into F5");
            });
        } finally { ctx.runOnClient(mc -> mc.options.setCameraType(camera)); }
    }
}
