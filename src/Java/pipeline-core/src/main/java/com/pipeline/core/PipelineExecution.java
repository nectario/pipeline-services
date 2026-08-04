package com.pipeline.core;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Objects;

/** Execution-scoped control available to Actions declared anywhere. */
public final class PipelineExecution {
  private static final ThreadLocal<Deque<ExecutionState<?>>> ACTIVE_EXECUTIONS =
      ThreadLocal.withInitial(ArrayDeque::new);

  private PipelineExecution() {}

  /** Marks the innermost actively executing Pipeline Action as short-circuited. */
  public static void shortCircuit() {
    ExecutionState<?> executionState = currentState();
    if (!executionState.isActionExecuting()) {
      throw new IllegalStateException(
          "shortCircuit() can only be called while a Pipeline Action is executing");
    }
    executionState.requestShortCircuit();
  }

  static Scope open(ExecutionState<?> executionState) {
    Objects.requireNonNull(executionState, "executionState");
    Deque<ExecutionState<?>> executions = ACTIVE_EXECUTIONS.get();
    executions.push(executionState);
    return new Scope(executionState);
  }

  static ExecutionState<?> currentState() {
    Deque<ExecutionState<?>> executions = ACTIVE_EXECUTIONS.get();
    if (executions.isEmpty()) {
      ACTIVE_EXECUTIONS.remove();
      throw new IllegalStateException(
          "shortCircuit() can only be called during an active pipeline run");
    }
    return executions.peek();
  }

  @SuppressWarnings("unchecked")
  static <C> ExecutionState<C> currentStateTyped() {
    return (ExecutionState<C>) currentState();
  }

  static final class Scope implements AutoCloseable {
    private final ExecutionState<?> expectedState;
    private boolean closed;

    private Scope(ExecutionState<?> expectedState) {
      this.expectedState = expectedState;
    }

    @Override
    public void close() {
      if (closed) return;
      closed = true;

      Deque<ExecutionState<?>> executions = ACTIVE_EXECUTIONS.get();
      if (executions.isEmpty() || executions.peek() != expectedState) {
        ACTIVE_EXECUTIONS.remove();
        throw new IllegalStateException("Pipeline execution scopes were closed out of order");
      }

      executions.pop();
      if (executions.isEmpty()) ACTIVE_EXECUTIONS.remove();
    }
  }
}
