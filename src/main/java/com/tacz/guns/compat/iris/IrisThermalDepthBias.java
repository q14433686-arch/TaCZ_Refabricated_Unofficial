package com.tacz.guns.compat.iris;

import org.lwjgl.opengl.GL11;

/** Scoped OpenGL-only replay state. Shader-pack jitter makes equality against the
 * already-written entity depth unstable. Slope-scaled offset favours that same surface,
 * without disabling terrain occlusion or modifying the scene's depth attachment.
 * Iris 1.11.2 keeps its world comparison active even after LevelRenderer returns;
 * do not invert it again. Use the comparison actually bound for this draw. */
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
    public static IrisThermalDepthBias begin() { return IrisCompat.hasActiveIrisPack() ? new IrisThermalDepthBias() : null; }
    /** After pipeline binding, which otherwise resets polygon offset on each draw. */
    public static void apply() {
        if (replay) {
            int func = GL11.glGetInteger(GL11.GL_DEPTH_FUNC);
            if (func == GL11.GL_EQUAL) { // shifted-face match needs the exact rasterized depth
                GL11.glDisable(GL11.GL_POLYGON_OFFSET_FILL);
                return;
            }
            GL11.glEnable(GL11.GL_POLYGON_OFFSET_FILL);
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
