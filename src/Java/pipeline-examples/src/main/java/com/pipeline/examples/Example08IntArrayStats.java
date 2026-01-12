package com.pipeline.examples;

import com.pipeline.api.Pipeline;
import com.pipeline.examples.steps.ArraySteps;

public final class Example08IntArrayStats {
  private Example08IntArrayStats() {}

  public static void run() throws Exception {
    var pipe = Pipeline.<int[]>named("ex08", /*shortCircuit=*/true)
        .addAction(ArraySteps::clipNegatives)
        .addAction(ArraySteps::stats);

    int[] in = new int[] { 5, -2, 10, 3 };
    var stats = pipe.run(in, ArraySteps.Stats.class);
    System.out.println("[ex08] => " + stats);
  }
}
