package com.tacz.guns.init;

import com.tacz.guns.compat.iris.IrisCompat;
import net.fabricmc.loader.api.FabricLoader;

public class CompatRegistry {
    public static final String CLOTH_CONFIG = "cloth-config";
    public static final String IRIS = "iris";
    public static final String CARRY_ON_ID = "carryon";

    public static void onEnqueue() {
        // Iris 26.2 is available and the compatibility layer uses reflection only.
        // This initialization selects the shadow-pass supplier; leaving it commented
        // made isRenderShadow() permanently false despite the rest of Iris compat running.
        checkModLoad(IRIS, IrisCompat::initCompat);

        // Carry On still has no compile target in this port; its source package remains
        // excluded until a compatible 26.2 API is available.
    }

    public static void checkModLoad(String modId, Runnable runnable) {
        if (FabricLoader.getInstance().isModLoaded(modId)) {
            runnable.run();
        }
    }
}
