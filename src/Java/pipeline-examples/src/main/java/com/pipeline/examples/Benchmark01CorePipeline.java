package com.pipeline.examples;

import com.pipeline.core.ActionTiming;
import com.pipeline.core.Pipeline;
import com.pipeline.core.PipelineResult;
import com.pipeline.examples.steps.TextSteps;

public final class Benchmark01CorePipeline {
  private static volatile int checksumSink;

  private Benchmark01CorePipeline() {}

  public static void main(String[] args) {
    int warmupIterations = 20_000;
    int iterations = 100_000;
    String inputValue = "  Hello Benchmark  ";

    Pipeline<String> pipeline = new Pipeline<String>("benchmark01_core_pipeline", true)
        .addAction(TextSteps::strip)
        .addAction(TextSteps::upper)
        .addAction(value -> value + "|");

    for (int warmupIndex = 0; warmupIndex < warmupIterations; warmupIndex++) {
      checksumSink ^= directRun(inputValue).hashCode();
      checksumSink ^= pipeline.run(inputValue).hashCode();
      checksumSink ^= pipeline.runDetailed(inputValue).context().hashCode();
    }

    Measurement directMeasurement = measure(iterations, () -> directRun(inputValue));
    Measurement runMeasurement = measure(iterations, () -> pipeline.run(inputValue));
    Measurement detailedMeasurement = measure(
        iterations,
        () -> pipeline.runDetailed(inputValue).context());

    PipelineResult<String> detailedSample = pipeline.runDetailed(inputValue);

    System.out.println("benchmark=informational_not_a_ci_threshold");
    System.out.println("iterations=" + iterations);
    System.out.println("directNsPerOp=" + directMeasurement.nanosPerOperation());
    System.out.println("pipelineRunNsPerOp=" + runMeasurement.nanosPerOperation());
    System.out.println("pipelineRunDetailedNsPerOp=" + detailedMeasurement.nanosPerOperation());
    System.out.println("pipelineRunOverDirect=" + ratio(
        runMeasurement.nanosPerOperation(),
        directMeasurement.nanosPerOperation()));
    System.out.println("runDetailedOverRun=" + ratio(
        detailedMeasurement.nanosPerOperation(),
        runMeasurement.nanosPerOperation()));
    System.out.println("checksum=" + checksumSink);

    System.out.println("sampleActionTimingsNs=");
    for (ActionTiming timing : detailedSample.actionTimings()) {
      System.out.println("  " + timing.actionName() + "=" + timing.elapsedNanos());
    }
  }

  private static String directRun(String inputValue) {
    String outputValue = TextSteps.strip(inputValue);
    outputValue = TextSteps.upper(outputValue);
    return outputValue + "|";
  }

  private static Measurement measure(int iterations, Operation operation) {
    int checksum = 0;
    long startNanos = System.nanoTime();
    for (int index = 0; index < iterations; index++) {
      checksum ^= operation.run().hashCode();
    }
    long elapsedNanos = System.nanoTime() - startNanos;
    checksumSink ^= checksum;
    return new Measurement(elapsedNanos / (double) iterations);
  }

  private static double ratio(double numerator, double denominator) {
    return denominator == 0.0 ? 0.0 : numerator / denominator;
  }

  @FunctionalInterface
  private interface Operation {
    String run();
  }

  private record Measurement(double nanosPerOperation) {}
}
