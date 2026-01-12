package com.pipeline.examples;

import com.pipeline.core.RuntimePipeline;
import com.pipeline.examples.steps.TextSteps;
import com.pipeline.examples.steps.PolicySteps;

public final class ExampleRuntimeImperative {
  public static void run() {
    var rt = new RuntimePipeline<>("adhoc_clean", /*shortCircuit=*/false, "  Hello   World  ");
    rt.addPreAction(PolicySteps::rateLimit);
    rt.addAction(TextSteps::strip);
    rt.addAction(TextSteps::normalizeWhitespace);
    rt.addPostAction(PolicySteps::audit);
    System.out.println("[adhoc_clean] -> " + rt.value());

    // If you later want a reusable immutable pipeline out of the recorded steps:
    // Pipeline<String> p = rt.freeze(); // or rt.toImmutable()
    // System.out.println(p.run("  Another    Input "));
  }
}
