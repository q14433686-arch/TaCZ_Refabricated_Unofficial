package com.tacz.guns.compat.zoomify;

import net.fabricmc.loader.api.FabricLoader;

/**
 * Optional facade for Zoomify on Minecraft 26.3.
 *
 * <p>Delegates to {@link ZoomifyCompatInner} only after confirming the {@code zoomify}
 * mod is loaded so {@code dev.isxander.zoomify.Zoomify} is never touched when absent.</p>
 */
public class ZoomifyCompat {
    private static final String MOD_ID = "zoomify";
    private static boolean INSTALLED = false;

    public static void init() {
        INSTALLED = FabricLoader.getInstance().isModLoaded(MOD_ID);
        if (INSTALLED) {
            ZoomifyCompatInner.init();
        }
    }

    public static double getFov(double fov, float tickDelta) {
        if (INSTALLED) {
            return ZoomifyCompatInner.getFov(fov, tickDelta);
        }
        return fov;
    }
}
