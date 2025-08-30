package com.pipeline.examples.steps;

import java.util.concurrent.atomic.AtomicLong;

public final class PolicySteps {
  private static final AtomicLong LAST_NS = new AtomicLong(0);
  private static final long MIN_GAP_NS = 5_000_000; // ~200 qps demo throttle

  private PolicySteps() {}
  /** Simple local rate-limiter: drops to previous value if too soon. */
  public static String rateLimit(String s) throws Exception {
    long now = System.nanoTime();
    long last = LAST_NS.get();
    if (now - last < MIN_GAP_NS) return s; // pass through; real impl could ShortCircuit.now(...)
    LAST_NS.set(now);
    return s;
  }
  /** Minimal audit example (no-op; could log/metric). */
  public static String audit(String s) throws Exception { return s; }
}

