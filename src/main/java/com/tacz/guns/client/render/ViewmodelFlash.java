package com.tacz.guns.client.render;

import com.tacz.guns.client.particle.NativeMuzzleEffects;
import com.tacz.guns.compat.iris.IrisCompat;
import net.minecraft.world.item.ItemStack;

import java.util.function.IntConsumer;

/** Scope one exposure to extraction of the local gun and its two arms. A delayed draw must
 * capture the gain now, not look up a shot timestamp or change a global shader colour at flush. */
public final class ViewmodelFlash {
    private static final ScopedValue<Float> GAIN = ScopedValue.newInstance();

    public static void submit(ItemStack gun, int ambient, IntConsumer draw) {
        int lit = NativeMuzzleEffects.flashLight(gun, ambient);
        if (lit == ambient || !IrisCompat.hasActiveIrisPack()) {
            draw.accept(lit);
            return;
        }
        // Reuse the pack's bounded block-light increment (including the mount multiplier).
        // Keep BOTH original light coordinates for the normal shader pass. Only the shot's
        // contribution is neutral; a soul lantern must still colour the ambient gun and skin.
        float gain = (((lit >> 4) & 15) - ((ambient >> 4) & 15)) / 15f;
        ScopedValue.where(GAIN, gain).run(() -> draw.accept(ambient));
    }
    public static float gain() { return GAIN.orElse(0f); }
    private ViewmodelFlash() {}
}
