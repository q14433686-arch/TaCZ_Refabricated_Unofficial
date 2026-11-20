package com.tacz.guns.util;

import org.junit.jupiter.api.Test;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class CycleTaskTest {
    @Test void delayIsNotSubtractedTwiceAndPausedWorldDoesNotCatchUp() {
        var calls = new AtomicInteger();
        var cycle = new CycleTaskHelper.Cycle(() -> { calls.incrementAndGet(); return true; }, 100, 10, 3, 2);
        assertTrue(cycle.tick(90, 2, true));
        assertTrue(cycle.tick(99, 2, true));
        assertTrue(cycle.tick(100, 2, false));
        assertEquals(0, calls.get());
        assertTrue(cycle.tick(100, 2, true));
        assertEquals(1, calls.get());
        assertFalse(cycle.tick(120, 2, true));
        assertEquals(3, calls.get());
        var stale = new CycleTaskHelper.Cycle(() -> fail("past-world task executed"), 0, 0, -1, 2);
        assertFalse(stale.tick(180, 3, true));
    }
}
