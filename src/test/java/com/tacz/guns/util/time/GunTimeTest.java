package com.tacz.guns.util.time;

import org.junit.jupiter.api.Test;
import java.util.concurrent.atomic.AtomicLong;
import static org.junit.jupiter.api.Assertions.*;

class GunTimeTest {
    @Test void freezingIsIdempotentAndEpochResetDoesNotRewind() {
        var source = new AtomicLong(-42); // nanoTime origin needn't be positive
        var clock = new GunTime.Clock(source::get);
        long start = clock.now();
        source.addAndGet(7);
        clock.pause(true);
        source.addAndGet(9_000_000_000L);
        clock.pause(true);
        assertEquals(start + 7, clock.now());
        clock.nextEpoch();
        assertEquals(1, clock.generation());
        assertEquals(start + 7, clock.now());
        source.addAndGet(20);
        clock.pause(false);
        assertEquals(start + 27, clock.now());
    }
}
