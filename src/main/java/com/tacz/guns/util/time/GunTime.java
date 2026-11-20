package com.tacz.guns.util.time;

import java.util.function.LongSupplier;

/** Elapsed gameplay time, never a wall-clock date. Dedicated servers have no client pause hook. */
public final class GunTime {
    private static final Clock CLOCK = new Clock(System::nanoTime);
    private GunTime() {}

    public static long nowNanos() { return CLOCK.now(); }
    public static long nowMillis() { return nowNanos() / 1_000_000L; }
    public static boolean shouldTick() { return !CLOCK.paused; }
    public static long epoch() { return CLOCK.generation(); }
    public static void setPaused(boolean value) { CLOCK.pause(value); }
    public static void resetEpoch() { CLOCK.nextEpoch(); }
    public static boolean isWithin(long timestamp, long durationMillis) {
        long elapsed = nowMillis() - timestamp;
        return timestamp >= 0 && elapsed >= 0 && elapsed < durationMillis;
    }
    public static boolean hasElapsed(long timestamp, long durationMillis) {
        return timestamp >= 0 && nowMillis() - timestamp >= durationMillis;
    }

    static final class Clock {
        private final LongSupplier nanos;
        private final long origin;
        private long pauseStarted, pausedTime;
        private volatile boolean paused;
        private volatile long epoch;

        Clock(LongSupplier nanos) { this.nanos = nanos; origin = nanos.getAsLong(); }
        long generation() { return epoch; }
        synchronized long now() {
            // Keep the legacy -1 sentinel outside short action windows, even at startup.
            return 100_000_000_000L + (paused ? pauseStarted : nanos.getAsLong()) - origin - pausedTime;
        }
        synchronized void pause(boolean value) {
            if (value == paused) return;
            long raw = nanos.getAsLong();
            if (value) pauseStarted = raw;
            else pausedTime += raw - pauseStarted;
            paused = value;
        }
        synchronized void nextEpoch() {
            pause(false);
            // Invalidate pending work without moving time backwards under the integrated server.
            epoch++;
        }
    }
}
