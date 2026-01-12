package com.pipeline.core;

import java.util.Objects;

public record PipelineError(
    String pipelineName,
    StepPhase phase,
    int stepIndex,
    String stepName,
    Exception exception
) {
  public PipelineError {
    pipelineName = Objects.requireNonNull(pipelineName, "pipelineName");
    phase = Objects.requireNonNull(phase, "phase");
    if (stepIndex < 0) throw new IllegalArgumentException("stepIndex must be >= 0");
    stepName = Objects.requireNonNull(stepName, "stepName");
    exception = Objects.requireNonNull(exception, "exception");
  }
}

