package com.pipeline.examples.disruptor;

public final class Alerts {
  public static final class Enriched {
    public final String symbol;
    public final double price;
    public final double lastPrice;
    public final double pctChange;
    public Enriched(String symbol, double price, double lastPrice) {
      this.symbol = symbol; this.price = price; this.lastPrice = lastPrice;
      this.pctChange = lastPrice == 0.0 ? 0.0 : (price - lastPrice) / lastPrice;
    }
    @Override public String toString(){ return "Enriched[" + symbol + " p=" + price + " last=" + lastPrice + " d=" + (pctChange*100) + "%]"; }
  }
  public static final class Alert {
    public final String symbol;
    public final String level;
    public final String message;
    public Alert(String symbol, String level, String message) {
      this.symbol = symbol; this.level = level; this.message = message;
    }
    @Override public String toString(){ return "Alert[" + level + " " + symbol + " " + message + "]"; }
  }
}
