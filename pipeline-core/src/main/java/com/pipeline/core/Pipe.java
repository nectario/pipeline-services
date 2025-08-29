package com.pipeline.core;

import com.pipeline.metrics.Metrics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

public class Pipe<I, O> {
    private Pipe() {}

    public static <I> Builder<I> from(Class<I> inType) { return new Builder<>(); }

    public static final class Builder<I> {
        private static final Logger log = LoggerFactory.getLogger(Builder.class);

        private boolean shortCircuit = true;
        private final List<ThrowingFn<?, ?>> steps = new ArrayList<>();
        private Function<Exception, ?> onErrorReturn;

        public Builder<I> shortCircuit(boolean b) { this.shortCircuit = b; return this; }

        public <O> Builder<I> onErrorReturn(Function<Exception, O> f) {
            this.onErrorReturn = f; return this;
        }

        @SuppressWarnings("unchecked")
        public <M> Builder<M> step(ThrowingFn<?, ?> s) { // type-bending but safe at runtime order
            steps.add(s);
            return (Builder<M>) this;
        }

        public <O> Pipe<I, O> to(Class<O> outType) { return new Runner<>(steps, shortCircuit, onErrorReturn); }
    }

    public O run(I in) throws Exception { throw new UnsupportedOperationException("Not implemented"); }

    static final class Runner<I, O> extends Pipe<I, O> {
        private final List<ThrowingFn<?, ?>> steps;
        private final boolean shortCircuit;
        private final Function<Exception, ?> onErrorReturn;

        Runner(List<ThrowingFn<?, ?>> steps, boolean shortCircuit, Function<Exception, ?> onErrorReturn) {
            this.steps = List.copyOf(steps);
            this.shortCircuit = shortCircuit;
            this.onErrorReturn = onErrorReturn;
        }

        @SuppressWarnings({"unchecked", "rawtypes"})
        public O run(I in) throws Exception {
            Object current = in;
            int idx = 0;
            try {
                for (ThrowingFn step : steps) {
                    long start = System.nanoTime();
                    try {
                        current = step.apply(current);
                        Metrics.recorder().onStepSuccess("pipe", "s" + idx, System.nanoTime() - start);
                    } catch (ShortCircuit.Signal s) {
                        Metrics.recorder().onShortCircuit("pipe", "s" + idx);
                        return (O) s.value;
                    } catch (Exception e) {
                        Metrics.recorder().onStepError("pipe", "s" + idx, e);
                        if (shortCircuit) {
                            Metrics.recorder().onShortCircuit("pipe", "s" + idx);
                            if (onErrorReturn != null) {
                                return (O) onErrorReturn.apply(e);
                            }
                            throw e;
                        }
                        // keep current and continue
                    }
                    idx++;
                }
            } catch (ShortCircuit.Signal s) {
                Metrics.recorder().onShortCircuit("pipe", "s" + idx);
                return (O) s.value;
            }
            return (O) current;
        }
    }
}
