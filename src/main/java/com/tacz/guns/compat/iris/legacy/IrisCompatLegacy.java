package com.tacz.guns.compat.iris.legacy;

/**
 * Iris 旧版本的可选反射桥；不建立编译期依赖。
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
