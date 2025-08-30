package com.pipeline.examples;

import com.pipeline.core.Pipeline;
import com.pipeline.examples.steps.PolicySteps;
import com.pipeline.examples.steps.TextSteps;

public final class Example06PrePostPolicies {
  private Example06PrePostPolicies() {}

  public static void run() {
    var p = new Pipeline.Builder<String>("ex06")
        .beforeEach(PolicySteps::rateLimit)
        .step(TextSteps::strip)
        .afterEach(PolicySteps::audit)
        .build();

    String out = p.run("   hi   ");
    System.out.println("[ex06] => '" + out + "'");
  }
}

