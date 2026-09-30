package com.tacz.guns.compat.shouldersurfing;

import net.fabricmc.loader.api.FabricLoader;

/** Optional facade for Shoulder Surfing Reloaded 5.x on Minecraft 26.3. */
public final class ShoulderSurfingCompat {
    private static final String MOD_ID = "shouldersurfing";
    private static boolean INSTALLED = false;

    private ShoulderSurfingCompat() {
    }

    public static void init() {
        INSTALLED = FabricLoader.getInstance().isModLoaded(MOD_ID);
    }

    public static boolean showCrosshair() {
        if (INSTALLED) {
            return ShoulderSurfingCompatInner.showCrosshair();
        }
        return false;
    }

    public static boolean isInstalled() {
        return INSTALLED;
    }
}
