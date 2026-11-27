package com.tacz.guns.client.render;

import com.tacz.guns.client.particle.NativeMuzzleEffects;
import com.tacz.guns.compat.iris.IrisCompat;
import net.minecraft.world.item.ItemStack;

import java.util.function.IntConsumer;

/** Scope one exposure to extraction of the local gun and its two arms. A delayed draw must
 * capture the gain now, not look up a shot timestamp or change a global shader colour at flush. */
public final class ViewmodelFlash {
    private static final ThreadLocal<Float> GAIN = new ThreadLocal<>();

    public static void submit(ItemStack gun, int ambient, IntConsumer draw) {
        int lit = NativeMuzzleEffects.flashLight(gun, ambient);
        if (lit == ambient || !IrisCompat.isUsingRenderPack()) {
            withGain(0, lit, draw);
            return;
        }
        // Reuse the pack's bounded block-light increment (including the mount multiplier).
        // Keep BOTH original light coordinates for the normal shader pass. Only the shot's
        // contribution is neutral; a soul lantern must still colour the ambient gun and skin.
        float gain = (((lit >> 4) & 15) - ((ambient >> 4) & 15)) / 15f;
        withGain(gain, ambient, draw);
    }
    private static void withGain(float gain, int light, IntConsumer draw) {
        Float previous = GAIN.get();
        GAIN.set(gain);
        try {
            draw.accept(light);
        } finally {
            if (previous == null) GAIN.remove();
            else GAIN.set(previous);
        }
    }

    public static float gain() {
        Float gain = GAIN.get();
        return gain == null ? 0f : gain;
    }
    private ViewmodelFlash() {}
}
