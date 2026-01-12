package com.pipeline.examples;

import com.pipeline.api.Pipeline;
import com.pipeline.examples.steps.FinanceSteps;

public final class Example04FinanceOrderFlow {
  private Example04FinanceOrderFlow() {}

  public static void run() throws Exception {
    Pipeline<FinanceSteps.Tick, FinanceSteps.OrderResponse> pipe =
        Pipeline.<FinanceSteps.Tick>named("ex04", /*shortCircuit=*/true)
            .addAction(FinanceSteps::computeFeatures)
            .addAction(FinanceSteps::score)
            .addAction(FinanceSteps::decide);

    var tick = new FinanceSteps.Tick("AAPL", 30.0);
    var res = pipe.run(tick, FinanceSteps.OrderResponse.class);
    System.out.println("[ex04] => " + res);
  }
}
