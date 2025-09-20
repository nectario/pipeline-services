package com.pipeline.examples;

import com.pipeline.examples.bloomberg.BloombergRFQPipeline;
import com.pipeline.examples.bloomberg.BloombergSession;
import com.pipeline.examples.steps.FinanceSteps;
import com.pipeline.examples.steps.FinanceSteps.*;

public final class ExampleBloombergRFQPipeline {
  private ExampleBloombergRFQPipeline() {}

  public static void run() throws Exception {
    try (var p = new BloombergRFQPipeline(new BloombergSession("prod"))) {
      // Append JSON that targets @this instance methods before first run
      p.addPipelineConfig("pipeline-examples/src/main/resources/pipelines/bloomberg_rfq.json");
      OrderResponse out = p.run(new Tick("AAPL", 101.25), OrderResponse.class);
      System.out.println("[bloomberg-rfq] -> " + out);
    }
  }
}
