package com.pipeline.core;

import java.util.List;
import java.util.Objects;

/** The one canonical Java execution engine. */
final class PipelineRunner {
  private PipelineRunner() {}

  static <C> C run(PipelinePlan<C> plan, C input) {
    return execute(plan, input, false).context();
  }

  static <C> PipelineResult<C> runDetailed(PipelinePlan<C> plan, C input) {
    ExecutionState<C> executionState = execute(plan, input, true);
    long totalNanos = Math.max(0L, System.nanoTime() - executionState.runStartNanos());
    return new PipelineResult<>(
        executionState.context(),
        executionState.isShortCircuited(),
        executionState.errors(),
        executionState.actionTimings(),
        totalNanos);
  }

  private static <C> ExecutionState<C> execute(
      PipelinePlan<C> plan,
      C input,
      boolean collectTimings) {
    Objects.requireNonNull(plan, "plan");

    long runStartNanos = System.nanoTime();
    ExecutionState<C> executionState =
        new ExecutionState<>(plan, input, collectTimings, runStartNanos);
    notifyPipelineStarted(plan);

    PipelineExecution.Scope executionScope = PipelineExecution.open(executionState);
    try {
      try {
        executeActions(
            plan,
            executionState,
            StepPhase.PRE,
            plan.preActions(),
            false,
            collectTimings);

        if (!executionState.isShortCircuited()) {
          executeActions(
              plan,
              executionState,
              StepPhase.MAIN,
              plan.actions(),
              true,
              collectTimings);
        }
      } finally {
        executeActions(
            plan,
            executionState,
            StepPhase.POST,
            plan.postActions(),
            false,
            collectTimings);
      }
    } finally {
      try {
        executionScope.close();
      } finally {
        long elapsedNanos = Math.max(0L, System.nanoTime() - runStartNanos);
        notifyPipelineCompleted(plan, executionState, elapsedNanos);
      }
    }

    return executionState;
  }

  private static <C> void executeActions(
      PipelinePlan<C> plan,
      ExecutionState<C> executionState,
      StepPhase phase,
      List<RegisteredAction<C>> registeredActions,
      boolean stopOnShortCircuit,
      boolean collectTimings) {

    boolean observerEnabled = plan.observer() != PipelineObserver.NOOP;

    for (int actionIndex = 0; actionIndex < registeredActions.size(); actionIndex++) {
      RegisteredAction<C> registeredAction = registeredActions.get(actionIndex);
      String actionName = formatActionName(phase, actionIndex, registeredAction.name());
      executionState.beginAction(phase, actionIndex, actionName);

      boolean wasShortCircuited = executionState.isShortCircuited();
      if (observerEnabled) {
        notifyActionStarted(plan, phase, actionIndex, actionName);
      }

      long actionStartNanos =
          (collectTimings || observerEnabled) ? System.nanoTime() : 0L;
      boolean actionSucceeded = true;
      Exception actionFailure = null;

      try {
        C nextContext = registeredAction.action().apply(executionState.context());
        executionState.context(
            Objects.requireNonNull(nextContext, "Action returned null: " + actionName));
      } catch (Exception exception) {
        actionSucceeded = false;
        actionFailure = exception;
        executionState.recordError(executionState.context(), exception);
        if (plan.shortCircuitOnException()) {
          executionState.requestShortCircuit();
        }
      }

      long elapsedNanos = (collectTimings || observerEnabled)
          ? Math.max(0L, System.nanoTime() - actionStartNanos)
          : 0L;
      executionState.recordTiming(elapsedNanos, actionSucceeded);

      if (observerEnabled) {
        if (actionSucceeded) {
          notifyActionCompleted(plan, phase, actionIndex, actionName, elapsedNanos);
        } else {
          notifyActionFailed(
              plan,
              phase,
              actionIndex,
              actionName,
              actionFailure,
              elapsedNanos);
        }
      }

      if (!wasShortCircuited && executionState.isShortCircuited()) {
        notifyShortCircuited(plan, phase, actionIndex, actionName);
      }

      if (stopOnShortCircuit && executionState.isShortCircuited()) {
        break;
      }
    }
  }

  private static String formatActionName(
      StepPhase phase,
      int actionIndex,
      String registeredName) {
    String prefix = switch (phase) {
      case PRE -> "pre";
      case MAIN -> "s";
      case POST -> "post";
    };
    if (registeredName == null) return prefix + actionIndex;
    return prefix + actionIndex + ":" + registeredName;
  }

  private static <C> void notifyPipelineStarted(PipelinePlan<C> plan) {
    try {
      plan.observer().onPipelineStarted(plan.pipelineName());
    } catch (RuntimeException ignored) {
      // Observers must not change pipeline semantics.
    }
  }

  private static <C> void notifyActionStarted(
      PipelinePlan<C> plan,
      StepPhase phase,
      int index,
      String name) {
    try {
      plan.observer().onActionStarted(plan.pipelineName(), phase, index, name);
    } catch (RuntimeException ignored) {
      // Observers must not change pipeline semantics.
    }
  }

  private static <C> void notifyActionCompleted(
      PipelinePlan<C> plan,
      StepPhase phase,
      int index,
      String name,
      long nanos) {
    try {
      plan.observer().onActionCompleted(plan.pipelineName(), phase, index, name, nanos);
    } catch (RuntimeException ignored) {
      // Observers must not change pipeline semantics.
    }
  }

  private static <C> void notifyActionFailed(
      PipelinePlan<C> plan,
      StepPhase phase,
      int index,
      String name,
      Exception error,
      long nanos) {
    try {
      plan.observer().onActionFailed(
          plan.pipelineName(), phase, index, name, error, nanos);
    } catch (RuntimeException ignored) {
      // Observers must not change pipeline semantics.
    }
  }

  private static <C> void notifyShortCircuited(
      PipelinePlan<C> plan,
      StepPhase phase,
      int index,
      String name) {
    try {
      plan.observer().onShortCircuited(plan.pipelineName(), phase, index, name);
    } catch (RuntimeException ignored) {
      // Observers must not change pipeline semantics.
    }
  }

  private static <C> void notifyPipelineCompleted(
      PipelinePlan<C> plan,
      ExecutionState<C> executionState,
      long nanos) {
    try {
      plan.observer().onPipelineCompleted(
          plan.pipelineName(),
          executionState.isShortCircuited(),
          executionState.errors().size(),
          nanos);
    } catch (RuntimeException ignored) {
      // Observers must not change pipeline semantics.
    }
  }
}
