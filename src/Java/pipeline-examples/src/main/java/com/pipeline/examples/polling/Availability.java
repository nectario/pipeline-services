package com.pipeline.examples.polling;

import com.pipeline.core.Jumps;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Demo-only readiness checker. */
public final class Availability {
  private static final Map<String,Integer> attempts = new ConcurrentHashMap<>();

  /** Loop until jobId becomes "ready" on the 3rd attempt (demo). */
  public static String awaitJob(String jobId) {
    int n = attempts.merge(jobId, 1, Integer::sum);
    if (n < 3) {
      // re-enter this labeled step after a short delay
      Jumps.after("await", Duration.ofMillis(1));
    }
    return jobId;
  }
}
