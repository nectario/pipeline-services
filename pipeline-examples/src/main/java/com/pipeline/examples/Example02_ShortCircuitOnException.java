package com.pipeline.examples;

import com.pipeline.core.Pipeline;
import com.pipeline.examples.steps.TextSteps;

public final class Example02_ShortCircuitOnException {
  private Example02_ShortCircuitOnException() {}

  public static void run() {
    var p = new Pipeline.Builder<String>("ex02")
        .shortCircuit(true)
        .step(TextSteps::disallowEmoji)
        .step(TextSteps::upper)
        .build();

    String input = "Hello 🌟"; // contains an emoji to trigger exception
    String out = p.run(input);
    System.out.println("[ex02] in='" + input + "' out='" + out + "'");
  }
}

