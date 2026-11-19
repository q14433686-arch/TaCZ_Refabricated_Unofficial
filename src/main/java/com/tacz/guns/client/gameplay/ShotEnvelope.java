package com.tacz.guns.client.gameplay;

/** Cosmetic displacement in units of one shot. Sampling never advances it: HUD, editor and FPS agree. */
public final class ShotEnvelope {
    private static final int RISE_MS = 65;
    private long last = -1;
    private int recovery;
    private float start, peak;

    public void fire(long now, int recoveryMs) {
        start = sample(now);
        // Even a burst faster than the attack phase adds impulses, without a discontinuity on the new frame.
        float remaining = last >= 0 && now >= last && now - last < RISE_MS ? peak : start;
        peak = Math.min(3, remaining + 1);
        recovery = Math.max(1, recoveryMs);
        last = now;
    }

    public float sample(long now) {
        long t = now - last;
        if (last < 0 || t < 0 || t >= RISE_MS + recovery) return 0;
        if (t < RISE_MS) return start + (peak - start) * ease(t / (float) RISE_MS);
        return peak * (1 - ease((t - RISE_MS) / (float) recovery));
    }

    private static float ease(float u) { return u * u * (3 - 2 * u); }
    public void clear() { last = -1; start = peak = 0; }
}
