package com.tacz.guns.client.resource;

import org.jetbrains.annotations.Nullable;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** One CPU bake for one display revision. Asking from an item render must never join the loader. */
final class DisplayBake<T> {
    private final Executor executor;
    private final BooleanSupplier current;
    private final FutureTask<T> task;
    private final AtomicBoolean requested = new AtomicBoolean();

    DisplayBake(Executor executor, BooleanSupplier current, Supplier<T> loader, Consumer<RuntimeException> onError) {
        this.executor = executor;
        this.current = current;
        this.task = new FutureTask<>(() -> {
            if (!current.getAsBoolean()) return null;
            try {
                return loader.get();
            } catch (RuntimeException e) {
                if (current.getAsBoolean()) onError.accept(e);
                return null;
            }
        });
    }

    void request() {
        if (current.getAsBoolean() && requested.compareAndSet(false, true)) executor.execute(task);
    }

    @Nullable T ready() {
        if (!current.getAsBoolean()) return null;
        return switch (task.state()) {
            case SUCCESS -> task.resultNow();
            case FAILED -> throw new IllegalStateException("Display bake failed", task.exceptionNow());
            default -> null;
        };
    }

    /** While the requested detail is baking keep drawing the other one if it already exists. */
    @Nullable T preferOver(DisplayBake<T> alternate) {
        request();
        T value = ready();
        if (value != null) return value;
        if (task.state() == Future.State.SUCCESS) alternate.request(); // absent or unsupported at requested detail
        return alternate.ready();
    }

    /** Compatibility for explicit blocking asset access, not used by frame submission. */
    @Nullable T loadNow() {
        if (!current.getAsBoolean()) return null;
        requested.set(true);
        task.run(); // also works if queued behind this code on the single asset worker
        try {
            var result = task.get();
            return current.getAsBoolean() ? result : null;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        } catch (ExecutionException e) {
            throw new IllegalStateException("Display bake failed", e.getCause());
        }
    }
}
