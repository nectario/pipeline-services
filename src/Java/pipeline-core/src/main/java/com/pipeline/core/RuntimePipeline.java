package com.pipeline.core;

import com.pipeline.metrics.Metrics;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.UnaryOperator;

/**
 * Imperative, single-threaded, runtime pipeline for unary flows (T -> T).
 * - addPreAction/Action/PostAction append to a recording AND (if not ended) apply immediately.
 * - shortCircuit semantics match Pipeline<T>.
 * - Not thread-safe. Use per request/session or in tools/REPLs.
 */
public final class RuntimePipeline<T> {
  private final String name;
  private final boolean shortCircuitOnException;

  // Current value for this session
  private T current;
  private boolean ended = false;

  // Recording of the pipeline shape (preserved across reset() unless cleared)
  private final List<StepAction<T>> pre  = new ArrayList<>();
  private final List<StepAction<T>> main = new ArrayList<>();
  private final List<StepAction<T>> post = new ArrayList<>();

  // Indices for metrics labels (do not affect recording)
  private int preIdx = 0, actionIdx = 0, postIdx = 0;

  private final SessionActionControl<T> control;

  public RuntimePipeline(String name, boolean shortCircuitOnException, T initial) {
    this.name = name;
    this.shortCircuitOnException = shortCircuitOnException;
    this.current = initial;
    this.control = new SessionActionControl<>(name);
  }

  /** Apply a pre action (record + execute unless ended) and return the updated value. */
  public T addPreAction(StepAction<T> preFn) {
    if (ended) return current;           // <-- do not record after short-circuit
    pre.add(preFn);
    return apply(preFn, StepPhase.PRE, preIdx++, "pre");
  }

  /** Apply a main step (record + execute unless ended) and return the updated value. */
  public T addAction(StepAction<T> stepFn) {
    if (ended) return current;           // <-- do not record after short-circuit
    main.add(stepFn);
    return apply(stepFn, StepPhase.MAIN, actionIdx++, "s");
  }

  /** Apply a post action (record + execute unless ended) and return the updated value. */
  public T addPostAction(StepAction<T> postFn) {
    if (ended) return current;           // <-- do not record after short-circuit
    post.add(postFn);
    return apply(postFn, StepPhase.POST, postIdx++, "post");
  }

  public T addPreAction(UnaryOperator<T> fn) { return addPreAction(adapt(fn)); }
  public T addAction(UnaryOperator<T> fn) { return addAction(adapt(fn)); }
  public T addPostAction(UnaryOperator<T> fn) { return addPostAction(adapt(fn)); }

  /** The current value after the last add* call. */
  public T value() { return current; }

  /** Start a new session with a fresh input. Resets the "ended" flag but keeps the recording. */
  public void reset(T initial) {
    this.current = initial;
    this.ended = false;
    this.control.reset();
    // indices continue so metrics remain unique across a long-lived session
  }

  /** Remove all recorded steps (pre/main/post). Does not change the current session value. */
  public void clearRecorded() {
    pre.clear(); main.clear(); post.clear();
    preIdx = actionIdx = postIdx = 0;
  }

  /** Counts for introspection / tests. */
  public int recordedPreActionCount()  { return pre.size(); }
  public int recordedActionCount() { return main.size(); }
  public int recordedPostActionCount() { return post.size(); }

  /** Freeze the recorded steps into an immutable Pipeline<T>. */
  public Pipeline<T> toImmutable() {
    Pipeline.Builder<T> b = Pipeline.<T>builder(name).shortCircuitOnException(shortCircuitOnException);
    for (var fn : pre)  b.addPreAction(fn);
    for (var fn : main) b.addAction(fn);
    for (var fn : post) b.addPostAction(fn);
    return b.build();
  }

  /** Synonym for toImmutable() for readability. */
  public Pipeline<T> freeze() { return toImmutable(); }

  // --- internal ---

  private T apply(StepAction<T> fn, StepPhase phase, int idx, String prefix) {
    var rec = Metrics.recorder();

    // If the session has ended (explicit short-circuit), do not apply further steps or record metrics.
    if (ended) return current;

    String stepName = prefix + idx;
    control.beginStep(phase, idx, stepName);
    boolean wasShortCircuited = control.isShortCircuited();

    try {
      long t0 = System.nanoTime();
      T out = fn.apply(current, control);
      if (out == null) throw new IllegalStateException("Step returned null: " + stepName);
      rec.onStepSuccess(name, stepName, System.nanoTime() - t0);
      current = out;
      if (!wasShortCircuited && control.isShortCircuited()) {
        rec.onShortCircuit(name, stepName);
      }
      if (control.isShortCircuited()) ended = true;
      return current;
    } catch (Exception ex) {
      rec.onStepError(name, stepName, ex);
      current = control.recordError(current, ex);
      if (shortCircuitOnException) {
        control.shortCircuit();
        rec.onShortCircuit(name, stepName);
        ended = true;
      }
      // continue-on-error: keep current unchanged; session continues
      return current;
    }
  }

  private static <T> StepAction<T> adapt(UnaryOperator<T> fn) {
    Objects.requireNonNull(fn, "fn");
    return (ctx, control) -> fn.apply(ctx);
  }

  private static final class SessionActionControl<C> implements ActionControl<C> {
    private final String pipelineName;
    private final List<PipelineError> errors = new ArrayList<>();
    private boolean shortCircuited;
    private StepPhase phase = StepPhase.MAIN;
    private int index = 0;
    private String stepName = "?";

    private SessionActionControl(String pipelineName) {
      this.pipelineName = Objects.requireNonNull(pipelineName, "pipelineName");
    }

    private void beginStep(StepPhase phase, int index, String stepName) {
      this.phase = Objects.requireNonNull(phase, "phase");
      this.index = index;
      this.stepName = Objects.requireNonNull(stepName, "stepName");
    }

    private void reset() {
      shortCircuited = false;
      errors.clear();
      phase = StepPhase.MAIN;
      index = 0;
      stepName = "?";
    }

    @Override public void shortCircuit() { shortCircuited = true; }
    @Override public boolean isShortCircuited() { return shortCircuited; }

    @Override
    public C recordError(C ctx, Exception exception) {
      errors.add(new PipelineError(pipelineName, phase, index, stepName, exception));
      return ctx;
    }

    @Override
    public List<PipelineError> errors() {
      return List.copyOf(errors);
    }
  }
}
