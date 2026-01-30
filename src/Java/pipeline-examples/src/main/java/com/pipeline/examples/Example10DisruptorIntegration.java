package com.pipeline.examples;

import com.pipeline.core.Pipeline;
import com.pipeline.disruptor.DisruptorEngine;
import com.pipeline.examples.steps.TextSteps;

public final class Example10DisruptorIntegration {
  private Example10DisruptorIntegration() {}

  public static void run() throws Exception {
    Pipeline<String> pipeline = new Pipeline<String>("ex10-clean", /*shortCircuitOnException=*/false)
        .addAction(TextSteps::strip)
        .addAction(TextSteps::normalizeWhitespace)
        .addAction(TextSteps::truncateAt280);

    try (DisruptorEngine<String> engine = new DisruptorEngine<>("ex10", 1024, pipeline)) {
      for (int i = 0; i < 50; i++) {
        engine.publish(" hello  " + i + "  ");
      }
      Thread.sleep(200);
    }
    System.out.println("[ex10] disruptor processed ~50 messages");
  }
}
