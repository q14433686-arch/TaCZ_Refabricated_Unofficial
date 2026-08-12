package com.tacz.guns.compat.ar;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

/**
 * Compatibility facade retained for callers shared with upstream.
 *
 * <p>Accelerated Rendering only publishes through Minecraft 1.21.1 and its API targets the old
 * immediate entity renderer. It has no 26.1.2 Feature Rendering build, so all acceleration hooks
 * intentionally report disabled while TACZ uses its complete {@code SubmitNodeCollector} path.</p>
 */
public class ARCompat {
    public static final String MOD_ID = "acceleratedrendering";
    public static boolean LOADED;

    public static void init() {
        LOADED = false;
    }

    public static boolean shouldAccelerate() {
        return false;
    }

    public static boolean isAccelerated(VertexConsumer vertexConsumer) {
        return false;
    }

    public static void setRenderingLevel() {
    }

    public static void resetRenderingLevel() {
    }

    public static void setRenderLayer(int layer) {
    }

    public static void setRenderBeforeFunction(Runnable runnable) {
    }

    public static void setRenderAfterFunction(Runnable runnable) {
    }

    public static void resetRenderLayer() {
    }

    public static void resetRenderBeforeFunction() {
    }

    public static void resetRenderAfterFunction() {
    }

    public static void disableAcceleration() {
    }

    public static void resetAcceleration() {
    }

    /** Prevents accidental linkage to the absent AcceleratedBeamRenderer implementation. */
    public static void renderLaser(VertexConsumer extension, float z, float width, boolean fadeOut,
                                   PoseStack poseStack, int color) {
    }
}
