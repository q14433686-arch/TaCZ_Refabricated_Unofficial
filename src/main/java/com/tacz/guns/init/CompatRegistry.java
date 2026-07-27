package com.tacz.guns.init;

import net.fabricmc.loader.api.FabricLoader;

public class CompatRegistry {
    public static final String CLOTH_CONFIG = "cloth-config";
    public static final String IRIS = "iris";
    public static final String CARRY_ON_ID = "carryon";

    public static void onEnqueue() {
        // 26.2: Iris/CarryOn compat temporarily disabled (dependencies not yet available)
        // checkModLoad(IRIS, IrisCompat::initCompat);
        // checkModLoad(CARRY_ON_ID, BlackList::addBlackList);
    }

    public static void checkModLoad(String modId, Runnable runnable) {
        if (FabricLoader.getInstance().isModLoaded(modId)) {
            runnable.run();
        }
    }
}
