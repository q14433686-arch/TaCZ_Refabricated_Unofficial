package com.tacz.guns.compat.immediatelyfast;

import net.minecraft.world.item.ItemStack;

// 26.2: ImmediatelyFast not yet available, stub implementation
public class ImmediatelyFastCompat {
    private static final String MOD_ID = "immediatelyfast";
    private static boolean INSTALLED = false;

    public static void init() {
        // No-op: ImmediatelyFast not available for 26.2
    }

    public static void renderHotbarItem(ItemStack stack, boolean pre) {
        // No-op: ImmediatelyFast not available for 26.2
    }
}
