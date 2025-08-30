package com.pipeline.examples;

import com.pipeline.core.Pipeline;
import com.pipeline.core.RuntimePipeline;
import com.pipeline.examples.steps.PolicySteps;
import com.pipeline.examples.steps.TextSteps;

public final class Example13RuntimeResetAndFreeze {
  private Example13RuntimeResetAndFreeze() {}

  public static void run() {
    // Build incrementally at runtime
    var rt = new RuntimePipeline<>("adhoc_session", /*shortCircuit=*/false, "   First   Input   ");
    rt.addPreAction(PolicySteps::rateLimit);
    rt.addStep(TextSteps::strip);
    rt.addStep(TextSteps::normalizeWhitespace);
    rt.addPostAction(PolicySteps::audit);
    System.out.println("[ex13-runtime] session1 -> " + rt.value());

    // Start another session with a different input
    rt.reset("   Second     Input   ");
    rt.addStep(TextSteps::truncateAt280); // may short-circuit if very long
    System.out.println("[ex13-runtime] session2 -> " + rt.value());

    // Freeze the recorded steps (pre/main/post) into a reusable immutable Pipeline
    Pipeline<String> immutable = rt.toImmutable();
    String out = immutable.run("  Reusable   pipeline   input  ");
    System.out.println("[ex13-runtime] frozen -> " + out);
  }
}

