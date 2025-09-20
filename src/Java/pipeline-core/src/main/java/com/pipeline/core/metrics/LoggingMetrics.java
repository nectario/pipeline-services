package com.pipeline.core.metrics;

import java.time.Duration;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Simple out-of-the-box metrics/logger using java.util.logging. */
public final class LoggingMetrics implements Metrics {
  private final Logger log;
  private final Level level;

  public LoggingMetrics() { this(Logger.getLogger("Pipeline"), Level.INFO); }
  public LoggingMetrics(Logger log) { this(log, Level.INFO); }
  public LoggingMetrics(Logger log, Level level) { this.log = log; this.level = level; }

  @Override public RunScope onPipelineStart(String pipelineName, String runId, String startLabel) {
    long t0 = System.nanoTime();
    if (log.isLoggable(level)) {
      log.log(level, () -> String.format("pipeline.start name=%s runId=%s startLabel=%s", pipelineName, runId, startLabel));
    }
    return new RunScope() {
      @Override public void onStepStart(int idx, String label) {
        if (log.isLoggable(level)) log.log(level, () -> String.format("step.start runId=%s idx=%d label=%s", runId, idx, label));
      }
      @Override public void onStepEnd(int idx, String label, long nanos, boolean success) {
        if (log.isLoggable(level)) log.log(level, () -> String.format("step.end runId=%s idx=%d label=%s durMs=%.3f success=%s",
            runId, idx, label, nanos/1_000_000.0, success));
      }
      @Override public void onStepError(int idx, String label, Throwable error) {
        log.log(Level.WARNING, String.format("step.error runId=%s idx=%d label=%s", runId, idx, label), error);
      }
      @Override public void onJump(String from, String to, long delayMs) {
        if (log.isLoggable(level)) log.log(level, () -> String.format("step.jump runId=%s from=%s to=%s delayMs=%d", runId, from, to, delayMs));
      }
      @Override public void onPipelineEnd(boolean success, long nanos, Throwable error) {
        if (error == null) {
          if (log.isLoggable(level)) log.log(level, () -> String.format("pipeline.end name=%s runId=%s durMs=%.3f success=%s",
              pipelineName, runId, nanos/1_000_000.0, success));
        } else {
          log.log(Level.WARNING, String.format("pipeline.end name=%s runId=%s durMs=%.3f success=%s",
              pipelineName, runId, nanos/1_000_000.0, success), error);
        }
      }
    };
  }
}
