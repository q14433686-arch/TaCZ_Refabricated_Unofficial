package com.tacz.guns.test;

import com.mojang.blaze3d.vertex.PoseStack;
import com.tacz.guns.GunMod;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.client.animation.AnimationController;
import com.tacz.guns.api.client.animation.ObjectAnimation;
import com.tacz.guns.client.input.AimKey;
import com.tacz.guns.client.model.BedrockGunModel;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.List;

/** Observe the real constraint write between FP positioning and hand-model submission, not a
 * second invocation of the math helper. A moving ancestor makes zero weights an active control;
 * unit constraint coefficients cancel the correction, irrespective of the signed channel. */
public final class ConstraintPoseSmoke {
    private static BedrockGunModel watched;
    private static final Matrix4f before = new Matrix4f();
    private static boolean captured;
    private static int wanted, samples;
    private static float translation, rotation, lastWeight;
    private static final Vector3f lastT = new Vector3f(), lastR = new Vector3f();

    public static void begin(PoseStack stack, BedrockGunModel model, float weight) {
        captured = false;
        if (model != watched || watched == null) return;
        var constraint = model.getConstraintObject();
        if (constraint == null) throw new AssertionError("Authored AK rig has no constraint");
        lastT.set(constraint.translationConstraint); lastR.set(constraint.rotationConstraint); lastWeight = weight;
        if (weight < .95f || !matches(lastT) || !matches(lastR)) return;
        before.set(stack.last().pose()).invert(); captured = true;
    }

    public static void end(PoseStack stack) {
        if (!captured) return;
        captured = false;
        var delta = before.mul(stack.last().pose());
        translation = Math.max(translation, delta.getTranslation(new Vector3f()).length());
        float w = delta.getUnnormalizedRotation(new Quaternionf()).normalize().w;
        rotation = Math.max(rotation, (float)(2 * Math.acos(Math.min(1, Math.abs(w)))));
        samples++;
    }

    private static boolean matches(Vector3f v) {
        return Math.abs(v.x - wanted) < .001f && Math.abs(v.y - wanted) < .001f && Math.abs(v.z - wanted) < .001f;
    }

    static void run(ClientGameTestContext ctx, AnimationController controller, int track) {
        ctx.getInput().holdKey(AimKey.AIM_KEY);
        try {
            var frames = new HashMap<String, BufferedImage>();
            // Pass through zero (measurable correction) between signs, so the negative clip
            // cannot succeed just because a stale positive channel is still installed.
            for (String name : List.of("positive", "zero", "negative")) {
                ctx.runOnClient(mc -> {
                    watched = TimelessAPI.getGunDisplay(mc.player.getMainHandItem()).orElseThrow().getGunModel();
                    wanted = name.equals("zero") ? 0 : 1; samples = 0; translation = rotation = 0;
                    String clip = "constraint_" + name;
                    if (!controller.containPrototype(clip)) throw new AssertionError("Missing " + clip);
                    controller.runAnimation(track, clip, ObjectAnimation.PlayType.PLAY_ONCE_HOLD, 0);
                });
                try { ctx.waitFor(mc -> samples >= 4); }
                catch (AssertionError e) { throw new AssertionError(name + " constraint not consumed: " + ctx.computeOnClient(mc -> lastT + ", " + lastR + ", ADS=" + lastWeight), e); }
                ctx.runOnClient(mc -> {
                    if (!(Float.isFinite(translation) && Float.isFinite(rotation)) || (wanted == 0
                            ? translation < .04f || rotation < .05f : translation > .002f || rotation > .002f))
                        throw new AssertionError(name + " FP constraint delta: t=" + translation + ", radians=" + rotation);
                    GunMod.LOGGER.info("PORT_CONSTRAINT: ADS {} coefficient={}, hand correction translation={}, radians={}", name, wanted, translation, rotation);
                });
                ctx.waitTicks(6);
                frames.put(name, SnowstormMaterialSmoke.read(ctx.takeScreenshot("tacz_constraint_ads_" + name)));
            }
            int signed = changed(frames.get("positive"), frames.get("negative"));
            int posZero = changed(frames.get("positive"), frames.get("zero"));
            int negZero = changed(frames.get("negative"), frames.get("zero"));
            if (Math.min(posZero, negZero) < 300 || 2 * signed >= Math.min(posZero, negZero))
                throw new AssertionError("Signed ADS pose pixels: +/-=" + signed + ", +/0=" + posZero + ", -/0=" + negZero);
            GunMod.LOGGER.info("PORT_CONSTRAINT: ADS pixels +/−={}, +/0={}, −/0={}", signed, posZero, negZero);
        } finally {
            ctx.runOnClient(mc -> { watched = null; captured = false; controller.removeAnimation(track); });
            ctx.getInput().releaseKey(AimKey.AIM_KEY);
        }
    }

    // Same still camera and wall. Exclude the chat, hotbar, and world edges. Count large RGB
    // differences rather than requiring identical screenshots from a breathing animated rig.
    private static int changed(BufferedImage a, BufferedImage b) {
        int count = 0;
        for (int y = a.getHeight()/12; y < a.getHeight()*3/5; y++)
            for (int x = a.getWidth()/6; x < a.getWidth()*5/6; x++) {
                int v = a.getRGB(x, y), w = b.getRGB(x, y);
                if (Math.abs((v >> 16 & 255) - (w >> 16 & 255)) + Math.abs((v >> 8 & 255) - (w >> 8 & 255))
                        + Math.abs((v & 255) - (w & 255)) >= 48) count++;
            }
        return count;
    }
}
