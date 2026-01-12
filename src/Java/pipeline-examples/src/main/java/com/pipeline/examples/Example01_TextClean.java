package com.pipeline.examples;

import com.pipeline.core.Pipeline;
import com.pipeline.examples.steps.TextSteps;

public final class Example01_TextClean {
  private Example01_TextClean() {}

  public static void run() {
    var p = new Pipeline<String>("ex01", /*shortCircuitOnException=*/false)
        .addAction(TextSteps::strip)
        .addAction(TextSteps::normalizeWhitespace)
        .addAction(TextSteps::truncateAt280);

    String input = "  Hello   <b>World</b>  ";
    String out = p.run(input);
    System.out.println("[ex01] => " + out);
  }
}
