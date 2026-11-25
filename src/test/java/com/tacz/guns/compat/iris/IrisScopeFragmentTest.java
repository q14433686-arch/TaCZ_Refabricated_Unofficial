package com.tacz.guns.compat.iris;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class IrisScopeFragmentTest {
    private static final String HEADER = "#version 330\n#extension GL_ARB_gpu_shader5 : enable\n";
    private static final String SOURCE = HEADER + "out vec4 color;\nvoid main() { color = vec4(1); }";

    @Test void depthRestoreIsHandOnly() {
        for (String key : new String[]{"entities_eyes", "entities_cutout", "particles", "shadow_entities", "sps", "terrain_solid"}) {
            assertSame(SOURCE, IrisScopeFragment.patch(key, SOURCE));
        }
        String hand = IrisScopeFragment.patch("hand_cutout", SOURCE);
        assertTrue(hand.contains("gl_FragDepth = gl_FragCoord.z"));
        assertTrue(hand.contains("tacz_DepthRestoreMode"));
        assertTrue(hand.contains("depthtex2"));
    }

    @Test void clippedEmissiveUsesMatchedCopiesAndNeverWritesDepth() {
        String eyes = IrisScopeFragment.patch("entities_eyes_trans", SOURCE);
        assertTrue(eyes.startsWith(HEADER));
        assertTrue(eyes.contains("texelFetch(tacz_WorldDepthSampler"));
        assertTrue(eyes.contains("texelFetch(tacz_ApertureDepthSampler"));
        assertTrue(eyes.contains("tacz_ScopeMaskMode == 2"));
        assertFalse(eyes.contains("gl_FragDepth"));
        assertFalse(eyes.contains("depthtex2"));
        assertSame(eyes, IrisScopeFragment.patch("entities_eyes_trans", eyes));
    }
}
