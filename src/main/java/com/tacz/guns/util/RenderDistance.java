package com.tacz.guns.util;

import cn.sh1rocu.tacz.compat.meshloader.render.ScreenRenderTracker;
import com.mojang.blaze3d.vertex.PoseStack;
import com.tacz.guns.config.client.RenderConfig;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import org.joml.Matrix4f;

@Environment(EnvType.CLIENT)
public final class RenderDistance {
    /**
     * Depth of screen-owned item draws that run outside {@code Screen#render}. Upstream 1.2.0 replaced the
     * old "GUI drawn within 100 ms" timestamp with an exact screen-draw context ({@code ScreenItemVboContext}),
     * because an open screen still renders the world first and the timestamp made every world gun skip LOD.
     * On 26.1.2 a screen's 3D preview is a picture-in-picture element drawn after {@code Screen#render}
     * returns, so {@link ScreenRenderTracker} alone does not cover it; the PIP renderer opens this scope.
     */
    private static int screenItemDrawDepth;

    public static boolean inRenderHighPolyModelDistance(PoseStack poseStack) {
        // Only the active screen draw bypasses LOD; an open screen still renders the world first.
        if (isGuiRender()) {
            return true;
        }
        int distance = RenderConfig.GUN_LOD_RENDER_DISTANCE.get();
        if (distance <= 0) {
            return false;
        }
        Matrix4f matrix4f = poseStack.last().pose();
        float viewDistance = matrix4f.m30() * matrix4f.m30() + matrix4f.m31() * matrix4f.m31() + matrix4f.m32() * matrix4f.m32();
        return viewDistance < distance * distance;
    }

    /** Opens a screen-owned item draw (e.g. the gunsmith table preview). Pair with {@link #endScreenItemDraw()}. */
    public static void beginScreenItemDraw() {
        screenItemDrawDepth++;
    }

    public static void endScreenItemDraw() {
        if (screenItemDrawDepth > 0) screenItemDrawDepth--;
    }

    /**
     * Whether the current draw belongs to a GUI screen: inside {@code Screen#render} or inside a screen-owned
     * picture-in-picture draw. Also used by the poly_mesh context gate ({@code TaczPolyMeshGunModel#isWorldGpuContext}):
     * {@code FIXED}/{@code HEAD} are shared by world display frames/statues and the gunsmith preview.
     */
    public static boolean isGuiRender() {
        return screenItemDrawDepth > 0 || ScreenRenderTracker.isRenderingScreen();
    }
}
