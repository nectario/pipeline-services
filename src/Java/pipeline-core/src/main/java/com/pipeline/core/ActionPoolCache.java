package com.pipeline.core;

import java.util.Objects;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Provider-scoped cache for pooling action instances across different pipeline instances.
 *
 * <p>This is intentionally seed-based: the cache does not instantiate actions. Pipelines contribute
 * instances (seeds) and pooled wrappers borrow/release them per invocation.
 */
public final class ActionPoolCache {
    private final ConcurrentMap<ActionCacheKey, ActionPoolEntry> entries;
    private final int maxPerAction;

    public ActionPoolCache() {
        this(defaultPoolMax());
    }

    public ActionPoolCache(int maxPerAction) {
        if (maxPerAction < 1) throw new IllegalArgumentException("maxPerAction must be >= 1");
        this.maxPerAction = maxPerAction;
        this.entries = new ConcurrentHashMap<>();
    }

    public int maxPerAction() {
        return maxPerAction;
    }

    ActionPoolEntry entry(ActionCacheKey key, Class<?> actionClass, ActionInvokeStyle invokeStyle) {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(actionClass, "actionClass");
        Objects.requireNonNull(invokeStyle, "invokeStyle");

        return entries.compute(key, (existingKey, existingEntry) -> {
            if (existingEntry == null) {
                return new ActionPoolEntry(actionClass, invokeStyle, new SeededPool<>(maxPerAction));
            }
            if (!existingEntry.actionClass().equals(actionClass)) {
                throw new IllegalStateException(
                    "Action key collision for '" + key + "'. Existing class=" + existingEntry.actionClass().getName()
                        + " new class=" + actionClass.getName());
            }
            if (existingEntry.invokeStyle() != invokeStyle) {
                throw new IllegalStateException(
                    "Action invoke-style collision for '" + key + "'. Existing=" + existingEntry.invokeStyle()
                        + " new=" + invokeStyle);
            }
            return existingEntry;
        });
    }

    private static int defaultPoolMax() {
        int processors = Runtime.getRuntime().availableProcessors();
        int computed = processors * 8;
        return Math.min(256, Math.max(1, computed));
    }

    static final class ActionPoolEntry {
        private final Class<?> actionClass;
        private final ActionInvokeStyle invokeStyle;
        private final SeededPool<Object> pool;

        private ActionPoolEntry(Class<?> actionClass, ActionInvokeStyle invokeStyle, SeededPool<Object> pool) {
            this.actionClass = actionClass;
            this.invokeStyle = invokeStyle;
            this.pool = pool;
        }

        Class<?> actionClass() {
            return actionClass;
        }

        ActionInvokeStyle invokeStyle() {
            return invokeStyle;
        }

        SeededPool<Object> pool() {
            return pool;
        }
    }

    static final class SeededPool<T> {
        private final ArrayBlockingQueue<T> available;

        SeededPool(int max) {
            this.available = new ArrayBlockingQueue<>(max);
        }

        boolean trySeed(T instance) {
            if (instance == null) return false;
            return available.offer(instance);
        }

        T borrow() {
            T fromQueue = available.poll();
            if (fromQueue != null) return fromQueue;
            try {
                return available.take();
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Interrupted while waiting for an action instance from the pool");
            }
        }

        void release(T instance) {
            if (instance == null) return;
            available.offer(instance);
        }
    }
}

