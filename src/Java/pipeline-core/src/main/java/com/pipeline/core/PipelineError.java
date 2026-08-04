package com.pipeline.core;

import java.util.Objects;

public record PipelineError(
    String pipelineName,
    StepPhase phase,
    int actionIndex,
    String actionName,
    Exception exception
) {
  public PipelineError {
    pipelineName = Objects.requireNonNull(pipelineName, "pipelineName");
    phase = Objects.requireNonNull(phase, "phase");
    if (actionIndex < 0) throw new IllegalArgumentException("actionIndex must be >= 0");
    actionName = Objects.requireNonNull(actionName, "actionName");
    exception = Objects.requireNonNull(exception, "exception");
  }
}
