package com.pipeline.core;

import java.util.Objects;

public record ActionTiming(
    StepPhase phase,
    int index,
    String actionName,
    long elapsedNanos,
    boolean success
) {
  public ActionTiming {
    phase = Objects.requireNonNull(phase, "phase");
    actionName = Objects.requireNonNull(actionName, "actionName");
  }
}

