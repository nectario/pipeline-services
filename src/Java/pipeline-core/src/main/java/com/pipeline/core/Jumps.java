package com.pipeline.core;

import java.time.Duration;

/**
 * Lightweight control signal to jump to a labeled step.
 * Throw using Jumps.now("label") or Jumps.after("label", Duration).
 * The engine catches Signal and resumes execution at the target label.
 */
public final class Jumps {
  private Jumps() {}

  public static final class Signal extends RuntimeException {
    private final String label;
    private final long sleepMillis;

    public Signal(String label, long sleepMillis) {
      super(null, null, false, false); // no stacktrace for speed
      this.label = label;
      this.sleepMillis = sleepMillis;
    }

    public String label() { return label; }
    public long sleepMillis() { return sleepMillis; }
  }

  /** Jump immediately to a labeled step. */
  public static void now(String label) {
    if (label == null || label.isBlank()) throw new IllegalArgumentException("label must be non-empty");
    throw new Signal(label, 0L);
  }

  /** Jump to a labeled step after a delay. */
  public static void after(String label, Duration d) {
    if (label == null || label.isBlank()) throw new IllegalArgumentException("label must be non-empty");
    long ms = d == null ? 0L : Math.max(0L, d.toMillis());
    throw new Signal(label, ms);
  }
}
