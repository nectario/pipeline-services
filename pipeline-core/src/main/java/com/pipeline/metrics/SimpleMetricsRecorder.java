package com.pipeline.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

import java.util.concurrent.TimeUnit;

public final class SimpleMetricsRecorder implements MetricsRecorder {
    private final MeterRegistry registry;

    public SimpleMetricsRecorder() {
        this.registry = new SimpleMeterRegistry();
    }

    public SimpleMetricsRecorder(MeterRegistry registry) {
        this.registry = registry;
    }

    @Override
    public void onStepSuccess(String pipeline, String stepName, long nanos) {
        Timer.builder(metric(pipeline, stepName, "duration"))
                .register(registry)
                .record(nanos, TimeUnit.NANOSECONDS);
    }

    @Override
    public void onStepError(String pipeline, String stepName, Throwable t) {
        Counter.builder(metric(pipeline, stepName, "errors")).register(registry).increment();
    }

    @Override
    public void onShortCircuit(String pipeline, String stepName) {
        Counter.builder(metric(pipeline, stepName, "short_circuits")).register(registry).increment();
    }

    @Override
    public MeterRegistry registry() {
        return registry;
    }

    private static String metric(String pipeline, String step, String name) {
        return "ps.pipeline." + pipeline + ".step." + step + "." + name;
    }
}

