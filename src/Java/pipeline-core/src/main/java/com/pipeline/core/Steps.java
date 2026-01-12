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

    /** Convert a consumer into a pass-through step (executes side effect, returns input). */
    public static <T> ThrowingFn<T, T> tap(ThrowingConsumer<? super T> consumer) {
        return in -> {
            consumer.accept(in);
            return in;
        };
    }

    /** Bind a second argument to a bi-function to form a unary step. */
    public static <A, B, R> ThrowingFn<A, R> bind(ThrowingBiFn<? super A, ? super B, ? extends R> fn, B arg) {
        return in -> fn.apply(in, arg);
    }

    /** Bind a second argument to a bi-consumer to form a pass-through unary step. */
    public static <A, B> ThrowingFn<A, A> bind(ThrowingBiConsumer<? super A, ? super B> consumer, B arg) {
        return in -> {
            consumer.accept(in, arg);
            return in;
        };
    }
}
