package com.pipeline.core;

import java.util.List;
import java.util.Objects;

public record PipelineResult<C>(
    C context,
    boolean shortCircuited,
    List<PipelineError> errors,
    List<ActionTiming> actionTimings,
    long totalNanos
) {
  public PipelineResult {
    context = Objects.requireNonNull(context, "context");
    errors = List.copyOf(Objects.requireNonNull(errors, "errors"));
    actionTimings = List.copyOf(Objects.requireNonNull(actionTimings, "actionTimings"));
  }

  public boolean hasErrors() {
    return !errors.isEmpty();
  }
}
