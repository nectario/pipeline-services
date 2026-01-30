package com.pipeline.examples;

import com.pipeline.core.Pipeline;
import com.pipeline.examples.steps.TextSteps;

public final class Example02ShortCircuitOnException {
  private Example02ShortCircuitOnException() {}

  public static void run() {
    Pipeline<String> pipeline = new Pipeline<String>("ex02", /*shortCircuitOnException=*/true)
        .addAction(TextSteps::disallowEmoji)
        .addAction(TextSteps::upper);

    String input = "Hello 🌟"; // contains an emoji to trigger exception
    String outputValue = pipeline.run(input).context();
    System.out.println("[ex02] in='" + input + "' out='" + outputValue + "'");
  }
}
