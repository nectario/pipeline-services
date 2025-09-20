package com.pipeline.core.metrics;

/** Minimal metrics sink for Pipeline Services.
 *  Implementations may push to logs, Prometheus, Micrometer, etc.
 */
public interface Metrics {
  /** Called at the start of a pipeline run. */
  RunScope onPipelineStart(String pipelineName, String runId, String startLabel);

  /** A per-run scope for step-level and end-of-run events. Implementations must be thread-safe. */
  interface RunScope {
    void onStepStart(int index, String label);
    void onStepEnd(int index, String label, long elapsedNanos, boolean success);
    void onStepError(int index, String label, Throwable error);
    void onJump(String fromLabel, String toLabel, long delayMillis);
    void onPipelineEnd(boolean success, long elapsedNanos, Throwable error);
  }
}
