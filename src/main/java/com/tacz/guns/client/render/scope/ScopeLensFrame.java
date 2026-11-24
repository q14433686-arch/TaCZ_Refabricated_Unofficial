package com.tacz.guns.client.render.scope;

import com.tacz.guns.api.client.gameplay.IClientPlayerGunOperator;
import com.tacz.guns.client.resource.pojo.display.attachment.ScopeLensConfig;
import com.tacz.guns.compat.iris.IrisCompat;
import com.tacz.guns.config.client.RenderConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Util;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;

/** The world is finished before the current hand mask exists. Predict from the last actual
 * mask, then require identity with the display that submitted this frame's geometry. */
public final class ScopeLensFrame {
    static @Nullable ScopeLensConfig expected;
    private static ScopeLensConfig observed;
    private static final float[] bounds = new float[4];
    static boolean disabled;

    public static void clear() {
        observed = expected = null;
        disabled = false;
        ScopeThermalCapture.close();
    }

    public static void ocular(@Nullable ScopeLensConfig lens, java.util.List<com.tacz.guns.client.renderer.snapshot.BedrockRenderSnapshot> geometry) {
        observed = lens;
        if (lens == null || lens != expected) return;
        var projection = ScopeProjection.current();
        if (projection == null) { observed = null; return; }
        var clip = new Matrix4f(projection).mul(com.mojang.blaze3d.systems.RenderSystem.getModelViewMatrix());
        bounds[0] = bounds[1] = Float.POSITIVE_INFINITY; bounds[2] = bounds[3] = Float.NEGATIVE_INFINITY;
        for (var part : geometry) part.extendOcularBounds(clip, bounds);
        if (!(bounds[0] < bounds[2] && bounds[1] < bounds[3])) observed = null;
    }

    private ScopeLensFrame() {}

    static void beginFrame(Minecraft mc) {
        expected = observed; observed = null;
        if (expected != null && (disabled || !RenderConfig.EXPERIMENTAL_SCOPE_LENS.get()
                || !RenderConfig.SCOPE_MASK_ENABLE.get() || mc.player == null || mc.level == null
                || mc.getCameraEntity() != mc.player || !mc.options.getCameraType().isFirstPerson()
                || mc.player.isEyeInFluid(net.minecraft.tags.FluidTags.LAVA)
                || IClientPlayerGunOperator.fromLocalPlayer(mc.player).getClientAimingProgress(
                        mc.getDeltaTracker().getGameTimeDeltaPartialTick(false)) <= 0.001f)) expected = null;
        ScopeThermalCapture.beginFrame(needsHeat());
    }

    static boolean needsHeat() {
        return expected != null && expected.getVisionMode().isThermal() && RenderConfig.SCOPE_THERMAL_ENABLE.get();
    }

    static @Nullable ScopeLensConfig current() {
        return observed == expected && ScopeDepthCopyState.hasMaskCycleThisFrame() ? expected : null;
    }

    // core/screenquad doesn't transform vertices. Its otherwise unused ModelViewMat supplies
    // four vec4 columns in the existing DynamicTransforms layout, not a second projection.
    static Matrix4f uniforms(ScopeLensConfig lens, boolean heatReady) {
        float[] b = bounds;
        int tint = lens.getTint();
        float t = lens.getTintStrength();
        var mode = lens.getVisionMode();
        if (mode.isThermal() && !heatReady) mode = ScopeVisionMode.NORMAL;
        return new Matrix4f().set(
                lens.getDistortion(), lens.getAberration(), lens.getEdgeWidth(), lens.getVignetteStrength(),
                lens.getVignetteWidth(), lens.getVisionGain(), lens.getVisionNoise(), (Util.getMillis() % 60000L) / 1000f,
                1-t+t*((tint>>16)&255)/255f, 1-t+t*((tint>>8)&255)/255f, 1-t+t*(tint&255)/255f, mode.ordinal(),
                .5f+.25f*(b[0]+b[2]), .5f+.25f*(b[1]+b[3]), .25f*(b[2]-b[0]), .25f*(b[3]-b[1]));
    }
}
