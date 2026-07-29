package com.tacz.guns.compat.iris.newly;

/**
 * Iris 新版本的可选反射桥；26.1.2 使用 ShadowRenderingState 查询 shadow pass。
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
