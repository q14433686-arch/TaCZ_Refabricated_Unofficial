package com.tacz.guns.client.resource;

import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class DisplayBakeTest {
    @Test void selectsReadyDetailWithoutJoiningOrBakingTwice() {
        var queue = new ArrayDeque<Runnable>();
        var active = new AtomicBoolean(true);
        var high = new DisplayBake<>(queue::add, active::get, () -> "high", e -> fail(e));
        var low = new DisplayBake<>(queue::add, active::get, () -> "low", e -> fail(e));
        assertNull(high.preferOver(low));
        assertNull(high.preferOver(low));
        assertEquals(1, queue.size());
        queue.remove().run();
        assertEquals("high", low.preferOver(high)); // low still pending, don't block the collector
        queue.remove().run();
        assertEquals("low", low.preferOver(high));
        active.set(false);
        assertNull(high.preferOver(low));
        assertNull(low.loadNow());
        assertTrue(queue.isEmpty());
    }

    @Test void failedOrAbsentDetailFallsBackOnceAndStaleQueuedWorkDoesntRun() {
        var queue = new ArrayDeque<Runnable>();
        var errors = new AtomicInteger();
        var active = new AtomicBoolean(true);
        var bad = new DisplayBake<String>(queue::add, active::get, () -> { throw new IllegalArgumentException(); }, e -> errors.incrementAndGet());
        var fallback = new DisplayBake<>(queue::add, active::get, () -> "fallback", e -> fail(e));
        var stale = new DisplayBake<String>(queue::add, active::get, () -> fail("outdated bake ran"), e -> fail(e));
        assertFalse(bad.completedEmpty());
        assertNull(bad.preferOver(fallback));
        assertFalse(bad.completedEmpty()); // pending is not missing
        queue.remove().run();
        assertTrue(bad.completedEmpty());
        assertNull(bad.preferOver(fallback));
        assertEquals("fallback", fallback.loadNow()); // synchronous request can claim a queued task
        queue.remove().run();
        assertEquals("fallback", bad.preferOver(fallback));
        assertEquals(1, errors.get());
        assertFalse(fallback.completedEmpty());
        stale.request();
        active.set(false);
        queue.remove().run();
        assertNull(stale.ready());
        assertFalse(bad.completedEmpty()); // retired display
    }
}
