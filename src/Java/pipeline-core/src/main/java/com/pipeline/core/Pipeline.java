package com.pipeline.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.UnaryOperator;

/**
 * A reusable, single-context pipeline.
 *
 * <p>Actions are registered during assembly. The plan freezes explicitly or on first run.
 * Every run receives independent execution state, so one frozen Pipeline may be reused concurrently
 * when its user-provided Actions are themselves concurrency-safe.</p>
 */
public class Pipeline<C> {
  private final String pipelineName;
  private final Object assemblyLock = new Object();
  private final List<RegisteredAction<C>> preActions = new ArrayList<>();
  private final List<RegisteredAction<C>> actions = new ArrayList<>();
  private final List<RegisteredAction<C>> postActions = new ArrayList<>();

  private boolean shortCircuitOnException;
  private BiFunction<C, PipelineError, C> errorHandler = (context, error) -> context;
  private PipelineObserver observer = PipelineObserver.NOOP;
  private volatile PipelinePlan<C> frozenPlan;

  public Pipeline(String pipelineName) {
    this(pipelineName, true);
  }

  public Pipeline(String pipelineName, boolean shortCircuitOnException) {
    String normalizedName = Objects.requireNonNull(pipelineName, "pipelineName").strip();
    if (normalizedName.isEmpty()) {
      throw new IllegalArgumentException("pipelineName must not be blank");
    }
    this.pipelineName = normalizedName;
    this.shortCircuitOnException = shortCircuitOnException;
  }

  @SafeVarargs
  public static <C> Pipeline<C> build(
      String pipelineName,
      boolean shortCircuitOnException,
      Action<C>... actions) {
    Pipeline<C> pipeline = new Pipeline<>(pipelineName, shortCircuitOnException);
    if (actions != null) {
      for (Action<C> action : actions) pipeline.addAction(action);
    }
    return pipeline.freeze();
  }

  /** Compatibility overload for ordinary {@link UnaryOperator} implementations. */
  @SafeVarargs
  public static <C> Pipeline<C> build(
      String pipelineName,
      boolean shortCircuitOnException,
      UnaryOperator<C>... actions) {
    Pipeline<C> pipeline = new Pipeline<>(pipelineName, shortCircuitOnException);
    if (actions != null) {
      for (UnaryOperator<C> action : actions) pipeline.addAction(action);
    }
    return pipeline.freeze();
  }

  /** Compatibility overload for the preview control-aware Action shape. */
  @SafeVarargs
  public static <C> Pipeline<C> build(
      String pipelineName,
      boolean shortCircuitOnException,
      StepAction<C>... actions) {
    Pipeline<C> pipeline = new Pipeline<>(pipelineName, shortCircuitOnException);
    if (actions != null) {
      for (StepAction<C> action : actions) pipeline.addAction(action);
    }
    return pipeline.freeze();
  }

  public static <C> Builder<C> builder(String pipelineName) {
    return new Builder<>(pipelineName);
  }

  public final Pipeline<C> shortCircuitOnException(boolean enabled) {
    synchronized (assemblyLock) {
      ensureMutable();
      shortCircuitOnException = enabled;
      return this;
    }
  }

  /** Legacy alias retained during the preview migration. */
  @Deprecated
  public final Pipeline<C> shortCircuit(boolean enabled) {
    return shortCircuitOnException(enabled);
  }

  public final Pipeline<C> onError(BiFunction<C, PipelineError, C> handler) {
    synchronized (assemblyLock) {
      ensureMutable();
      errorHandler = handler == null ? ((context, error) -> context) : handler;
      return this;
    }
  }

  public final Pipeline<C> observer(PipelineObserver pipelineObserver) {
    synchronized (assemblyLock) {
      ensureMutable();
      observer = pipelineObserver == null ? PipelineObserver.NOOP : pipelineObserver;
      return this;
    }
  }

  public final Pipeline<C> addPreAction(Action<C> action) {
    return addPreAction(null, action);
  }

  public final Pipeline<C> addAction(Action<C> action) {
    return addAction(null, action);
  }

  public final Pipeline<C> addPostAction(Action<C> action) {
    return addPostAction(null, action);
  }

  public final Pipeline<C> addPreAction(String actionName, Action<C> action) {
    register(preActions, actionName, action);
    return this;
  }

