package com.pipeline.core;

import java.util.List;
import java.util.Objects;
import java.util.function.BiFunction;

record PipelinePlan<C>(
    String pipelineName,
    boolean shortCircuitOnException,
    BiFunction<C, PipelineError, C> errorHandler,
    PipelineObserver observer,
    List<RegisteredAction<C>> preActions,
    List<RegisteredAction<C>> actions,
    List<RegisteredAction<C>> postActions) {

  PipelinePlan {
    pipelineName = Objects.requireNonNull(pipelineName, "pipelineName");
    errorHandler = Objects.requireNonNull(errorHandler, "errorHandler");
    observer = Objects.requireNonNull(observer, "observer");
    preActions = List.copyOf(Objects.requireNonNull(preActions, "preActions"));
    actions = List.copyOf(Objects.requireNonNull(actions, "actions"));
    postActions = List.copyOf(Objects.requireNonNull(postActions, "postActions"));
  }
}
