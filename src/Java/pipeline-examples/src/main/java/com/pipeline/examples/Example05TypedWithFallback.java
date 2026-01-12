package com.pipeline.examples;

import com.pipeline.api.Pipeline;
import com.pipeline.examples.steps.ErrorHandlers;
import com.pipeline.examples.steps.QuoteSteps;

public final class Example05TypedWithFallback {
  private Example05TypedWithFallback() {}

  public static void run() throws Exception {
    var pipe = Pipeline.<QuoteSteps.Req>named("ex05", /*shortCircuit=*/true)
        .addAction(QuoteSteps::validate)
        .addAction(QuoteSteps::price)
        .onErrorReturn(ErrorHandlers::quoteError);

    var res = pipe.run(new QuoteSteps.Req("FAIL", 10), QuoteSteps.Res.class);
    System.out.println("[ex05] => " + res);
  }
}
