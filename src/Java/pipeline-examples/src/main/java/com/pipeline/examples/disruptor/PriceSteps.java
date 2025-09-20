package com.pipeline.examples.disruptor;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/** Simple enrichment & alerting steps. */
public final class PriceSteps {
  private static final ConcurrentMap<String, Double> last = new ConcurrentHashMap<>();

  public static Alerts.Enriched enrich(MarketDataEvent e) {
    double prev = last.getOrDefault(e.symbol, 0.0);
    last.put(e.symbol, e.price);
    return new Alerts.Enriched(e.symbol, e.price, prev);
  }

  public static Alerts.Alert alert(Alerts.Enriched en) {
    double abs = Math.abs(en.pctChange);
    String level = abs >= 0.03 ? "HIGH" : (abs >= 0.01 ? "MEDIUM" : "NONE");
    String msg = String.format("px=%.2f last=%.2f change=%.2f%%", en.price, en.lastPrice, en.pctChange*100.0);
    return new Alerts.Alert(en.symbol, level, msg);
  }
}
