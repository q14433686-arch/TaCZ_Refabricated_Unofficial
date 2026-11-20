package com.tacz.guns.client.model.papi;

/** Display estimate in Minecraft blocks/ticks only. Mirrors the pack's dry, unscripted entity tick;
 * no spawning, server hooks, spread sampling or mutation of a live projectile. */
public final class ImpactPreview {
    private ImpactPreview() {}

    public static double slope(double blocks, double step, double gravity, double friction, int lifeTicks) {
        if (!(blocks > 0 && step > 0 && gravity >= 0 && friction >= 0 && friction <= 1)
                || !Double.isFinite(blocks) || !Double.isFinite(step) || !Double.isFinite(gravity)) return Double.NaN;
        double left = blocks, fallStep = 0, fall = 0;
        for (int i = 0; i < Math.min(600, lifeTicks) && step > 0.0001; i++) {
            if (left <= step) return (fall + fallStep * left / step) / blocks;
            left -= step;
            fall += fallStep;
            step *= 1 - friction;
            fallStep = fallStep * (1 - friction) + gravity;
        }
        return Double.NaN; // beyond the entity lifetime or unreachable, don't show a stale box
    }
}
