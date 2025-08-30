package com.pipeline.core;

import com.pipeline.metrics.Metrics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

public final class Pipe<I, O> {
    private static final Logger log = LoggerFactory.getLogger(Pipe.class);

    private final List<ThrowingFn<?, ?>> steps;
    private final boolean shortCircuit;
    private final Function<Exception, O> onErrorReturn; // may be null
    private final String name;

    private Pipe(String name,
                 boolean shortCircuit,
                 Function<Exception, O> onErrorReturn,
                 List<ThrowingFn<?, ?>> steps) {
        this.name = name;
        this.shortCircuit = shortCircuit;
        this.onErrorReturn = onErrorReturn;
        this.steps = List.copyOf(steps);
    }

    /** Start a typed pipeline builder; current type == input type initially. */
    public static <I> Builder<I, I> from(Class<I> inType) { return new Builder<>("pipe"); }

    /** Start with a friendly name. */
    public static <I> Builder<I, I> named(String name) { return new Builder<>(name); }

    /** Builder tracks both the original input type I and the current type C. */
    public static final class Builder<I, C> {
        private final String name;
        private boolean shortCircuit = true;
        private final List<ThrowingFn<?, ?>> steps = new ArrayList<>();
        private Function<Exception, ?> onErrorReturn;

        private Builder(String name) { this.name = name; }

        public Builder<I, C> shortCircuit(boolean b) { this.shortCircuit = b; return this; }

        /** You can set this before `to(...)`; it's cast to the final O there. */
        public Builder<I, C> onErrorReturn(Function<Exception, ?> f) { this.onErrorReturn = f; return this; }

        /** Add a step that transforms current type C to a new type M. */
        @SuppressWarnings("unchecked")
        public <M> Builder<I, M> step(ThrowingFn<? super C, ? extends M> f) {
            steps.add((ThrowingFn<?, ?>) f);
            return (Builder<I, M>) this;
        }

        /** Finish: produce a Pipe<I,O> from original input I to desired output O. */
        @SuppressWarnings("unchecked")
        public <O> Pipe<I, O> to(Class<O> outType) {
            return new Pipe<>(name, shortCircuit, (Function<Exception, O>) onErrorReturn, steps);
        }
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
                // shortCircuit=false -> keep current value and continue
            }
        }
        return (O) cur;
    }
}
