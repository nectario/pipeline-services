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
    Throwable executionFailure = null;

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
      } catch (Throwable failure) {
        executionFailure = failure;
      }

      try {
        executeActions(
            plan,
            executionState,
            StepPhase.POST,
            plan.postActions(),
            false,
            collectTimings);
      } catch (Throwable postFailure) {
        executionFailure = combineFailures(executionFailure, postFailure);
      }
    } finally {
      try {
        executionScope.close();
      } catch (Throwable scopeFailure) {
        executionFailure = combineFailures(executionFailure, scopeFailure);
      } finally {
        long elapsedNanos = Math.max(0L, System.nanoTime() - runStartNanos);
        notifyPipelineCompleted(plan, executionState, elapsedNanos);
      }
    }

    if (executionFailure != null) {
      throw propagate(executionFailure);
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
    RuntimeException deferredPostFailure = null;

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
      RuntimeException invalidErrorHandlerFailure = null;

      try {
        C nextContext;
        executionState.beginActionExecution();
        try {
          nextContext = registeredAction.action().execute(executionState.context());
        } finally {
          executionState.endActionExecution();
        }
        executionState.context(
            Objects.requireNonNull(nextContext, "Action returned null: " + actionName));
      } catch (Exception exception) {
        actionSucceeded = false;
        actionFailure = exception;
        try {
          executionState.recordError(executionState.context(), exception);
        } catch (RuntimeException errorHandlerFailure) {
          invalidErrorHandlerFailure = errorHandlerFailure;
          executionState.requestShortCircuit();
        }
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

      if (invalidErrorHandlerFailure != null) {
        if (phase == StepPhase.POST) {
          deferredPostFailure = combineRuntimeFailures(
              deferredPostFailure,
              invalidErrorHandlerFailure);
        } else {
          throw invalidErrorHandlerFailure;
        }
      }

      if (stopOnShortCircuit && executionState.isShortCircuited()) {
        break;
      }
    }

    if (deferredPostFailure != null) {
      throw deferredPostFailure;
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

  private static Throwable combineFailures(Throwable firstFailure, Throwable nextFailure) {
    if (firstFailure == null) return nextFailure;
    if (firstFailure != nextFailure) firstFailure.addSuppressed(nextFailure);
    return firstFailure;
  }

  private static RuntimeException combineRuntimeFailures(
      RuntimeException firstFailure,
      RuntimeException nextFailure) {
    if (firstFailure == null) return nextFailure;
    if (firstFailure != nextFailure) firstFailure.addSuppressed(nextFailure);
    return firstFailure;
  }

  private static RuntimeException propagate(Throwable failure) {
    if (failure instanceof RuntimeException runtimeException) {
      return runtimeException;
    }
    if (failure instanceof Error error) {
      throw error;
    }
    return new IllegalStateException("Unexpected checked Pipeline failure", failure);
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
