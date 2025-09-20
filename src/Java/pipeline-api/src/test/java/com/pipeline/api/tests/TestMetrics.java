package com.pipeline.api.tests;

import com.pipeline.core.metrics.Metrics;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public final class TestMetrics implements Metrics {
  public final List<String> events = new ArrayList<>();
  public final AtomicInteger jumps = new AtomicInteger();

  @Override
  public RunScope onPipelineStart(String name, String runId, String startLabel) {
    events.add("pipeline.start:" + name + ":" + startLabel);
    long t0 = System.nanoTime();
    return new RunScope() {
      @Override public void onStepStart(int idx, String label) { events.add("step.start:" + idx + ":" + label); }
      @Override public void onStepEnd(int idx, String label, long nanos, boolean success) { events.add("step.end:" + idx + ":" + label + ":" + success); }
      @Override public void onStepError(int idx, String label, Throwable error) { events.add("step.error:" + idx + ":" + label + ":" + error.getClass().getSimpleName()); }
      @Override public void onJump(String from, String to, long delayMs) { jumps.incrementAndGet(); events.add("jump:" + from + "->" + to); }
      @Override public void onPipelineEnd(boolean success, long nanos, Throwable error) { events.add("pipeline.end:" + success); }
    };
  }
}
