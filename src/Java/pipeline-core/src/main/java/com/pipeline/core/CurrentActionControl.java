package com.pipeline.core;

import java.util.List;

/** Compatibility bridge for the preview control-aware Action shape. */
final class CurrentActionControl<C> implements ActionControl<C> {
  private static final CurrentActionControl<?> INSTANCE = new CurrentActionControl<>();

  private CurrentActionControl() {}

  @SuppressWarnings("unchecked")
  static <C> CurrentActionControl<C> instance() {
    return (CurrentActionControl<C>) INSTANCE;
  }

  private ExecutionState<C> state() {
    return PipelineExecution.currentStateTyped();
  }

  @Override
  public void shortCircuit() {
    PipelineExecution.shortCircuit();
  }

  @Override
  public boolean isShortCircuited() {
    return state().isShortCircuited();
  }

  @Override
  public C recordError(C context, Exception exception) {
    return state().recordError(context, exception);
  }

  @Override
  public List<PipelineError> errors() {
    return state().errors();
  }

  @Override
  public String pipelineName() {
    return state().plan().pipelineName();
  }

  @Override
  public long runStartNanos() {
    return state().runStartNanos();
  }

  @Override
  public List<ActionTiming> actionTimings() {
    return state().actionTimings();
  }
}
