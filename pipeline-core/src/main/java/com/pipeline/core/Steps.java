package com.pipeline.core;

import java.util.function.Function;

public final class Steps {
    private Steps(){}

    /** Unary: swallow errors, pass input through unchanged. */
    public static <T> ThrowingFn<T,T> ignoreErrors(ThrowingFn<T,T> step) {
        return in -> {
            try { return step.apply(in); }
            catch (Exception e) { return in; }
        };
    }

    /** Typed: if step throws, use fallback to produce required O. */
    public static <I,O> ThrowingFn<I,O> withFallback(ThrowingFn<? super I, ? extends O> step,
                                                     Function<? super Exception, ? extends O> fallback) {
        return in -> {
            try { return step.apply(in); }
            catch (Exception e) { return fallback.apply(e); }
        };
    }
}

