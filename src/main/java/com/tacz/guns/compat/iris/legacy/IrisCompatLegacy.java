package com.tacz.guns.compat.iris.legacy;

/**
 * 26.2 迁移: Iris 已不兼容 Vulkan，legacy 兼容层改为 no-op
 */
public final class IrisCompatLegacy {
    public static boolean isRenderShadow() {
        // 反射调用 ShadowRenderingState.areShadowsCurrentlyBeingRendered() 避免硬依赖
        try {
            Class<?> clazz = Class.forName("net.irisshaders.iris.shadows.ShadowRenderingState");
            return (Boolean) clazz.getMethod("areShadowsCurrentlyBeingRendered").invoke(null);
        } catch (Exception e) {
            return false;
        }
    }

    public static boolean endBatch(Object bufferSource) {
        return false;
    }
}
