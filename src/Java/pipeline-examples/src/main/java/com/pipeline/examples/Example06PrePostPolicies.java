package com.pipeline.examples;

import com.pipeline.core.Pipeline;
import com.pipeline.examples.steps.PolicySteps;
import com.pipeline.examples.steps.TextSteps;

public final class Example06PrePostPolicies {
  private Example06PrePostPolicies() {}

  public static void run() {
    Pipeline<String> pipeline = new Pipeline<String>("ex06", /*shortCircuitOnException=*/true)
        .addPreAction(PolicySteps::rateLimit)
        .addAction(TextSteps::strip)
        .addPostAction(PolicySteps::audit);

    String outputValue = pipeline.run("   hi   ").context();
    System.out.println("[ex06] => '" + outputValue + "'");
  }
}
