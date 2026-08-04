package com.tacz.guns.util;

import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Factory for the remaining TaCZ client background workers.
 *
 * <p>Client-only delayed work must not keep the JVM alive after Minecraft has finished shutting
 * down. Minecraft 26.2's shutdown watchdog writes a crash report if a non-daemon worker prevents
 * process exit, so these disposable presentation tasks always use daemon threads. Persistent
 * per-animation workers are deliberately not created: {@code SecondOrderDynamics} is advanced on
 * demand by the render thread.</p>
 */
public final class TaczThreads {
    private TaczThreads() {
    }

    /**
     * Creates daemon threads with stable diagnostic names.
     *
     * @param poolName thread-name prefix, producing names such as {@code tacz-gun-scheduler-1}
     */
    public static ThreadFactory daemonFactory(String poolName) {
        AtomicInteger counter = new AtomicInteger(1);
        return runnable -> {
            Thread thread = new Thread(runnable, poolName + "-" + counter.getAndIncrement());
            thread.setDaemon(true);
            // Avoid inheriting an elevated priority from a render or reload thread.
            thread.setPriority(Thread.NORM_PRIORITY);
            return thread;
        };
    }
}
