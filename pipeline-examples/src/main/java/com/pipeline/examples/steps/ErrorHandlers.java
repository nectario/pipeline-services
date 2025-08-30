package com.pipeline.examples.steps;

public final class ErrorHandlers {
  private ErrorHandlers() {}
  public static QuoteSteps.Res quoteError(Exception e) {
    return new QuoteSteps.Rejected("PricingError: " + e.getMessage());
  }
}

