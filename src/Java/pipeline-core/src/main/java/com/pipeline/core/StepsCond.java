package com.pipeline.core;

import java.time.Duration;

/**
 * Predicate-based helpers for conditional jumps without changing the core addAction API.
 * Only two entry points:
 *  - jumpIf(label, pred)                  -> immediate jump when predicate is true
 *  - jumpIf(label, pred, Duration delay) -> delayed (or immediate when delay == null/0)
 */
public final class StepsCond {
    private StepsCond(){}

    /** If pred is true, jump to label immediately. Value flows through unchanged. */
    public static <T> ThrowingFn<T,T> jumpIf(String label, ThrowingPred<? super T> pred) {
        return t -> { if (pred.test(t)) com.pipeline.core.Jumps.now(label); return t; };
    }

    /** If pred is true, jump to label after delay (or immediately when delay is null/0). */
    public static <T> ThrowingFn<T,T> jumpIf(String label, ThrowingPred<? super T> pred, Duration delay) {
        return t -> {
            if (pred.test(t)) {
                long ms = (delay == null) ? 0L : Math.max(0L, delay.toMillis());
                if (ms > 0) com.pipeline.core.Jumps.after(label, delay);
                else com.pipeline.core.Jumps.now(label);
            }
            return t;
        };
    }
}
