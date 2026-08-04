package com.pipeline.core;

import java.util.Objects;

public record ActionTiming(
    StepPhase phase,
    int actionIndex,
    String actionName,
    long elapsedNanos,
    boolean success
) {
  public ActionTiming {
    phase = Objects.requireNonNull(phase, "phase");
    if (actionIndex < 0) throw new IllegalArgumentException("actionIndex must be >= 0");
    actionName = Objects.requireNonNull(actionName, "actionName");
    if (elapsedNanos < 0L) throw new IllegalArgumentException("elapsedNanos must be >= 0");
  }
}
