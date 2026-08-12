package com.tacz.guns.compat.shouldersurfing;

import net.fabricmc.loader.api.FabricLoader;

public final class ShoulderSurfingCompat {
    private static final String MOD_ID = "shouldersurfing";
    private static boolean INSTALLED = false;

    public static void init() {
        INSTALLED = FabricLoader.getInstance().isModLoaded(MOD_ID) && hasV5Api();
    }

    private static boolean hasV5Api() {
        try {
            Class.forName("com.github.exopandora.shouldersurfing.api.client.Perspective", false,
                    ShoulderSurfingCompat.class.getClassLoader());
            return true;
        } catch (ClassNotFoundException ignored) {
            // 26.1.2 also had 4.x builds. They use the legacy plugin API; fail closed instead of
            // linking ShoulderSurfingCompatInner against classes that only exist in 5.x.
            return false;
        }
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
