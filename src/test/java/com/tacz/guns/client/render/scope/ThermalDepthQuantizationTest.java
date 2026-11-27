package com.tacz.guns.client.render.scope;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** The target's DEPTH_COMPONENT32 is UNORM32. Sampling returns float, losing low rasterizer
 * bits at ordinary world depths. A raw depth copy is not an EQUAL-compatible sampler copy. */
class ThermalDepthQuantizationTest {
    private static final double UINT32_MAX = 4294967295d;
    private static long store(float z) { return Math.round((double) z * UINT32_MAX); }
    private static float sample(long z) { return (float) (z / UINT32_MAX); }

    @Test void canonicalCopyRoundTripsWhileBitCopyDoesNot() {
        long raw = Math.round(.99674094 * UINT32_MAX) + 3;
        long canonical = store(sample(raw));
        assertNotEquals(raw, canonical, "raw rasterizer bits must exercise the fixed-point/float gap");
        assertEquals(canonical, store(sample(canonical)));
        assertEquals(sample(raw), sample(canonical));
    }

    @Test void noDepthBiasOrOcclusionWindowIsIntroduced() {
        for (double scene : new double[]{0, .01, .1, .5, .9, .99674094, 1}) {
            long raw = Math.round(scene * UINT32_MAX);
            long canonical = store(sample(raw));
            double error = Math.abs(canonical - raw) / UINT32_MAX;
            assertTrue(error <= .5 * Math.ulp(sample(raw)) + 1 / UINT32_MAX);
            assertEquals(canonical, store(sample(canonical)));
        }
    }
}
