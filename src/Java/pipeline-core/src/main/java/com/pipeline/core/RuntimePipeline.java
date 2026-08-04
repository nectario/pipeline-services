package com.pipeline.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.UnaryOperator;

/**
 * Legacy imperative assembly helper for interactive sessions.
 *
 * <p>Each newly registered Action is executed immediately through the canonical Pipeline runner.
 * The recorded Actions can then be frozen into a normal reusable Pipeline.</p>
 */
@Deprecated
public final class RuntimePipeline<T> {
  private final String pipelineName;
  private final boolean shortCircuitOnException;
  private final List<Action<T>> preActions = new ArrayList<>();
  private final List<Action<T>> actions = new ArrayList<>();
  private final List<Action<T>> postActions = new ArrayList<>();

  private T currentContext;
  private boolean ended;
  private int preActionIndex;
  private int actionIndex;
  private int postActionIndex;

  public RuntimePipeline(
      String pipelineName,
      boolean shortCircuitOnException,
      T initialContext) {
    this.pipelineName = Objects.requireNonNull(pipelineName, "pipelineName");
    this.shortCircuitOnException = shortCircuitOnException;
    this.currentContext = Objects.requireNonNull(initialContext, "initialContext");
  }

  public T addPreAction(StepAction<T> action) {
    return addPreAction(adapt(action));
  }

  public T addAction(StepAction<T> action) {
    return addAction(adapt(action));
  }

  public T addPostAction(StepAction<T> action) {
    return addPostAction(adapt(action));
  }

  public T addPreAction(UnaryOperator<T> action) {
    if (ended) return currentContext;
    Action<T> canonicalAction = Action.from(action);
    preActions.add(canonicalAction);
    return executeImmediately(
        canonicalAction,
        StepPhase.PRE,
        "pre" + preActionIndex++);
  }

  public T addAction(UnaryOperator<T> action) {
    if (ended) return currentContext;
    Action<T> canonicalAction = Action.from(action);
    actions.add(canonicalAction);
    return executeImmediately(
        canonicalAction,
        StepPhase.MAIN,
        "s" + actionIndex++);
  }

  public T addPostAction(UnaryOperator<T> action) {
    if (ended) return currentContext;
    Action<T> canonicalAction = Action.from(action);
    postActions.add(canonicalAction);
    return executeImmediately(
        canonicalAction,
        StepPhase.POST,
        "post" + postActionIndex++);
  }

  public T value() {
    return currentContext;
  }

  public void reset(T initialContext) {
    currentContext = Objects.requireNonNull(initialContext, "initialContext");
    ended = false;
  }

  public void clearRecorded() {
    preActions.clear();
    actions.clear();
    postActions.clear();
    preActionIndex = 0;
    actionIndex = 0;
    postActionIndex = 0;
  }

  public int recordedPreActionCount() {
    return preActions.size();
  }

  public int recordedActionCount() {
    return actions.size();
  }

  public int recordedPostActionCount() {
    return postActions.size();
  }

  public Pipeline<T> toImmutable() {
    Pipeline<T> pipeline = new Pipeline<T>(pipelineName, shortCircuitOnException);
    for (Action<T> action : preActions) pipeline.addPreAction(action);
    for (Action<T> action : actions) pipeline.addAction(action);
    for (Action<T> action : postActions) pipeline.addPostAction(action);
    return pipeline.freeze();
  }

  public Pipeline<T> freeze() {
    return toImmutable();
  }

  private T executeImmediately(
      Action<T> action,
      StepPhase phase,
      String actionName) {
    Pipeline<T> singleActionPipeline = new Pipeline<T>(
        pipelineName + ".runtime." + actionName,
        shortCircuitOnException);

    switch (phase) {
      case PRE -> singleActionPipeline.addPreAction(actionName, action);
      case MAIN -> singleActionPipeline.addAction(actionName, action);
      case POST -> singleActionPipeline.addPostAction(actionName, action);
    }

    PipelineResult<T> result = singleActionPipeline.runDetailed(currentContext);
    currentContext = result.context();
    ended = result.shortCircuited();
    return currentContext;
  }

  private static <T> Action<T> adapt(StepAction<T> action) {
    Objects.requireNonNull(action, "action");
    return context -> action.apply(context, CurrentActionControl.instance());
  }
}
