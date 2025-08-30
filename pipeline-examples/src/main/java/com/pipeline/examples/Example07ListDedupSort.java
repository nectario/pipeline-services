package com.pipeline.examples;

import com.pipeline.core.Pipeline;
import com.pipeline.examples.steps.ListSteps;

import java.util.Arrays;
import java.util.List;

public final class Example07ListDedupSort {
  private Example07ListDedupSort() {}

  public static void run() {
    var p = new Pipeline.Builder<List<String>>("ex07")
        .step(ListSteps::nonEmptyOrShortCircuit)
        .step(ListSteps::dedup)
        .step(ListSteps::sortNatural)
        .build();

    List<String> out = p.run(Arrays.asList("orange","apple","orange"));
    System.out.println("[ex07] => " + out);
  }
}

