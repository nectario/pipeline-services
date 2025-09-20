package com.pipeline.examples.disruptor;

/** Mutable Disruptor event. */
public final class MarketDataEvent {
  public String symbol;
  public double price;
  public long   tsNanos;

  public void set(String symbol, double price, long tsNanos) {
    this.symbol = symbol;
    this.price = price;
    this.tsNanos = tsNanos;
  }
}
