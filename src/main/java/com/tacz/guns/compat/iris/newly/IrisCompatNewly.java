package com.tacz.guns.compat.iris.newly;

/** Iris 1.7+ reflection bridge; no hard Iris dependency is loaded when absent. */
public final class IrisCompatNewly {
    // The public API queries the current pass (which can change *within* the frame).
    // Cache the lookup, never cache its boolean result or load Iris on the no-Iris path.
    private record ShadowPass(Object api, java.lang.reflect.Method query) {
        static ShadowPass resolve() {
            try {
                var type = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
                return new ShadowPass(type.getMethod("getInstance").invoke(null), type.getMethod("isRenderingShadowPass"));
            } catch (ReflectiveOperationException | LinkageError e) {
                return null;
            }
        }
    }
    private static final ShadowPass SHADOW = ShadowPass.resolve();

    public static boolean isRenderShadow() {
        try {
            return SHADOW != null && (boolean) SHADOW.query.invoke(SHADOW.api);
        } catch (ReflectiveOperationException e) {
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
