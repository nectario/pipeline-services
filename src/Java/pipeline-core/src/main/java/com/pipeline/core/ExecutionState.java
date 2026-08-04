package com.pipeline.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

final class ExecutionState<C> {
  private final PipelinePlan<C> plan;
  private final boolean collectTimings;
  private final long runStartNanos;
  private final List<PipelineError> errors = new ArrayList<>();
  private final List<ActionTiming> actionTimings;

  private C context;
  private boolean shortCircuited;
  private boolean actionExecuting;
  private StepPhase phase = StepPhase.MAIN;
  private int actionIndex;
  private String actionName = "?";

  ExecutionState(
      PipelinePlan<C> plan,
      C input,
      boolean collectTimings,
      long runStartNanos) {
    this.plan = Objects.requireNonNull(plan, "plan");
    this.context = Objects.requireNonNull(input, "input");
    this.collectTimings = collectTimings;
    this.runStartNanos = runStartNanos;
    this.actionTimings = collectTimings ? new ArrayList<>() : List.of();
  }

  PipelinePlan<C> plan() {
    return plan;
  }

  C context() {
    return context;
  }

  void context(C context) {
    this.context = Objects.requireNonNull(context, "context");
  }

  boolean isShortCircuited() {
    return shortCircuited;
  }

  boolean requestShortCircuit() {
    if (shortCircuited) return false;
    shortCircuited = true;
    return true;
  }

  void beginAction(StepPhase phase, int actionIndex, String actionName) {
    this.phase = Objects.requireNonNull(phase, "phase");
    this.actionIndex = actionIndex;
    this.actionName = Objects.requireNonNull(actionName, "actionName");
  }

  void beginActionExecution() {
    if (actionExecuting) {
      throw new IllegalStateException("A Pipeline Action is already executing for this run");
    }
    actionExecuting = true;
  }

  void endActionExecution() {
    if (!actionExecuting) {
      throw new IllegalStateException("No Pipeline Action is executing for this run");
    }
    actionExecuting = false;
  }

  boolean isActionExecuting() {
    return actionExecuting;
  }

  C recordError(C currentContext, Exception exception) {
    PipelineError pipelineError = new PipelineError(
        plan.pipelineName(),
        phase,
        actionIndex,
        actionName,
        Objects.requireNonNull(exception, "exception"));
    errors.add(pipelineError);

    C updatedContext = currentContext;
    try {
      updatedContext = Objects.requireNonNull(
          plan.errorHandler().apply(currentContext, pipelineError),
          "onError returned null");
    } catch (RuntimeException handlerFailure) {
      errors.add(new PipelineError(
          plan.pipelineName(),
          phase,
          actionIndex,
          actionName,
          handlerFailure));
      requestShortCircuit();
    }

    context(updatedContext);
    return updatedContext;
  }

  void recordTiming(long elapsedNanos, boolean success) {
    if (!collectTimings) return;
    actionTimings.add(new ActionTiming(
        phase,
        actionIndex,
        actionName,
        elapsedNanos,
        success));
  }

  List<PipelineError> errors() {
    return List.copyOf(errors);
  }

  List<ActionTiming> actionTimings() {
    return List.copyOf(actionTimings);
  }

  long runStartNanos() {
    return runStartNanos;
  }
}
