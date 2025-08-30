## 📦 PR brief for Codex

**Branch:** `fix/prompt-java-and-ex8-generic`

### A) Replace `pipeline-prompt/src/main/java/com/pipeline/prompt/Prompt.java`

```java
package com.pipeline.prompt;

import com.pipeline.core.ThrowingFn;
import java.util.ArrayList;
import java.util.List;

public final class Prompt {
  private Prompt() {}

  public static <I, O> PromptBuilder<I, O> step(Class<I> in, Class<O> out) {
    return new PromptBuilder<>();
  }

  public static final class PromptBuilder<I, O> {
    private String name = "promptStep";
    private String goal = "";
    private final List<String> rules = new ArrayList<>();
    private final List<Example<I, O>> examples = new ArrayList<>();
    private final List<String> properties = new ArrayList<>();
    private int p50Micros = 0;

    public PromptBuilder<I, O> name(String stepName) { this.name = stepName; return this; }
    public PromptBuilder<I, O> goal(String text) { this.goal = text; return this; }
    public PromptBuilder<I, O> rules(String... lines) {
      if (lines != null) for (String s : lines) rules.add(s);
      return this;
    }
    public PromptBuilder<I, O> example(I input, O expected) {
      examples.add(new Example<>(input, expected));
      return this;
    }
    public PromptBuilder<I, O> property(String assertion) { properties.add(assertion); return this; }
    public PromptBuilder<I, O> p50Micros(int budget) { this.p50Micros = budget; return this; }

    public ThrowingFn<I, O> build() {
      // Placeholder; build-time codegen should replace this implementation.
      return in -> {
        throw new UnsupportedOperationException(
            "Prompt-generated code not available for step '" + name + "'");
      };
    }

    record Example<I, O>(I in, O out) {}
  }
}
```

### B) Fix generics in `Example08IntArrayStats.java`

**File:** `pipeline-examples/src/main/java/com/pipeline/examples/Example08_IntArrayStats.java`

```java
package com.pipeline.examples;

import com.pipeline.core.Pipe;
import com.pipeline.examples.steps.ArraySteps;

public final class Example08_IntArrayStats {
  private Example08IntArrayStats() {}

  public static void run() throws Exception {
    Pipe<int[], ArraySteps.Stats> pipe = Pipe.<int[]>named("ex08")
        .step(ArraySteps::clipNegatives)
        .step(ArraySteps::stats)
        .to(ArraySteps.Stats.class);

    int[] in = new int[] { 5, -2, 10, 3 };
    var stats = pipe.run(in);
    System.out.println("[ex08] => " + stats);
  }
}
```

### C) Remove build artifacts from git (keep `.gitignore` as-is)

```bash
git rm -r --cached pipeline-core/target pipeline-config/target pipeline-remote/target \
  pipeline-prompt/target pipeline-disruptor/target pipeline-examples/target
```

*(You already ignore `target/`; this ensures the repo is clean.)*

### D) Verify

```bash
./mvnw -q -DskipTests clean package
./mvnw -q -pl pipeline-examples exec:java -Dexec.mainClass=com.pipeline.examples.ExamplesMain
```

------

## Notes / validation I ran

- **Typed builder check:** `Pipe.java` has `class Builder<I, C>`, `step(...)` returns `Builder<I, M>`, and `to(...)` returns `Pipe<I,O>`. ✔️
- **Examples lambdas:** The only `->` in examples are **comments/strings**, not code. ✔️
- **JSON demo:** `pipeline-examples/src/main/resources/pipelines/clean_text.json` exists and loads via `PipelineJsonLoader`. ✔️
- **SLF4J:** `pipeline-core` depends on `slf4j-api` (found in its POM). ✔️

