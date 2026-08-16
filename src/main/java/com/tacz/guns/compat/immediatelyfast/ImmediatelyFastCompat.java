package com.tacz.guns.compat.immediatelyfast;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.world.item.ItemStack;

/**
 * Compatibility facade for ImmediatelyFast.
 *
 * <p>ImmediatelyFast does have a 26.2 Fabric build (1.16.x). The old TACZ
 * integration, however, targeted its pre-26.2 public HUD batching API
 * ({@code ImmediatelyFastApi#getBatching}), which no longer exists. Minecraft
 * 26.2 and ImmediatelyFast now both use the extracted/feature-rendering path;
 * TACZ's workbench icon is submitted normally and no manual batch break is
 * required. Keeping this no-op hook preserves call-site compatibility without
 * pretending that the mod itself is unavailable.</p>
 */
public final class ImmediatelyFastCompat {
    private static final String MOD_ID = "immediatelyfast";
    private static boolean installed;

    private ImmediatelyFastCompat() {
    }

    public static void init() {
        installed = FabricLoader.getInstance().isModLoaded(MOD_ID);
    }

    public static void renderHotbarItem(ItemStack stack, boolean pre) {
        // Intentionally empty on 26.2. See class documentation.
    }

    public static boolean isInstalled() {
        return installed;
    }
}
