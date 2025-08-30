package com.pipeline.core;

import com.pipeline.metrics.Metrics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

public final class Pipe<I, O> {
    private static final Logger log = LoggerFactory.getLogger(Pipe.class);

    private final List<ThrowingFn<?, ?>> steps;
    private final boolean shortCircuit;
    private final java.util.function.Function<Exception, O> onErrorReturn;
    private final String name;

    private Pipe(String name, boolean shortCircuit, java.util.function.Function<Exception, O> onErrorReturn,
                 List<ThrowingFn<?, ?>> steps) {
        this.name = name;
        this.shortCircuit = shortCircuit;
        this.onErrorReturn = onErrorReturn;
        this.steps = List.copyOf(steps);
    }

    public static <I> Builder<I, I> from(Class<I> inType) { return new Builder<>("pipe"); }
    public static <I> Builder<I, I> named(String name) { return new Builder<>(name); }

    public static final class Builder<I, C> {
        private final String name;
        private boolean shortCircuit = true;
        private final List<ThrowingFn<?, ?>> steps = new ArrayList<>();
        private java.util.function.Function<Exception, ?> onErrorReturn;

        private Builder(String name) { this.name = name; }

        public Builder<I, C> shortCircuit(boolean b) { this.shortCircuit = b; return this; }
        public <X> Builder<I, C> onErrorReturn(java.util.function.Function<Exception, X> f) { this.onErrorReturn = f; return this; }
        public <M> Builder<I, M> step(ThrowingFn<C, M> f) { steps.add(f); return (Builder<I, M>) this; }
        public <O> Pipe<I, O> to(Class<O> out) { return new Pipe<>(name, shortCircuit, (java.util.function.Function<Exception,O>) onErrorReturn, steps); }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    public O run(I in) throws Exception {
        var rec = Metrics.recorder();
        Object cur = in;
        for (int i = 0; i < steps.size(); i++) {
            var fn = (ThrowingFn) steps.get(i);
            var stepName = "s" + i;
            try {
                long t0 = System.nanoTime();
                cur = fn.apply(cur);
                rec.onStepSuccess(name, stepName, System.nanoTime() - t0);
            } catch (ShortCircuit.Signal sc) {
                rec.onShortCircuit(name, stepName);
                return (O) sc.value;
            } catch (Exception ex) {
                rec.onStepError(name, stepName, ex);
                if (shortCircuit) {
                    if (onErrorReturn != null) return onErrorReturn.apply(ex);
                    throw ex;
                }
                // continue: keep current value
            }
        }
        return (O) cur;
    }
}
