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

    private Pipeline(String name, boolean shortCircuit,
                     List<ThrowingFn<T, T>> beforeEach,
                     List<ThrowingFn<T, T>> steps,
                     List<ThrowingFn<T, T>> afterEach) {
        this.name = name;
        this.shortCircuit = shortCircuit;
        this.beforeEach = List.copyOf(beforeEach);
        this.steps = List.copyOf(steps);
        this.afterEach = List.copyOf(afterEach);
    }

    @SafeVarargs
    public static <T> Pipeline<T> build(String name, boolean shortCircuit, ThrowingFn<T, T>... steps) {
        return new Builder<T>(name).shortCircuit(shortCircuit).steps(steps).build();
    }

    public static final class Builder<T> {
        private final String name;
        private boolean shortCircuit = true;
        private final List<ThrowingFn<T, T>> steps = new ArrayList<>();
        private final List<ThrowingFn<T, T>> beforeEach = new ArrayList<>();
        private final List<ThrowingFn<T, T>> afterEach = new ArrayList<>();

        public Builder(String name) { this.name = name; }

        public Builder<T> shortCircuit(boolean b) { this.shortCircuit = b; return this; }
        public Builder<T> beforeEach(ThrowingFn<T, T> pre) { this.beforeEach.add(pre); return this; }
        public Builder<T> step(ThrowingFn<T, T> s) { this.steps.add(s); return this; }
        @SafeVarargs public final Builder<T> steps(ThrowingFn<T, T>... ss) { this.steps.addAll(Arrays.asList(ss)); return this; }
        public Builder<T> afterEach(ThrowingFn<T, T> post) { this.afterEach.add(post); return this; }
        public Pipeline<T> build() { return new Pipeline<>(name, shortCircuit, beforeEach, steps, afterEach); }
    }

    public T run(T input) {
        var rec = Metrics.recorder();
        T cur = input;

        // pre
        for (int i = 0; i < beforeEach.size(); i++) {
            var fn = beforeEach.get(i);
            var stepName = "pre" + i;
            try {
                long t0 = System.nanoTime();
                cur = fn.apply(cur);
                rec.onStepSuccess(name, stepName, System.nanoTime() - t0);
            } catch (ShortCircuit.Signal sc) {
                rec.onShortCircuit(name, stepName);
                @SuppressWarnings("unchecked") T v = (T) sc.value;
                return v;
            } catch (Exception ex) {
                rec.onStepError(name, stepName, ex);
                if (shortCircuit) return cur; // last good value
                // else skip
            }
        }

        // main
        for (int i = 0; i < steps.size(); i++) {
            var fn = steps.get(i);
            var stepName = "s" + i;
            try {
                long t0 = System.nanoTime();
                cur = fn.apply(cur);
                rec.onStepSuccess(name, stepName, System.nanoTime() - t0);
            } catch (ShortCircuit.Signal sc) {
                rec.onShortCircuit(name, stepName);
                @SuppressWarnings("unchecked") T v = (T) sc.value;
                return v;
            } catch (Exception ex) {
                rec.onStepError(name, stepName, ex);
                if (shortCircuit) {
                    log.debug("short-circuit '{}' at {}", name, stepName, ex);
                    return cur; // last good
                }
            }
        }

        // post
        for (int i = 0; i < afterEach.size(); i++) {
            var fn = afterEach.get(i);
            var stepName = "post" + i;
            try {
                long t0 = System.nanoTime();
                cur = fn.apply(cur);
                rec.onStepSuccess(name, stepName, System.nanoTime() - t0);
            } catch (ShortCircuit.Signal sc) {
                rec.onShortCircuit(name, stepName);
                @SuppressWarnings("unchecked") T v = (T) sc.value;
                return v;
            } catch (Exception ex) {
                rec.onStepError(name, stepName, ex);
                if (shortCircuit) return cur;
            }
        }
        return cur;
    }

    public String name() { return name; }
    public boolean shortCircuit() { return shortCircuit; }
    public int size() { return steps.size(); }
}
