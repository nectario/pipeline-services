package com.pipeline.examples;

import com.pipeline.core.RuntimePipeline;
import com.pipeline.examples.steps.PolicySteps;
import com.pipeline.examples.steps.TextSteps;

public final class Example11RuntimeTextClean {
  private Example11RuntimeTextClean() {}

  public static void run() {
    var rt = new RuntimePipeline<>("runtime_text_clean", /*shortCircuit=*/false, "  Hello   World  ");
    rt.addPreAction(PolicySteps::rateLimit);
    rt.addStep(TextSteps::strip);
    rt.addStep(TextSteps::normalizeWhitespace);
    rt.addStep(TextSteps::truncateAt280);  // explicit ShortCircuit inside;
    rt.addPostAction(PolicySteps::audit);

    System.out.println("[ex11-runtime] -> " + rt.value());
  }
}

