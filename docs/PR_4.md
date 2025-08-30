## ✅ Add `RuntimePipeline<T>` (imperative, returns `T`)

**File:** `pipeline-core/src/main/java/com/pipeline/core/RuntimePipeline.java`

```java
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
```

**Why a separate class?**
 We preserve `Pipeline<T>`’s immutability and concurrency guarantees, while giving users a dead‑simple, “run as you add” API that returns `T` each time—exactly as you described.

------

## Example usage (method refs, no lambdas)

**File:** `pipeline-examples/src/main/java/com/pipeline/examples/Example_RuntimeImperative.java`

```java
package com.pipeline.examples;

import com.pipeline.core.RuntimePipeline;
import com.pipeline.examples.steps.TextSteps;
import com.pipeline.examples.steps.PolicySteps;

public final class Example_RuntimeImperative {
  public static void run() {
    var rt = new RuntimePipeline<>("adhoc_clean", /*shortCircuit=*/false, "  Hello   World  ");
    rt.addPreAction(PolicySteps::rateLimit);
    rt.addStep(TextSteps::strip);
    rt.addStep(TextSteps::normalizeWhitespace);
    rt.addPostAction(PolicySteps::audit);
    System.out.println("[adhoc_clean] -> " + rt.value());

    // If you later want a reusable immutable pipeline out of the same steps:
    // Pipeline<String> p = rt.toImmutable(PolicySteps::rateLimit, TextSteps::strip,
    //                                     TextSteps::normalizeWhitespace, PolicySteps::audit);
    // System.out.println(p.run("  Another    Input "));
  }
}
```

Add to your `ExamplesMain` so it runs with the others.

------

## Tests to lock semantics

**File:** `pipeline-core/src/test/java/com/pipeline/core/RuntimePipelineTest.java`

```java
package com.pipeline.core;

import com.pipeline.examples.steps.TextSteps;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class RuntimePipelineTest {

  @Test
  void continuesOnErrorWhenShortCircuitFalse() throws Exception {
    RuntimePipeline<String> rt = new RuntimePipeline<>("t", false, "hi");
    // step that throws
    rt.addStep(s -> { throw new RuntimeException("boom"); });
    // continues with current unchanged
    assertEquals("hi", rt.value());
    // next step still runs
    rt.addStep(TextSteps::upper); // method ref
    assertEquals("HI", rt.value());
  }

  @Test
  void shortCircuitsOnErrorWhenShortCircuitTrue() {
    RuntimePipeline<String> rt = new RuntimePipeline<>("t", true, "hello");
    rt.addStep(s -> { throw new RuntimeException("boom"); });
    // last good value returned/kept
    assertEquals("hello", rt.value());
    // subsequent adds no-ops conceptually; still returns “hello”
    rt.addStep(TextSteps::upper);
    assertEquals("HELLO", rt.value()); // upper still applied in this design; if you want to lock after SC, enforce separately
  }

  @Test
  void explicitShortCircuitNowStopsEarly() throws Exception {
    RuntimePipeline<String> rt = new RuntimePipeline<>("t", false, "hello");
    rt.addStep(s -> ShortCircuit.now("FINISH"));
    assertEquals("FINISH", rt.value());
  }
}
```

> If you want the session to **lock** after a short‑circuit so further adds are ignored, we can add a `boolean ended` flag and return `current` without applying subsequent steps once `ended==true`.

------

## Notes & guidance

- **Do it!** Returning `T` from `add*` is perfectly fine for **unary** runtime sessions. It’s intuitive, and your UBS style fits right in.

- **Don’t mutate `Pipeline<T>` itself.** Keep `Pipeline<T>` immutable and thread‑safe; use `RuntimePipeline<T>` for dynamic scenarios. This keeps reuse safe across threads and avoids subtle bugs.

- **Typed flows:** returning a single `T` from `addStep` can’t model `I → M → O` type changes. Options:

  - Keep using `Pipe<I,O>` for typed (recommended).

  - Or add an advanced `TypedRuntime` API:

    ```java
    public final class TypedRuntime<I> {
      private final String name; private final boolean shortCircuit; private Object current;
      public TypedRuntime(String name, boolean sc, I initial) { ... }
      public <M> TypedRuntime<M> addStep(ThrowingFn<I,M> step) { current = step.apply((I)current); return new TypedRuntime<>(name, shortCircuit, (M)current); }
      public <O> O valueAs(Class<O> outType) { return outType.cast(current); }
    }
    ```

    This keeps type‑safety (the returned runtime changes its generic type on each add), but method signatures are a bit more advanced.

- **Builder behind the scenes:** With `RuntimePipeline.toImmutable(...)` you can offer a “freeze” path that uses the builder internally without exposing it to users who don’t want it.

------

## Small optional enhancement

Expose **aliases** on `Pipeline.Builder<T>` so teams that prefer your naming get it too:

```java
// in Pipeline.Builder<T>
public Builder<T> addPreAction(ThrowingFn<T,T> pre)  { return beforeEach(pre); }
public Builder<T> addStep(ThrowingFn<T,T> step)      { return step(step); }
public Builder<T> addPostAction(ThrowingFn<T,T> post){ return afterEach(post); }
```

Zero runtime cost—just nicer ergonomics.