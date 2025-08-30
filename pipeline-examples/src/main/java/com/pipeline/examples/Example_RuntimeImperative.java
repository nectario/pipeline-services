package com.pipeline.examples;

import com.pipeline.core.RuntimePipeline;
import com.pipeline.examples.steps.TextSteps;
import com.pipeline.examples.steps.PolicySteps;

public final class Example_RuntimeImperative {
  public static void run() {
    var rt = new RuntimePipeline<>("adhoc_clean", /*shortCircuit=*/false, "  Hello   World  ");
    rt.addPreAction(PolicySteps::rateLimit);
    rt.addStep(TextSteps::strip);
    rt.addStep(TextSteps::normalizeWhitespace);
    rt.addPostAction(PolicySteps::audit);
    System.out.println("[adhoc_clean] -> " + rt.value());

    // If you later want a reusable immutable pipeline out of the same steps:
    // Pipeline<String> p = rt.toImmutable(PolicySteps::rateLimit, TextSteps::strip,
    //                                     TextSteps::normalizeWhitespace, PolicySteps::audit);
    // System.out.println(p.run("  Another    Input "));
  }
}

