package com.tacz.guns.compat.iris;

import org.lwjgl.opengl.GL11;

/** Scoped OpenGL-only replay state. Shader-pack jitter makes equality against the
 * already-written entity depth unstable. Slope-scaled offset favours that same surface,
 * without disabling terrain occlusion or modifying the scene's depth attachment.
 * 26.3 uses reversed depth, so a positive offset moves toward the camera. */
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
    public static IrisThermalDepthBias begin() { return IrisCompat.isUsingRenderPack() ? new IrisThermalDepthBias() : null; }
    /** After pipeline binding, which otherwise resets polygon offset on each draw. */
    public static void apply() {
        if (replay) {
            GL11.glEnable(GL11.GL_POLYGON_OFFSET_FILL);
            GL11.glPolygonOffset(2f, 2f);
        }
    }
    @Override public void close() {
        replay = false;
        GL11.glPolygonOffset(factor, units);
        if (enabled) GL11.glEnable(GL11.GL_POLYGON_OFFSET_FILL);
        else GL11.glDisable(GL11.GL_POLYGON_OFFSET_FILL);
    }
}
