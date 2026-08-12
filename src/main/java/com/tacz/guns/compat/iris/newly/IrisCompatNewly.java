package com.tacz.guns.compat.iris.newly;

/** Iris 1.7+ reflection bridge; no hard Iris dependency is loaded when absent. */
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
