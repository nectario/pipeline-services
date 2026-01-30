package com.pipeline.examples;

import com.pipeline.core.Pipeline;
import com.pipeline.examples.steps.ListSteps;

import java.util.Arrays;
import java.util.List;

public final class Example07ListDedupSort {
  private Example07ListDedupSort() {}

  public static void run() {
    Pipeline<List<String>> pipeline = new Pipeline<List<String>>("ex07", /*shortCircuitOnException=*/true)
        .addAction(ListSteps::nonEmptyOrShortCircuit)
        .addAction(ListSteps::dedup)
        .addAction(ListSteps::sortNatural);

    List<String> outputValue = pipeline.run(Arrays.asList("orange", "apple", "orange")).context();
    System.out.println("[ex07] => " + outputValue);
  }
}
