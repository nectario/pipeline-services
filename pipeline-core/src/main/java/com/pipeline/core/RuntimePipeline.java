package com.pipeline.core;

import com.pipeline.metrics.Metrics;

/**
 * Imperative, single-threaded, runtime pipeline for unary flows (T -> T).
 * - addPreAction/Step/PostAction apply immediately and return the current T.
 * - shortCircuit semantics match Pipeline<T>.
 * - Not thread-safe. Use per request/session or in tools/REPLs.
 */
public final class RuntimePipeline<T> {
  private final String name;
  private final boolean shortCircuit;
  private T current;
  private int preIdx = 0, stepIdx = 0, postIdx = 0;

  public RuntimePipeline(String name, boolean shortCircuit, T initial) {
    this.name = name;
    this.shortCircuit = shortCircuit;
    this.current = initial;
  }

  /** Apply a pre action immediately and return the updated value. */
  public T addPreAction(ThrowingFn<T,T> pre) {
    current = apply(pre, "pre" + preIdx++);
    return current;
  }

  /** Apply a main step immediately and return the updated value. */
  public T addStep(ThrowingFn<T,T> step) {
    current = apply(step, "s" + stepIdx++);
    return current;
  }

  /** Apply a post action immediately and return the updated value. */
  public T addPostAction(ThrowingFn<T,T> post) {
    current = apply(post, "post" + postIdx++);
    return current;
  }

  /** Current value (after the last add). */
  public T value() { return current; }

  /** Reset the session with a new starting value (indexes continue for metrics). */
  public void reset(T initial) { this.current = initial; }

  /** Freeze the steps used so far into a reusable immutable Pipeline. */
  @SafeVarargs
  public final Pipeline<T> toImmutable(ThrowingFn<T,T>... stepsInOrder) {
    return Pipeline.build(name, shortCircuit, stepsInOrder);
  }

  private T apply(ThrowingFn<T,T> fn, String stepName) {
    var rec = Metrics.recorder();
    try {
      long t0 = System.nanoTime();
      T out = fn.apply(current);
      rec.onStepSuccess(name, stepName, System.nanoTime() - t0);
      return out;
    } catch (ShortCircuit.Signal sc) {
      rec.onShortCircuit(name, stepName);
      @SuppressWarnings("unchecked") T v = (T) sc.value;
      current = v;
      return current;                  // Session ends “logically”, but caller may still add ops if desired.
    } catch (Exception ex) {
      rec.onStepError(name, stepName, ex);
      if (shortCircuit) {
        // implicit short-circuit on error: keep last good current
        return current;
      } else {
        // continue-on-error: skip, return current unchanged
        return current;
      }
    }
  }
}

