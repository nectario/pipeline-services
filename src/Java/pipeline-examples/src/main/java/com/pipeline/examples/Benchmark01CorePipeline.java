package com.pipeline.examples;

import com.pipeline.core.ActionTiming;
import com.pipeline.core.Pipeline;
import com.pipeline.core.PipelineResult;
import com.pipeline.examples.steps.TextSteps;

import java.util.LinkedHashMap;
import java.util.Map;

public final class Benchmark01CorePipeline {
  private Benchmark01CorePipeline() {}

  public static void main(String[] args) {
    int warmupIterations = 1_000;
    int iterations = 10_000;
    String inputValue = "  Hello Benchmark  ";

    Pipeline<String> pipeline = new Pipeline<String>("benchmark01_core_pipeline", true)
        .addAction(TextSteps::strip)
        .addAction(TextSteps::upper)
        .addAction(value -> value + "|");

    for (int warmupIndex = 0; warmupIndex < warmupIterations; warmupIndex++) {
      pipeline.run(inputValue);
    }

    long totalPipelineNanos = 0L;
    Map<String, Long> actionNanosTotals = new LinkedHashMap<>();
    Map<String, Integer> actionCounts = new LinkedHashMap<>();

    long wallStartNanos = System.nanoTime();
    for (int iterationIndex = 0; iterationIndex < iterations; iterationIndex++) {
      PipelineResult<String> result = pipeline.execute(inputValue);
      totalPipelineNanos += result.totalNanos();

      for (ActionTiming timing : result.actionTimings()) {
        actionNanosTotals.merge(timing.actionName(), timing.elapsedNanos(), Long::sum);
        actionCounts.merge(timing.actionName(), 1, Integer::sum);
      }
    }
    long wallEndNanos = System.nanoTime();

    long wallNanos = wallEndNanos - wallStartNanos;
    System.out.println("iterations=" + iterations);
    System.out.println("wallMs=" + (wallNanos / 1_000_000.0));
    System.out.println("avgPipelineUs=" + ((totalPipelineNanos / (double) iterations) / 1_000.0));

    System.out.println("avgActionUs=");
    for (Map.Entry<String, Long> entry : actionNanosTotals.entrySet()) {
      String actionName = entry.getKey();
      long nanosTotal = entry.getValue();
      int count = actionCounts.getOrDefault(actionName, 1);
      double avgUs = (nanosTotal / (double) count) / 1_000.0;
      System.out.println("  " + actionName + "=" + avgUs);
    }
  }
}
