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
}

