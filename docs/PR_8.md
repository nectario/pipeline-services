

## PR 8 - Freeze Pipelines or make Pipelines Immutable 



## What changes & why

- **Record as you go.** `addPreAction`, `addStep`, and `addPostAction` now:
  1. **Append** the function to an internal list (`pre`, `main`, `post`), and
  2. **Apply** it to the current value (unless the session already short‑circuited).
- **`toImmutable()`** builds a `Pipeline<T>` using the recorded lists, preserving pre/main/post structure.
- **`freeze()`** is a convenience alias for readability.
- **Short‑circuit behavior:** if a step calls `ShortCircuit.now(...)`, we mark the session **ended**; further `add*` calls **do not execute or record** until you call `reset(...)`. (This keeps the recording faithful to what actually ran.)
- **Introspection helpers** (optional): `recordedPreCount()`, `recordedStepCount()`, `recordedPostCount()`, and `clearRecorded()` in case you want to reuse the same runtime instance to record a different pipeline shape.

Runtime cost is negligible (three small `ArrayList`s per runtime session).

------

## Patch: `RuntimePipeline.java` (replace the class with this)

```java
package com.pipeline.core;

import com.pipeline.metrics.Metrics;

import java.util.ArrayList;
import java.util.List;

/**
 * Imperative, single-threaded, runtime pipeline for unary flows (T -> T).
 * - addPreAction/Step/PostAction append to a recording AND (if not ended) apply immediately.
 * - shortCircuit semantics match Pipeline<T>.
 * - Not thread-safe. Use per request/session or in tools/REPLs.
 */
public final class RuntimePipeline<T> {
  private final String name;
  private final boolean shortCircuit;

  // Current value for this session
  private T current;
  private boolean ended = false;

  // Recording of the pipeline shape (preserved across reset() unless cleared)
  private final List<ThrowingFn<T,T>> pre  = new ArrayList<>();
  private final List<ThrowingFn<T,T>> main = new ArrayList<>();
  private final List<ThrowingFn<T,T>> post = new ArrayList<>();

  // Indices for metrics labels (do not affect recording)
  private int preIdx = 0, stepIdx = 0, postIdx = 0;

  public RuntimePipeline(String name, boolean shortCircuit, T initial) {
    this.name = name;
    this.shortCircuit = shortCircuit;
    this.current = initial;
  }

  /** Apply a pre action (record + execute unless ended) and return the updated value. */
  public T addPreAction(ThrowingFn<T,T> preFn) {
    pre.add(preFn);
    return apply(preFn, "pre" + preIdx++);
  }

  /** Apply a main step (record + execute unless ended) and return the updated value. */
  public T addStep(ThrowingFn<T,T> stepFn) {
    main.add(stepFn);
    return apply(stepFn, "s" + stepIdx++);
  }

  /** Apply a post action (record + execute unless ended) and return the updated value. */
  public T addPostAction(ThrowingFn<T,T> postFn) {
    post.add(postFn);
    return apply(postFn, "post" + postIdx++);
  }

  /** The current value after the last add* call. */
  public T value() { return current; }

  /** Start a new session with a fresh input. Resets the "ended" flag but keeps the recording. */
  public void reset(T initial) {
    this.current = initial;
    this.ended = false;
    // indices continue so metrics remain unique across a long-lived session
  }

  /** Remove all recorded steps (pre/main/post). Does not change the current session value. */
  public void clearRecorded() {
    pre.clear(); main.clear(); post.clear();
    preIdx = stepIdx = postIdx = 0;
  }

  /** Counts for introspection / tests. */
  public int recordedPreCount()  { return pre.size(); }
  public int recordedStepCount() { return main.size(); }
  public int recordedPostCount() { return post.size(); }

  /** Freeze the recorded steps into an immutable Pipeline<T>. */
  public Pipeline<T> toImmutable() {
    Pipeline.Builder<T> b = Pipeline.builder(name).shortCircuit(shortCircuit);
    for (var fn : pre)  b.beforeEach(fn);
    for (var fn : main) b.step(fn);
    for (var fn : post) b.afterEach(fn);
    return b.build();
  }

  /** Synonym for toImmutable() for readability. */
  public Pipeline<T> freeze() { return toImmutable(); }

  // --- internal ---

  private T apply(ThrowingFn<T,T> fn, String stepName) {
    var rec = Metrics.recorder();

    // If the session has ended (explicit short-circuit), do not apply further steps or record metrics.
    if (ended) return current;

    try {
      long t0 = System.nanoTime();
      T out = fn.apply(current);
      rec.onStepSuccess(name, stepName, System.nanoTime() - t0);
      current = out;
      return current;
    } catch (ShortCircuit.Signal sc) {
      rec.onShortCircuit(name, stepName);
      @SuppressWarnings("unchecked") T v = (T) sc.value;
      current = v;
      ended = true;                      // stop executing subsequent add* until reset()
      return current;
    } catch (Exception ex) {
      rec.onStepError(name, stepName, ex);
      if (shortCircuit) {
        // implicit short-circuit on error: keep last good current and end session
        ended = true;
        return current;
      } else {
        // continue-on-error: skip, keep current unchanged; session continues
        return current;
      }
    }
  }
}
```

**Behavior summary:**

- You can call `rt.toImmutable()` (or `rt.freeze()`) anytime to get a reusable `Pipeline<T>` matching what you’ve built so far.
- After a short‑circuit in this session, further `add*` calls are ignored until `reset()`—they also **won’t** be added to the recording. This keeps the frozen pipeline consistent with what ran.

------

## Example update (one line)

In your example that previously passed the steps again, change:

```java
// before
Pipeline<String> frozen = rt.toImmutable(
    com.pipeline.examples.steps.PolicySteps::rateLimit,
    TextSteps::strip,
    TextSteps::normalizeWhitespace,
    com.pipeline.examples.steps.PolicySteps::audit
);

// after
Pipeline<String> frozen = rt.toImmutable();  // or rt.freeze();
```

(Everything else in the example remains the same.)

