package com.pipeline.core;

import java.util.Objects;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

public final class ActionPool<T> {
    private final ArrayBlockingQueue<T> available;
    private final AtomicInteger createdCount = new AtomicInteger(0);
    private final int max;
    private final Supplier<? extends T> factory;

    public ActionPool(int max, Supplier<? extends T> factory) {
        if (max < 1) throw new IllegalArgumentException("max must be >= 1");
        this.max = max;
        this.factory = Objects.requireNonNull(factory, "factory");
        this.available = new ArrayBlockingQueue<>(max);
    }

    public int max() {
        return max;
    }

    public int createdCount() {
        return createdCount.get();
    }

    public T borrow() {
        while (true) {
            T fromQueue = available.poll();
            if (fromQueue != null) {
                return fromQueue;
            }

            int createdSoFar = createdCount.get();
            if (createdSoFar < max) {
                if (!createdCount.compareAndSet(createdSoFar, createdSoFar + 1)) {
                    continue;
                }
                return factory.get();
            }

            try {
                return available.take();
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Interrupted while waiting for an action instance from the pool");
            }
        }
    }

    public void release(T instance) {
        if (instance == null) return;
        available.offer(instance);
    }
}
