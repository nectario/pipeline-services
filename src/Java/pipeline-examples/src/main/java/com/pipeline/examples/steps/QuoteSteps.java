package com.pipeline.examples.steps;

public final class QuoteSteps {
  private QuoteSteps() {}
  public record Req(String symbol, int qty) {}
  public sealed interface Res permits Ok, Rejected {}
  public record Ok(double px) implements Res {}
  public record Rejected(String reason) implements Res {}

  public static Req validate(Req r) {
    if (r.qty() <= 0) throw new IllegalArgumentException("qty<=0");
    if (r.symbol() == null || r.symbol().isBlank()) throw new IllegalArgumentException("no symbol");
    return r;
  }
  public static Res price(Req r) {
    if ("FAIL".equalsIgnoreCase(r.symbol())) throw new RuntimeException("pricing backend down");
    return new Ok(101.25);
  }
}
