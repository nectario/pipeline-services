package com.pipeline.examples;

import com.pipeline.core.Pipe;
import com.pipeline.examples.steps.ArraySteps;

public final class Example08_IntArrayStats {
  private Example08_IntArrayStats() {}

  public static void run() throws Exception {
    Pipe pipe = Pipe.<int[]>named("ex08")
        .step(ArraySteps::clipNegatives)
        .step(ArraySteps::stats)
        .to(ArraySteps.Stats.class);

    int[] in = new int[] { 5, -2, 10, 3 };
    var stats = pipe.run(in);
    System.out.println("[ex08] => " + stats);
  }
}
