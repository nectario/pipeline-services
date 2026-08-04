package com.pipeline.examples;

import com.pipeline.core.Pipeline;
import com.pipeline.core.RuntimePipeline;
import com.pipeline.examples.steps.PolicySteps;
import com.pipeline.examples.steps.TextSteps;

public final class Example13RuntimeResetAndFreeze {
  private Example13RuntimeResetAndFreeze() {}

  public static void run() {
    RuntimePipeline<String> runtimePipeline = new RuntimePipeline<>(
        "adhoc_session",
        /*shortCircuit=*/false,
        "   First   Input   ");
    runtimePipeline.addPreAction(PolicySteps::rateLimit);
    runtimePipeline.addAction(TextSteps::strip);
    runtimePipeline.addAction(TextSteps::normalizeWhitespace);
    runtimePipeline.addPostAction(PolicySteps::audit);
    System.out.println("[ex13-runtime] session1 -> " + runtimePipeline.value());

    runtimePipeline.reset("   Second     Input   ");
    runtimePipeline.addAction(TextSteps::truncateAt280);
    System.out.println("[ex13-runtime] session2 -> " + runtimePipeline.value());

    Pipeline<String> immutablePipeline = runtimePipeline.toImmutable();
    String outputValue = immutablePipeline.run("  Reusable   pipeline   input  ");
    System.out.println("[ex13-runtime] frozen -> " + outputValue);
  }
}
