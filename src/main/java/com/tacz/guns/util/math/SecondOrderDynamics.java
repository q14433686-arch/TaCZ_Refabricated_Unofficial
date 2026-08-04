package com.tacz.guns.util.math;

/**
 * A small second-order response used to smooth first-person animation values.
 *
 * <p>The simulation is advanced lazily by the render thread. Older versions created one
 * permanently-running scheduled-executor task per instance. Besides waking several threads every
 * six milliseconds while a gun was held, changing perspective kept creating additional tasks and
 * caused steadily worsening frame times. Those tasks also remained visible during client shutdown.
 * Keeping the integrator here makes its cost proportional to frames that actually consume it and
 * leaves no background lifecycle to clean up.</p>
 */
public class SecondOrderDynamics {
    private static final long STEP_NANOS = 6_000_000L;
    private static final float INTEGRATION_STEP = 0.05f;

    /**
     * Do not replay an unbounded number of simulation steps after pausing, minimizing, or a
     * breakpoint. A long catch-up would itself cause a visible frame spike.
     */
    private static final int MAX_CATCH_UP_STEPS = 32;

    private final float k1;
    private final float k2;
    private final float k3;

    private float py;
    private float pyd;
    private float px;
    private float target;
    private long lastUpdateNanos;
    private boolean stopped;

    /**
     * @param f  natural frequency
     * @param z  damping coefficient
     * @param r  initial velocity response
     * @param x0 initial position
     */
    public SecondOrderDynamics(float f, float z, float r, float x0) {
        k1 = (float) (z / (Math.PI * f));
        k2 = (float) (1 / ((2 * Math.PI * f) * (2 * Math.PI * f)));
        k3 = (float) (r * z / (2 * Math.PI * f));

        py = px = x0;
        pyd = 0;
        target = x0;
        lastUpdateNanos = System.nanoTime();
    }

    /**
     * Changes the target and returns the current smoothed value.
     */
    public synchronized float update(float x) {
        // Account for the time since the previous frame using the target that was active during
        // that interval. The new target will be integrated from this point onward.
        advance(System.nanoTime());
        target = x;
        return value();
    }

    public synchronized float get() {
        advance(System.nanoTime());
        return value();
    }

    /**
     * Freezes this response. Retained for source compatibility with callers of the old worker-based
     * implementation; no executor or thread needs to be shut down anymore.
     */
    public synchronized void stop() {
        stopped = true;
    }

    private void advance(long nowNanos) {
        if (stopped) {
            lastUpdateNanos = nowNanos;
            return;
        }

        long elapsed = nowNanos - lastUpdateNanos;
        if (elapsed < STEP_NANOS) {
            // Also handles a theoretical nanoTime wraparound without running backwards.
            if (elapsed < 0) {
                lastUpdateNanos = nowNanos;
            }
            return;
        }

        int steps = (int) Math.min(elapsed / STEP_NANOS, MAX_CATCH_UP_STEPS);
        // Preserve the sub-step remainder during normal rendering, but discard a large backlog.
        if (elapsed / STEP_NANOS > MAX_CATCH_UP_STEPS) {
            lastUpdateNanos = nowNanos;
        } else {
            lastUpdateNanos += steps * STEP_NANOS;
        }

        for (int i = 0; i < steps; i++) {
            step();
        }
    }

    private void step() {
        sanitize();

        float xd = (target - px) / INTEGRATION_STEP;
        float y = py + INTEGRATION_STEP * pyd;
        pyd = pyd + INTEGRATION_STEP * (px + k3 * xd - py - k1 * pyd) / k2;
        px = target;
        py = y;
    }

    private float value() {
        sanitize();
        return py + INTEGRATION_STEP * pyd;
    }

    private void sanitize() {
        // Preserve the original protection against malformed animation data poisoning the state.
        if (Float.isNaN(py)) {
            py = 0;
        }
        if (Float.isNaN(pyd)) {
            pyd = 0;
        }
    }
}
