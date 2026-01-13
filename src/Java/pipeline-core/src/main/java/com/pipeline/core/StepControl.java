package com.pipeline.core;

import java.util.List;

public interface StepControl<C> {
  void shortCircuit();
  boolean isShortCircuited();

  /**
   * Records an error against the current step and returns a context that may be updated by the pipeline's
   * error handler (useful for immutable contexts).
   */
  C recordError(C ctx, Exception exception);

  List<PipelineError> errors();

  /** Pipeline name for this run (best-effort; may be empty for custom controls). */
  default String pipelineName() { return ""; }

  /** Run start timestamp in nanos from the pipeline's clock (best-effort; may be 0). */
  default long runStartNanos() { return 0L; }

  /** Per-action timing captured by the pipeline runtime (best-effort; may be empty). */
  default List<ActionTiming> actionTimings() { return List.of(); }
}
