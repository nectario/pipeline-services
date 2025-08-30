package com.pipeline.examples;

import com.pipeline.core.Pipeline;
import com.pipeline.disruptor.DisruptorEngine;
import com.pipeline.examples.steps.TextSteps;

public final class Example10DisruptorIntegration {
  private Example10DisruptorIntegration() {}

  public static void run() throws Exception {
    var p = new Pipeline.Builder<String>("ex10-clean")
        .shortCircuit(false)
        .step(TextSteps::strip)
        .step(TextSteps::normalizeWhitespace)
        .step(TextSteps::truncateAt280)
        .build();

    try (DisruptorEngine<String> engine = new DisruptorEngine<>("ex10", 1024, p)) {
      for (int i = 0; i < 50; i++) {
        engine.publish(" hello  " + i + "  ");
      }
      Thread.sleep(200);
    }
    System.out.println("[ex10] disruptor processed ~50 messages");
  }
}

