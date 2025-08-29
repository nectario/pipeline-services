package com.pipeline.metrics;

import java.util.Objects;

public final class Metrics {
    private static volatile MetricsRecorder recorder = new SimpleMetricsRecorder();

    private Metrics() {}

    public static MetricsRecorder recorder() {
        return recorder;
    }

    public static void setRecorder(MetricsRecorder r) {
        recorder = Objects.requireNonNull(r, "recorder");
    }
}

