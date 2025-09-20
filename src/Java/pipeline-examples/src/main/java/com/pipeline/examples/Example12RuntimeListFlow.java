package com.pipeline.examples;

import com.pipeline.core.RuntimePipeline;
import com.pipeline.examples.steps.ListSteps;

import java.util.List;

public final class Example12RuntimeListFlow {
  private Example12RuntimeListFlow() {}

  public static void run() {
    // First run: non-empty list flows through all steps
    var rt = new RuntimePipeline<>("runtime_list_flow", /*shortCircuit=*/true,
                                   List.of("orange", "apple", "orange"));
    rt.addStep(ListSteps::nonEmptyOrShortCircuit);
    rt.addStep(ListSteps::dedup);
    rt.addStep(ListSteps::sortNatural);
    System.out.println("[ex12-runtime-1] -> " + rt.value());

    // Second run: reset with empty list triggers an early ShortCircuit in the first step
    rt.reset(List.of());
    rt.addStep(ListSteps::nonEmptyOrShortCircuit); // ShortCircuit.now(...) returns immediately
    rt.addStep(ListSteps::dedup);                   // still safe if called again
    rt.addStep(ListSteps::sortNatural);
    System.out.println("[ex12-runtime-2] -> " + rt.value());
  }
}

