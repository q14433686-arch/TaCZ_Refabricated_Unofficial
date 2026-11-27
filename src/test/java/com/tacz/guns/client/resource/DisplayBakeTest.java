package com.tacz.guns.client.resource;

import org.junit.jupiter.api.Test;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class DisplayBakeTest {
    @Test void pendingRequestsNeverJoinAndOnlyScheduleOnce() {
        var queue = new ArrayDeque<Runnable>();
        var loads = new AtomicInteger();
        var bake = new DisplayBake<>(queue::add, () -> true, loads::incrementAndGet, e -> fail(e));
        assertTimeoutPreemptively(Duration.ofSeconds(1), () -> {
            bake.request(); bake.request();
            assertNull(bake.ready());
            assertFalse(bake.completedEmpty());
        });
        assertEquals(1, queue.size()); assertEquals(0, loads.get());
        queue.remove().run(); assertEquals(1, bake.ready());
    }

    @Test void pendingDetailUsesAlreadyReadyAlternateWithoutStartingAnotherLoad() {
        var queue = new ArrayDeque<Runnable>();
        var high = new DisplayBake<>(queue::add, () -> true, () -> "high", e -> fail(e));
        var lod = new DisplayBake<>(queue::add, () -> true, () -> "lod", e -> fail(e));
        assertEquals("high", high.loadNow());
        assertEquals("high", lod.preferOver(high));
        assertEquals(1, queue.size());
        queue.remove().run(); assertEquals("lod", lod.preferOver(high));
    }

    @Test void missingDetailSchedulesAlternateAndObsoleteResultsAreNotSelectable() {
        var current = new AtomicBoolean(true);
        var queue = new ArrayDeque<Runnable>();
        var lod = new DisplayBake<String>(queue::add, current::get, () -> null, e -> fail(e));
        var high = new DisplayBake<>(queue::add, current::get, () -> "high", e -> fail(e));
        assertNull(lod.preferOver(high)); queue.remove().run();
        assertTrue(lod.completedEmpty()); assertNull(lod.preferOver(high));
        queue.remove().run(); assertEquals("high", lod.preferOver(high));
        current.set(false);
        assertNull(high.ready()); assertNull(high.loadNow()); assertFalse(lod.completedEmpty());
    }

    @Test void revisionChangeDuringBakeCannotExposeOldResult() {
        var current = new AtomicBoolean(true);
        var bake = new DisplayBake<>(Runnable::run, current::get, () -> {
            current.set(false); return "obsolete";
        }, e -> fail(e));
        bake.request(); assertNull(bake.ready()); assertNull(bake.loadNow());
    }

    @Test void explicitBlockingAccessCanRunItsOwnQueuedTaskWithoutDeadlock() {
        var queue = new ArrayDeque<Runnable>();
        var loads = new AtomicInteger();
        var bake = new DisplayBake<>(queue::add, () -> true, loads::incrementAndGet, e -> fail(e));
        bake.request();
        assertTimeoutPreemptively(Duration.ofSeconds(1), () -> assertEquals(1, bake.loadNow()));
        queue.remove().run(); assertEquals(1, loads.get());
    }
}
