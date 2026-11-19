package com.tacz.guns.client.gameplay;

import org.junit.jupiter.api.Test;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class ClientActionTasksTest {
    @Test void cancellingADrawOrDisconnectAlsoInvalidatesTheAlreadyQueuedWake() throws InterruptedException {
        var queue = new LinkedBlockingQueue<Runnable>();
        var owner = new ClientActionTasks();
        var calls = new AtomicInteger();
        try (var timer = new ScheduledThreadPoolExecutor(1)) {
            for (boolean cancel : new boolean[]{true, false}) {
                var future = owner.start(timer, queue::add, 0, 100_000, () -> { calls.incrementAndGet(); return false; });
                var wake = queue.poll(5, TimeUnit.SECONDS);
                assertNotNull(wake);
                assertTrue(owner.pending());
                if (cancel) owner.cancelAll();
                wake.run();
                assertTrue(future.isCancelled());
                assertFalse(owner.pending());
            }
        }
        assertEquals(1, calls.get());
    }
}
