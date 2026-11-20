package com.tacz.guns.compat.zoomify;

import net.fabricmc.loader.api.FabricLoader;

/** Keep optional third-party types out of the baseline client class. */
public final class ZoomifyCompat {
    private static boolean installed;
    public static void init() {
        installed = FabricLoader.getInstance().isModLoaded("zoomify");
        if (installed) ZoomifyCompatInner.init();
    }
    public static double getFov(double fov, float tickDelta) {
        return installed ? ZoomifyCompatInner.getFov(fov, tickDelta) : fov;
    }
    private ZoomifyCompat() {}
}
