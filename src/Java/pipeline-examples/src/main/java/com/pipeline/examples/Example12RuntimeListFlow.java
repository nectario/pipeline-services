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
    rt.addAction(ListSteps::nonEmptyOrShortCircuit);
    rt.addAction(ListSteps::dedup);
    rt.addAction(ListSteps::sortNatural);
    System.out.println("[ex12-runtime-1] -> " + rt.value());

    // Second run: reset with empty list triggers an early short-circuit in the first action
    rt.reset(List.of());
    rt.addAction(ListSteps::nonEmptyOrShortCircuit); // stops main actions for this session
    rt.addAction(ListSteps::dedup);                   // ignored after short-circuit
    rt.addAction(ListSteps::sortNatural);
    System.out.println("[ex12-runtime-2] -> " + rt.value());
  }
}

