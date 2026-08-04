package com.pipeline.core;

/** Optional observer for one canonical pipeline runner. Observer failures are ignored. */
public interface PipelineObserver {
  PipelineObserver NOOP = new PipelineObserver() {};

  default void onPipelineStarted(String pipelineName) {}

  default void onActionStarted(
      String pipelineName,
      StepPhase phase,
      int actionIndex,
      String actionName) {}

  default void onActionCompleted(
      String pipelineName,
      StepPhase phase,
      int actionIndex,
      String actionName,
      long elapsedNanos) {}

  default void onActionFailed(
      String pipelineName,
      StepPhase phase,
      int actionIndex,
      String actionName,
      Exception error,
      long elapsedNanos) {}

  default void onShortCircuited(
      String pipelineName,
      StepPhase phase,
      int actionIndex,
      String actionName) {}

  default void onPipelineCompleted(
      String pipelineName,
      boolean shortCircuited,
      int errorCount,
      long elapsedNanos) {}
}
