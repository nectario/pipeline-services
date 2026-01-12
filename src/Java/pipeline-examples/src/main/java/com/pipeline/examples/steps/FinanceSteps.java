package com.pipeline.examples.steps;

public final class FinanceSteps {
  private FinanceSteps() {}
  // domain records
  public record Tick(String symbol, double price) {}
  public record Features(double r1, double vol) {}
  public record Score(double value) {}
  public sealed interface OrderResponse permits Accept, Reject {}
  public record Accept(String symbol, int qty, double price) implements OrderResponse {}
  public record Reject(String reason) implements OrderResponse {}

  public static Features computeFeatures(Tick t) {
    double r1 = 0.0; double vol = Math.abs(t.price()) * 0.01;
    return new Features(r1, vol);
  }
  public static Score score(Features f) { return new Score(Math.max(0, 1.0 - f.vol())); }
  public static OrderResponse decide(Score s) {
    return s.value() >= 0.5 ? new Accept("AAPL", 10, 101.25) : new Reject("LowScore");
  }
}
