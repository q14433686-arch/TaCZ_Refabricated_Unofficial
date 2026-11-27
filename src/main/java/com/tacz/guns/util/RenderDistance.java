package com.tacz.guns.util;

import com.mojang.blaze3d.vertex.PoseStack;
import cn.sh1rocu.tacz.compat.meshloader.render.ScreenRenderTracker;
import com.tacz.guns.config.client.RenderConfig;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import org.joml.Matrix4f;

@Environment(EnvType.CLIENT)
public final class RenderDistance {
    private static int screenItemDrawDepth;

    public static boolean inRenderHighPolyModelDistance(PoseStack poseStack) {
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

    /** Render-thread-only scope for deferred screen item draws (including PIP). */
    public static void beginScreenItemDraw() {
        ++screenItemDrawDepth;
    }

    public static void endScreenItemDraw() {
        if (screenItemDrawDepth > 0) {
            --screenItemDrawDepth;
        }
    }

    /** True only inside screen extraction or an explicitly scoped deferred preview draw. */
    public static boolean isGuiRender() {
        return screenItemDrawDepth > 0 || ScreenRenderTracker.isExtractingScreen();
    }
}
