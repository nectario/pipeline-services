package com.pipeline.examples;

import com.pipeline.core.Pipe;
import com.pipeline.examples.steps.ErrorHandlers;
import com.pipeline.examples.steps.QuoteSteps;

public final class Example05_TypedWithFallback {
  private Example05_TypedWithFallback() {}

  public static void run() throws Exception {
    Pipe pipe = Pipe.<QuoteSteps.Req>named("ex05")
        .shortCircuit(true)
        .onErrorReturn(ErrorHandlers::quoteError)
        .step(QuoteSteps::validate)
        .step(QuoteSteps::price)
        .to(QuoteSteps.Res.class);

    var res = pipe.run(new QuoteSteps.Req("FAIL", 10));
    System.out.println("[ex05] => " + res);
  }
}
