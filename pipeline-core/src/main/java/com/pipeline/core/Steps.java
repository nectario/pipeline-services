package com.pipeline.core;

import java.util.function.Function;

public final class Steps {
    private Steps() {}

    public static <T> ThrowingFn<T, T> ignoreErrors(ThrowingFn<T, T> step) {
        return in -> {
            try { return step.apply(in); } catch (Exception e) { return in; }
        };
    }

    public static <I, O> ThrowingFn<I, O> withFallback(ThrowingFn<I, O> step, Function<Exception, O> fallback) {
        return in -> {
            try { return step.apply(in); } catch (Exception e) { return fallback.apply(e); }
        };
    }
}

