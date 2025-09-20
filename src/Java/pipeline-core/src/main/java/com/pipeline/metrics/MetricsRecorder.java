package com.pipeline.metrics;

import io.micrometer.core.instrument.MeterRegistry;

public interface MetricsRecorder {
    void onStepSuccess(String pipeline, String stepName, long nanos);
    void onStepError(String pipeline, String stepName, Throwable t);
    void onShortCircuit(String pipeline, String stepName);
    MeterRegistry registry();
}

