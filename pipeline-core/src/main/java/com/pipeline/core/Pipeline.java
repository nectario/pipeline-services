package com.pipeline.core;

import com.pipeline.metrics.Metrics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class Pipeline<T> {
    private static final Logger log = LoggerFactory.getLogger(Pipeline.class);

    private final String name;
    private final List<ThrowingFn<T, T>> steps;
    private final boolean shortCircuit;
    private final List<ThrowingFn<T, T>> beforeEach;
    private final List<ThrowingFn<T, T>> afterEach;

    private Pipeline(String name, boolean shortCircuit, List<ThrowingFn<T, T>> steps,
                     List<ThrowingFn<T, T>> beforeEach, List<ThrowingFn<T, T>> afterEach) {
        this.name = name;
        this.steps = List.copyOf(steps);
        this.shortCircuit = shortCircuit;
        this.beforeEach = List.copyOf(beforeEach);
        this.afterEach = List.copyOf(afterEach);
    }

    @SafeVarargs
    public static <T> Pipeline<T> build(String name, boolean shortCircuit, ThrowingFn<T, T>... steps) {
        return new Pipeline<>(name, shortCircuit, Arrays.asList(steps), List.of(), List.of());
    }

    public static <T> Builder<T> builder(String name) {
        return new Builder<>(name);
    }

    public static final class Builder<T> {
        private final String name;
        private boolean shortCircuit = true;
        private final List<ThrowingFn<T, T>> steps = new ArrayList<>();
        private final List<ThrowingFn<T, T>> beforeEach = new ArrayList<>();
        private final List<ThrowingFn<T, T>> afterEach = new ArrayList<>();

        private Builder(String name) { this.name = name; }

        public Builder<T> shortCircuit(boolean b) { this.shortCircuit = b; return this; }

        public Builder<T> beforeEach(ThrowingFn<T, T> pre) { this.beforeEach.add(pre); return this; }

        public Builder<T> step(ThrowingFn<T, T> s) { this.steps.add(s); return this; }

        public Builder<T> afterEach(ThrowingFn<T, T> post) { this.afterEach.add(post); return this; }

        public Pipeline<T> build() { return new Pipeline<>(name, shortCircuit, steps, beforeEach, afterEach); }
    }

    public T run(T input) {
        log.debug("Pipeline {} start", name);
        T current = input;
        int idx = 0;
        try {
            for (ThrowingFn<T, T> step : steps) {
                // beforeEach
                for (ThrowingFn<T, T> pre : beforeEach) {
                    current = exec(idx + ".pre", pre, current);
                }

                current = exec(String.valueOf(idx), step, current);

                // afterEach
                for (ThrowingFn<T, T> post : afterEach) {
                    current = exec(idx + ".post", post, current);
                }
                idx++;
            }
        } catch (ShortCircuit.Signal s) {
            Metrics.recorder().onShortCircuit(name, idxName(idx));
            @SuppressWarnings("unchecked") T v = (T) s.value;
            log.debug("Pipeline {} short-circuit via now()", name);
            return v;
        } catch (Exception e) {
            if (shortCircuit) {
                log.warn("Pipeline {} short-circuited on error: {}", name, e.toString());
                return current; // last good
            } else {
                // continue behavior implemented in exec() path; shouldn't reach here
                log.warn("Pipeline {} unexpected error path", name);
            }
        }
        log.debug("Pipeline {} finish", name);
        return current;
    }

    private T exec(String stepId, ThrowingFn<T, T> fn, T in) throws Exception {
        long start = System.nanoTime();
        try {
            T out = fn.apply(in);
            Metrics.recorder().onStepSuccess(name, idxName(stepId), System.nanoTime() - start);
            return out;
        } catch (ShortCircuit.Signal s) {
            throw s;
        } catch (Exception e) {
            Metrics.recorder().onStepError(name, idxName(stepId), e);
            if (shortCircuit) {
                Metrics.recorder().onShortCircuit(name, idxName(stepId));
                throw e;
            }
            // shortCircuit == false: ignore error and pass through
            return in;
        }
    }

    private static String idxName(int idx) { return "s" + idx; }
    private static String idxName(String raw) { return "s" + raw; }

    public String name() { return name; }
    public boolean shortCircuit() { return shortCircuit; }
    public int size() { return steps.size(); }
}

