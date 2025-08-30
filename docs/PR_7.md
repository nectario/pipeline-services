# PR 7 — `examples: add RuntimePipeline examples and wire into runner`

**Branch name:** `feat/runtime-pipeline-examples`

## 1) Add three method‑reference examples (no lambdas)

Create the following files under **`pipeline-examples/src/main/java/com/pipeline/examples/`**.

### `Example11RuntimeTextClean.java`

```java
package com.pipeline.examples;

import com.pipeline.core.RuntimePipeline;
import com.pipeline.examples.steps.PolicySteps;
import com.pipeline.examples.steps.TextSteps;

public final class Example11RuntimeTextClean {
  private Example11RuntimeTextClean() {}

  public static void run() {
    var rt = new RuntimePipeline<>("runtime_text_clean", /*shortCircuit=*/false, "  Hello   World  ");
    rt.addPreAction(PolicySteps::rateLimit);
    rt.addStep(TextSteps::strip);
    rt.addStep(TextSteps::normalizeWhitespace);
    rt.addStep(TextSteps::truncateAt280);  // explicit ShortCircuit inside;
    rt.addPostAction(PolicySteps::audit);

    System.out.println("[ex11-runtime] -> " + rt.value());
  }
}
```

### `Example12RuntimeListFlow.java`

```java
package com.pipeline.examples;

import com.pipeline.core.RuntimePipeline;
import com.pipeline.examples.steps.ListSteps;

import java.util.List;

public final class Example12RuntimeListFlow {
  private Example12RuntimeListFlow() {}

  public static void run() {
    // First run: non-empty list flows through all steps
    var rt = new RuntimePipeline<>("runtime_list_flow", /*shortCircuit=*/true,
                                   List.of("orange", "apple", "orange"));
    rt.addStep(ListSteps::nonEmptyOrShortCircuit);
    rt.addStep(ListSteps::dedup);
    rt.addStep(ListSteps::sortNatural);
    System.out.println("[ex12-runtime-1] -> " + rt.value());

    // Second run: reset with empty list triggers an early ShortCircuit in the first step
    rt.reset(List.of());
    rt.addStep(ListSteps::nonEmptyOrShortCircuit); // ShortCircuit.now(...) returns immediately
    rt.addStep(ListSteps::dedup);                   // still safe if called again
    rt.addStep(ListSteps::sortNatural);
    System.out.println("[ex12-runtime-2] -> " + rt.value());
  }
}
```

### `Example13RuntimeResetAndFreeze.java`

```java
package com.pipeline.examples;

import com.pipeline.core.Pipeline;
import com.pipeline.core.RuntimePipeline;
import com.pipeline.examples.steps.PolicySteps;
import com.pipeline.examples.steps.TextSteps;

public final class Example13RuntimeResetAndFreeze {
  private Example13RuntimeResetAndFreeze() {}

  public static void run() {
    // Build incrementally at runtime
    var rt = new RuntimePipeline<>("adhoc_session", /*shortCircuit=*/false, "   First   Input   ");
    rt.addPreAction(PolicySteps::rateLimit);
    rt.addStep(TextSteps::strip);
    rt.addStep(TextSteps::normalizeWhitespace);
    rt.addPostAction(PolicySteps::audit);
    System.out.println("[ex13-runtime] session1 -> " + rt.value());

    // Start another session with a different input
    rt.reset("   Second     Input   ");
    rt.addStep(TextSteps::truncateAt280); // may short-circuit if very long
    System.out.println("[ex13-runtime] session2 -> " + rt.value());

    // Freeze the same set of steps into a reusable immutable Pipeline
    Pipeline<String> immutable = rt.toImmutable(
        PolicySteps::rateLimit,
        TextSteps::strip,
        TextSteps::normalizeWhitespace,
        PolicySteps::audit,
        TextSteps::truncateAt280
    );
    String out = immutable.run("  Reusable   pipeline   input  ");
    System.out.println("[ex13-runtime] frozen -> " + out);
  }
}
```

> All links are **method references**; no inline lambdas.

------

## 2) Update the runner

Append the new calls at the end of **`pipeline-examples/src/main/java/com/pipeline/examples/ExamplesMain.java`**:

```java
// ...
Example10_DisruptorIntegration.run();

// New runtime examples
Example11RuntimeTextClean.run();
Example12RuntimeListFlow.run();
Example13RuntimeResetAndFreeze.run();

System.out.println("-- done --");
```

*(Keep class names exactly as above—no underscores.)*

------

## 3) Build & run (for verification)

```bash
./mvnw -q -DskipTests clean package
./mvnw -q -pl pipeline-examples exec:java -Dexec.mainClass=com.pipeline.examples.ExamplesMain
```

Expected new lines in output:

```
[ex11-runtime] -> ...
[ex12-runtime-1] -> [apple, orange]
[ex12-runtime-2] -> []
[ex13-runtime] session1 -> ...
[ex13-runtime] session2 -> ...
[ex13-runtime] frozen -> ...
```

------

## 4) Commit

```bash
git add -A
git commit -m "examples: add RuntimePipeline examples (ex11–ex13) and wire into runner; remove old underscore example"
git push -u origin feat/runtime-pipeline-examples
```

------

### Notes

- These examples stick to **unary** `RuntimePipeline<T>` only (as designed). Typed flows remain on `Pipe<I,O>`.
- If you later add a “recorded steps” list inside `RuntimePipeline`, we can change `toImmutable()` to emit the captured steps automatically. For now we pass the same method refs explicitly (clear and fast).

