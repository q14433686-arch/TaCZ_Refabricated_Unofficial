package com.tacz.guns.client.gameplay;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ShotEnvelopeTest {
    @Test void continuousAttackRecoveryAndNoDependencyOnRenderRate() {
        var hud = new ShotEnvelope();
        var sparse = new ShotEnvelope();
        hud.fire(1000, 300); sparse.fire(1000, 300);
        assertEquals(0f, hud.sample(1000));
        float prev = 0;
        for (int ms = 1001; ms <= 1065; ms++) {
            float value = hud.sample(ms);
            assertTrue(value > prev); prev = value;
        }
        assertEquals(1f, prev);
        for (int ms = 1066; ms <= 1365; ms++) {
            float value = hud.sample(ms);
            assertTrue(value < prev); prev = value;
            if (ms % 31 == 0) assertEquals(value, sparse.sample(ms));
        }
        assertEquals(0f, hud.sample(1365));
        assertEquals(0f, hud.sample(2000));
    }

    @Test void rapidSubshotsStackPastOneWithoutTeleportingOrRunningAway() {
        var hud = new ShotEnvelope();
        int ms = 2000;
        for (int i = 0; i < 25; i++, ms += 12) {
            float before = hud.sample(ms);
            hud.fire(ms, 450);
            assertEquals(before, hud.sample(ms));
            assertTrue(hud.sample(ms + 10) > before);
        }
        assertEquals(3f, hud.sample(ms - 12 + 65));
        float recovering = hud.sample(ms + 200);
        hud.fire(ms + 200, 450);
        assertEquals(recovering, hud.sample(ms + 200));
        assertTrue(hud.sample(ms + 230) > recovering);
        hud.clear();
        assertEquals(0f, hud.sample(ms + 250));
    }
}
