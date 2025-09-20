package com.pipeline.examples;

import com.pipeline.core.Pipe;
import com.pipeline.examples.steps.FinanceSteps;

public final class Example04FinanceOrderFlow {
  private Example04FinanceOrderFlow() {}

  public static void run() throws Exception {
    Pipe<FinanceSteps.Tick, FinanceSteps.OrderResponse> pipe =
        Pipe.<FinanceSteps.Tick>named("ex04")
            .step(FinanceSteps::computeFeatures)
            .step(FinanceSteps::score)
            .step(FinanceSteps::decide)
            .to(FinanceSteps.OrderResponse.class);

    var tick = new FinanceSteps.Tick("AAPL", 30.0);
    var res = pipe.run(tick);
    System.out.println("[ex04] => " + res);
  }
}
