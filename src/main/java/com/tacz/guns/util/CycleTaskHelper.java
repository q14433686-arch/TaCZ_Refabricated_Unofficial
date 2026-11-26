package com.tacz.guns.util;

import com.tacz.guns.util.time.GunTime;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;

/** Ticked on the server thread. Lua may schedule another cycle from inside a callback. */
public final class CycleTaskHelper {
    private static final List<Cycle> tasks = new ArrayList<>();
    private CycleTaskHelper() {}

    public static void addCycleTask(BooleanSupplier task, long periodMs, int cycles) {
        addCycleTask(task, 0, periodMs, cycles);
    }

    public static void addCycleTask(BooleanSupplier task, long delayMs, long periodMs, int cycles) {
        long now = GunTime.nowMillis();
        var cycle = new Cycle(task, now + Math.max(0, delayMs), periodMs, cycles, GunTime.epoch());
        if (cycle.tick(now, GunTime.epoch(), GunTime.shouldTick())) tasks.add(cycle);
    }

    public static void tick() {
        if (tasks.isEmpty()) return;
        var current = List.copyOf(tasks);
        tasks.clear();
        long now = GunTime.nowMillis();
        for (var cycle : current) {
            if (cycle.tick(now, GunTime.epoch(), GunTime.shouldTick())) tasks.add(cycle);
        }
    }

    public static void clear() { tasks.clear(); }

    static final class Cycle {
        final BooleanSupplier action;
        final long period, epoch;
        long due;
        int remaining;

        Cycle(BooleanSupplier action, long due, long period, int cycles, long epoch) {
            this.action = action;
            this.due = due;
            this.period = Math.max(1, period);
            this.remaining = cycles;
            this.epoch = epoch;
        }

        boolean tick(long now, long currentEpoch, boolean running) {
            if (epoch != currentEpoch || remaining == 0 || remaining < -1) return false;
            if (!running) return true;
            // Keep sub-tick periods, but do not replay an unbounded backlog on a stalled tick.
            for (int n = 0; due <= now; n++) {
                if (!action.getAsBoolean() || remaining > 0 && --remaining == 0) return false;
                due += period;
                if (n == 63 && due <= now) { due = now + period; break; }
            }
            return true;
        }
    }
}