  public final Pipeline<C> addAction(String actionName, Action<C> action) {
    register(actions, actionName, action);
    return this;
  }

  public final Pipeline<C> addPostAction(String actionName, Action<C> action) {
    register(postActions, actionName, action);
    return this;
  }

  /** Compatibility overload for ordinary {@link UnaryOperator} implementations. */
  public final Pipeline<C> addPreAction(UnaryOperator<C> action) {
    return addPreAction(null, action);
  }

  /** Compatibility overload for ordinary {@link UnaryOperator} implementations. */
  public final Pipeline<C> addAction(UnaryOperator<C> action) {
    return addAction(null, action);
  }

  /** Compatibility overload for ordinary {@link UnaryOperator} implementations. */
  public final Pipeline<C> addPostAction(UnaryOperator<C> action) {
    return addPostAction(null, action);
  }

  /** Compatibility overload for ordinary {@link UnaryOperator} implementations. */
  public final Pipeline<C> addPreAction(String actionName, UnaryOperator<C> action) {
    return addPreAction(actionName, Action.from(action));
  }

  /** Compatibility overload for ordinary {@link UnaryOperator} implementations. */
  public final Pipeline<C> addAction(String actionName, UnaryOperator<C> action) {
    return addAction(actionName, Action.from(action));
  }

  /** Compatibility overload for ordinary {@link UnaryOperator} implementations. */
  public final Pipeline<C> addPostAction(String actionName, UnaryOperator<C> action) {
    return addPostAction(actionName, Action.from(action));
  }

  /** Compatibility overload for the preview control-aware Action shape. */
  @Deprecated
  public final Pipeline<C> addPreAction(StepAction<C> action) {
    return addPreAction(null, action);
  }

  /** Compatibility overload for the preview control-aware Action shape. */
  @Deprecated
  public final Pipeline<C> addAction(StepAction<C> action) {
    return addAction(null, action);
  }

  /** Compatibility overload for the preview control-aware Action shape. */
  @Deprecated
  public final Pipeline<C> addPostAction(StepAction<C> action) {
    return addPostAction(null, action);
  }

  /** Compatibility overload for the preview control-aware Action shape. */
  @Deprecated
  public final Pipeline<C> addPreAction(String actionName, StepAction<C> action) {
    return addPreAction(actionName, adapt(action));
  }

  /** Compatibility overload for the preview control-aware Action shape. */
  @Deprecated
  public final Pipeline<C> addAction(String actionName, StepAction<C> action) {
    return addAction(actionName, adapt(action));
  }

  /** Compatibility overload for the preview control-aware Action shape. */
  @Deprecated
  public final Pipeline<C> addPostAction(String actionName, StepAction<C> action) {
    return addPostAction(actionName, adapt(action));
  }

  /** Marks the innermost active Pipeline run as short-circuited. */
  public final void shortCircuit() {
    PipelineExecution.shortCircuit();
  }

  /** Returns the final context through the one canonical runner. */
  public final C run(C input) {
    return PipelineRunner.run(plan(), input);
  }

  /** Returns the final context plus diagnostics through the same canonical runner. */
  public final PipelineResult<C> runDetailed(C input) {
    return PipelineRunner.runDetailed(plan(), input);
  }

  /** Legacy diagnostic alias retained during the preview migration. */
  @Deprecated
  public final PipelineResult<C> execute(C input) {
    return runDetailed(input);
  }

  /** Freezes structural configuration and returns this Pipeline. */
  public final Pipeline<C> freeze() {
    plan();
    return this;
  }

  public final boolean isFrozen() {
    return frozenPlan != null;
  }

  public final String pipelineName() {
    return pipelineName;
  }

  /** Legacy name retained during the preview migration. */
  public final String name() {
    return pipelineName;
  }

  public final boolean shortCircuitOnException() {
    PipelinePlan<C> currentPlan = frozenPlan;
    return currentPlan == null
        ? shortCircuitOnException
        : currentPlan.shortCircuitOnException();
  }

  public final int size() {
    PipelinePlan<C> currentPlan = frozenPlan;
    return currentPlan == null ? actions.size() : currentPlan.actions().size();
  }

  private void register(
      List<RegisteredAction<C>> destination,
      String actionName,
      Action<C> action) {
    Objects.requireNonNull(action, "action");
    synchronized (assemblyLock) {
      ensureMutable();
      destination.add(RegisteredAction.of(actionName, action));
    }
  }

