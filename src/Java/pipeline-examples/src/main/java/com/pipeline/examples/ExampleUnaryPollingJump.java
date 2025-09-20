package com.pipeline.examples;

import com.pipeline.api.Pipeline;
import com.pipeline.examples.polling.Availability;
import com.pipeline.examples.steps.TextSteps;

public final class ExampleUnaryPollingJump {
  private ExampleUnaryPollingJump() {}

  public static void run() throws Exception {
    var p = com.pipeline.api.Pipeline.<String,String>named("await_job", false)
        .enableJumps(true)
        .sleeper(ms -> {})                          // no real sleep in example
        .addAction("await", Availability::awaitJob) // self-loop
        .addAction(TextSteps::normalizeWhitespace);

    System.out.println("[unary-poll] -> " + p.run("  job-123  "));
  }
}
