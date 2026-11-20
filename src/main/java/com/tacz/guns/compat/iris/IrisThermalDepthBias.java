package com.tacz.guns.compat.iris;

import org.lwjgl.opengl.GL11;

/** Scoped OpenGL-only replay state. Shader-pack jitter makes equality against the
 * already-written entity depth unstable. Slope-scaled offset favours that same surface,
 * without disabling terrain occlusion or modifying the scene's depth attachment.
 * Replay runs AFTER LevelRenderer.render returns. The copy still contains forward-Z
 * Iris world depth, but the normal GlConst conversion is back to reversed Z. Match
 * Iris's original comparator for these private draws only. */
public final class IrisThermalDepthBias implements AutoCloseable {
    private static boolean replay;
    private final boolean enabled;
    private final float factor, units;
    private IrisThermalDepthBias() {
        enabled = GL11.glIsEnabled(GL11.GL_POLYGON_OFFSET_FILL);
        factor = GL11.glGetFloat(GL11.GL_POLYGON_OFFSET_FACTOR);
        units = GL11.glGetFloat(GL11.GL_POLYGON_OFFSET_UNITS);
        replay = true;
    }
    public static boolean replayingForwardDepth() { return replay; }
    public static IrisThermalDepthBias begin() { return IrisCompat.hasActiveIrisPack() ? new IrisThermalDepthBias() : null; }
    /** After pipeline binding, which otherwise resets polygon offset on each draw. */
    public static void apply() {
        if (replay) {
            GL11.glEnable(GL11.GL_POLYGON_OFFSET_FILL);
            int func = GL11.glGetInteger(GL11.GL_DEPTH_FUNC);
            float towardCamera = func == GL11.GL_LESS || func == GL11.GL_LEQUAL ? -2f : 2f;
            GL11.glPolygonOffset(towardCamera, towardCamera);
        }
    }
    @Override public void close() {
        replay = false;
        GL11.glPolygonOffset(factor, units);
        if (enabled) GL11.glEnable(GL11.GL_POLYGON_OFFSET_FILL);
        else GL11.glDisable(GL11.GL_POLYGON_OFFSET_FILL);
    }
}
