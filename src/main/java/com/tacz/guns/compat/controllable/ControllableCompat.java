package com.tacz.guns.compat.controllable;

import com.tacz.guns.api.item.gun.FireMode;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.world.item.ItemStack;

public class ControllableCompat {
    private static final String MOD_ID = "controllable";

    public static void init() {
        // 26.2: ControllableInner not available, controller compat disabled
        // TODO: Re-implement when Controllable mod supports 26.2
    }

    public static void onGunShoot(ItemStack gunItem, FireMode fireMode) {
        // 26.2: no-op
    }
}
