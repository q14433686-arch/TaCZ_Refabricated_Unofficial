package com.tacz.guns.compat.immediatelyfast;

import net.minecraft.world.item.ItemStack;

/**
 * Compatibility facade for an obsolete ImmediatelyFast batching workaround.
 *
 * <p>ImmediatelyFast itself supports 26.1.2, but the old ImmediatelyFastApi HUD batching controls
 * used by TACZ 1.21.1 no longer exist. The 26.1.2 item bridge submits through GuiItemAtlas and
 * {@code SubmitNodeCollector}, so there is no immediate batch to end/restart here.</p>
 */
public class ImmediatelyFastCompat {
    public static void init() {
    }

    public static void renderHotbarItem(ItemStack stack, boolean pre) {
    }
}
