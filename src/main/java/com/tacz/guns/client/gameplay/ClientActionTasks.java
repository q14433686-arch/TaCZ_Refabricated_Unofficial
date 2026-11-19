package com.tacz.guns.client.gameplay;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;

/** Timers signal the client thread, they never inspect/mutate a player, stack, state machine or connection. */
final class ClientActionTasks {
    // Owned by the client thread. Unregistering a task also invalidates a callback already in its queue.
    private final Set<Task> active = new HashSet<>();

    ScheduledFuture<?> start(ScheduledExecutorService timer, Executor client, long delay, long period, BooleanSupplier step) {
        var task = new Task(client, step);
        active.add(task);
        try {
            task.future = timer.scheduleWithFixedDelay(task::wake, Math.max(0, delay), Math.max(1, period), TimeUnit.MILLISECONDS);
            return task.future;
        } catch (RuntimeException failure) {
            active.remove(task);
            throw failure;
        }
    }

    boolean pending() { return !active.isEmpty(); }
    void cancelAll() { List.copyOf(active).forEach(Task::cancel); }

    private final class Task {
        final Executor client;
        final BooleanSupplier step;
        final AtomicBoolean queued = new AtomicBoolean();
        ScheduledFuture<?> future;

        Task(Executor client, BooleanSupplier step) { this.client = client; this.step = step; }
        void wake() {
            if (!queued.compareAndSet(false, true)) return;
            client.execute(() -> {
                try {
                    if (active.contains(this) && !step.getAsBoolean()) cancel();
                } catch (RuntimeException | Error failure) {
                    cancel();
                    throw failure;
                } finally {
                    queued.set(false);
                }
            });
        }
        void cancel() { active.remove(this); future.cancel(false); }
    }
}
