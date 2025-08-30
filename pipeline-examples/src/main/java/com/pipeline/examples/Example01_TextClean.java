package com.pipeline.examples;

import com.pipeline.core.Pipeline;
import com.pipeline.examples.steps.TextSteps;

public final class Example01_TextClean {
  private Example01_TextClean() {}

  public static void run() {
    var p = new Pipeline.Builder<String>("ex01")
        .shortCircuit(false)
        .step(TextSteps::strip)
        .step(TextSteps::normalizeWhitespace)
        .step(TextSteps::truncateAt280)
        .build();

    String input = "  Hello   <b>World</b>  ";
    String out = p.run(input);
    System.out.println("[ex01] => " + out);
  }
}

