package com.tacz.guns.compat.iris.newly;

/**
 * 26.2 迁移: Iris 新版兼容层改为 no-op
 */
public final class IrisCompatNewly {
    public static boolean isRenderShadow() {
        try {
            Class<?> clazz = Class.forName("net.irisshaders.iris.shadows.ShadowRenderingState");
            return (Boolean) clazz.getMethod("areShadowsCurrentlyBeingRendered").invoke(null);
        } catch (Exception e) {
            return false;
        }
    }

    public static boolean endBatch(Object bufferSource) {
        try {
            // 反射检查 FullyBufferedMultiBufferSource
            Class<?> clazz = Class.forName("net.irisshaders.batchedentityrendering.impl.FullyBufferedMultiBufferSource");
            if (clazz.isInstance(bufferSource)) {
                clazz.getMethod("endBatch").invoke(bufferSource);
                return true;
            }
        } catch (Exception e) {
            // ignore
        }
        return false;
    }
}
