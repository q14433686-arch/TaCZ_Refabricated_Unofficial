package com.tacz.guns.init;

import net.fabricmc.loader.api.FabricLoader;

public class CompatRegistry {
    public static final String CLOTH_CONFIG = "cloth-config";
    public static final String IRIS = "iris";
    public static final String CARRY_ON_ID = "carryon";

    public static void onEnqueue() {
        // No imperative setup is required here: IrisCompat guards its public API calls and mixins
        // by mod presence, while Carry On 2.10 consumes data/carryon/tags/blocks/block_blacklist.json.
    }

    public static void checkModLoad(String modId, Runnable runnable) {
        if (FabricLoader.getInstance().isModLoaded(modId)) {
            runnable.run();
        }
    }
}
