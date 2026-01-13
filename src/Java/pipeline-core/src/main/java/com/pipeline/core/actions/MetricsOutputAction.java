package com.pipeline.core.actions;

import com.pipeline.core.ActionTiming;
import com.pipeline.core.StepAction;
import com.pipeline.core.StepControl;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.LongSupplier;

/**
 * Minimal out-of-the-box metrics as a post action (UBS-style):
 * measure per-action timings and emit a single metrics map to a sink.
 */
public final class MetricsOutputAction<C> implements StepAction<C> {
  private final String name;
  private final LongSupplier nanoClock;
  private final Consumer<Map<String, Object>> sink;

  public MetricsOutputAction() {
    this("Metrics", System::nanoTime, System.out::println);
  }

  public MetricsOutputAction(Consumer<Map<String, Object>> sink) {
    this("Metrics", System::nanoTime, sink);
  }

  public MetricsOutputAction(String name, Consumer<Map<String, Object>> sink) {
    this(name, System::nanoTime, sink);
  }

  public MetricsOutputAction(String name, LongSupplier nanoClock, Consumer<Map<String, Object>> sink) {
    this.name = Objects.requireNonNull(name, "name");
    this.nanoClock = Objects.requireNonNull(nanoClock, "nanoClock");
    this.sink = Objects.requireNonNull(sink, "sink");
  }

  @Override
  public C apply(C ctx, StepControl<C> control) {
    long nowNanos = nanoClock.getAsLong();
    long startNanos = control.runStartNanos();
    double pipelineLatencyMs = (startNanos == 0L) ? 0.0 : (nowNanos - startNanos) / 1_000_000.0;

    Map<String, Object> metricsMap = new LinkedHashMap<>();
    metricsMap.put("name", name);
    metricsMap.put("pipeline", control.pipelineName());
    metricsMap.put("shortCircuited", control.isShortCircuited());
    metricsMap.put("errorCount", control.errors().size());
    metricsMap.put("pipelineLatencyMs", pipelineLatencyMs);

    Map<String, Double> actionLatencyMs = new LinkedHashMap<>();
    for (ActionTiming timing : control.actionTimings()) {
      actionLatencyMs.put(timing.actionName(), timing.elapsedNanos() / 1_000_000.0);
    }
    metricsMap.put("actionLatencyMs", actionLatencyMs);

    sink.accept(metricsMap);
    return ctx;
  }
}

