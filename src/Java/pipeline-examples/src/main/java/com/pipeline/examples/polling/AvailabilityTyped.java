package com.pipeline.examples.polling;

import com.pipeline.core.Jumps;
import com.pipeline.examples.steps.FinanceSteps;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

public final class AvailabilityTyped {
  private static final AtomicInteger attempts = new AtomicInteger();

  /** Demo-only: ready on third attempt; self-loops via label 'await'. */
  public static FinanceSteps.Features awaitFeatures(FinanceSteps.Features f) {
    if (attempts.incrementAndGet() < 3) {
      Jumps.after("await", Duration.ofMillis(1));
    }
    return f;
  }
}