  private PipelinePlan<C> plan() {
    PipelinePlan<C> currentPlan = frozenPlan;
    if (currentPlan != null) return currentPlan;

    synchronized (assemblyLock) {
      currentPlan = frozenPlan;
      if (currentPlan == null) {
        currentPlan = new PipelinePlan<>(
            pipelineName,
            shortCircuitOnException,
            errorHandler,
            observer,
            preActions,
            actions,
            postActions);
        frozenPlan = currentPlan;
      }
      return currentPlan;
    }
  }

  private void ensureMutable() {
    if (frozenPlan != null) {
      throw new IllegalStateException("Pipeline '" + pipelineName + "' is frozen");
    }
  }

  private static <C> Action<C> adapt(StepAction<C> action) {
    Objects.requireNonNull(action, "action");
    return context -> action.apply(context, CurrentActionControl.instance());
  }

  public static final class Builder<C> {
    private final Pipeline<C> pipeline;

    private Builder(String pipelineName) {
      pipeline = new Pipeline<>(pipelineName);
    }

    public Builder<C> shortCircuitOnException(boolean enabled) {
      pipeline.shortCircuitOnException(enabled);
      return this;
    }

    /** Legacy alias retained during the preview migration. */
    @Deprecated
    public Builder<C> shortCircuit(boolean enabled) {
      return shortCircuitOnException(enabled);
    }

    public Builder<C> onError(BiFunction<C, PipelineError, C> handler) {
      pipeline.onError(handler);
      return this;
    }

    public Builder<C> observer(PipelineObserver observer) {
      pipeline.observer(observer);
      return this;
    }

    public Builder<C> addPreAction(Action<C> action) {
      pipeline.addPreAction(action);
      return this;
    }

    public Builder<C> addAction(Action<C> action) {
      pipeline.addAction(action);
      return this;
    }

    public Builder<C> addPostAction(Action<C> action) {
      pipeline.addPostAction(action);
      return this;
    }

    public Builder<C> addPreAction(String actionName, Action<C> action) {
      pipeline.addPreAction(actionName, action);
      return this;
    }

    public Builder<C> addAction(String actionName, Action<C> action) {
      pipeline.addAction(actionName, action);
      return this;
    }

    public Builder<C> addPostAction(String actionName, Action<C> action) {
      pipeline.addPostAction(actionName, action);
      return this;
    }

    public Builder<C> addPreAction(UnaryOperator<C> action) {
      pipeline.addPreAction(action);
      return this;
    }

    public Builder<C> addAction(UnaryOperator<C> action) {
      pipeline.addAction(action);
      return this;
    }

    public Builder<C> addPostAction(UnaryOperator<C> action) {
      pipeline.addPostAction(action);
      return this;
    }

    public Builder<C> addPreAction(String actionName, UnaryOperator<C> action) {
      pipeline.addPreAction(actionName, action);
      return this;
    }

    public Builder<C> addAction(String actionName, UnaryOperator<C> action) {
      pipeline.addAction(actionName, action);
      return this;
    }

    public Builder<C> addPostAction(String actionName, UnaryOperator<C> action) {
      pipeline.addPostAction(actionName, action);
      return this;
    }

    @Deprecated
    public Builder<C> addPreAction(StepAction<C> action) {
      pipeline.addPreAction(action);
      return this;
    }

    @Deprecated
    public Builder<C> addAction(StepAction<C> action) {
      pipeline.addAction(action);
      return this;
    }

    @Deprecated
    public Builder<C> addPostAction(StepAction<C> action) {
      pipeline.addPostAction(action);
      return this;
    }

    @Deprecated
    public Builder<C> addPreAction(String actionName, StepAction<C> action) {
      pipeline.addPreAction(actionName, action);
      return this;
    }

    @Deprecated
    public Builder<C> addAction(String actionName, StepAction<C> action) {
      pipeline.addAction(actionName, action);
      return this;
    }

    @Deprecated
    public Builder<C> addPostAction(String actionName, StepAction<C> action) {
      pipeline.addPostAction(actionName, action);
      return this;
    }

    public Pipeline<C> build() {
      return pipeline.freeze();
    }
  }
}
