# Codex Task — Add 10 method-based examples (no lambdas) to `pipeline-examples`

## Goals

- Create **10 runnable examples** that demonstrate common use cases.
- **No inline lambdas** in pipeline assembly: use **static/instance method references** (e.g., `TextSteps::strip`) or small `ThrowingFn` classes.
- Cover both **unary** (`Pipeline<T>`) and **typed** (`Pipe<I,O>`) pipelines, short‑circuiting, continue‑on‑error, before/after actions, remote step, JSON config loader, and Disruptor integration.
- Keep packages under `com.pipeline.examples` (steps under `com.pipeline.examples.steps`).
- Ensure `mvn -q -DskipTests package` passes and `ExamplesMain` runs.

------

## File & package layout (create if missing)

```
pipeline-examples/
  src/main/java/
    com/pipeline/examples/
      ExamplesMain.java
      Example01_TextClean.java
      Example02_ShortCircuitOnException.java
      Example03_CsvToJson.java
      Example04_FinanceOrderFlow.java
      Example05_TypedWithFallback.java
      Example06_PrePostPolicies.java
      Example07_ListDedupSort.java
      Example08_IntArrayStats.java
      Example09_LoadFromJsonConfig.java
      Example10_DisruptorIntegration.java
    com/pipeline/examples/steps/
      TextSteps.java
      CsvSteps.java
      JsonSteps.java
      FinanceSteps.java
      QuoteSteps.java
      PolicySteps.java
      ListSteps.java
      ArraySteps.java
      ErrorHandlers.java
    com/pipeline/examples/adapters/
      TextStripStep.java
      TextNormalizeStep.java
  src/main/resources/
    pipelines/clean_text.json
```

> **Note:** Packages elsewhere in the repo are already `com.pipeline.*`. Do **not** change them.

------

## Common constraint (apply to all examples)

- Build pipelines using **method references only**.
   ✅ `Pipeline.builder("ex").step(TextSteps::strip)`
   🚫 `s -> s.strip()`
- A “link” = a **public static method** (or an instance method on a class with a no‑arg ctor) with the signature `X method(X input) throws Exception` for unary, or `Y method(X input) throws Exception` for typed.
- Use `ShortCircuit.now(value)` to end early where stated.
- If you need a step class that implements `ThrowingFn`, write a small class — don’t use a lambda.

------

## Step classes (implement these methods)

Create these **utility step classes** under `com.pipeline.examples.steps`:

### `TextSteps`

```java
package com.pipeline.examples.steps;

import com.pipeline.core.ShortCircuit;

public final class TextSteps {
  private TextSteps() {}
  public static String strip(String s) throws Exception { return s == null ? "" : s.strip(); }
  public static String normalizeWhitespace(String s) throws Exception { return s.replaceAll("\\s+", " "); }
  /** Throws if the string contains emoji (simulates a validation error). */
  public static String disallowEmoji(String s) throws Exception {
    if (s.matches(".*\\p{So}.*")) throw new IllegalArgumentException("Emoji not allowed");
    return s;
  }
  /** If length exceeds 280, short-circuit the pipeline with a truncated value. */
  public static String truncateAt280(String s) throws Exception {
    return (s.length() > 280) ? ShortCircuit.now(s.substring(0, 280)) : s;
  }
  public static String upper(String s) throws Exception { return s.toUpperCase(); }
}
```

### `PolicySteps`

```java
package com.pipeline.examples.steps;

import java.util.concurrent.atomic.AtomicLong;

public final class PolicySteps {
  private static final AtomicLong LAST_NS = new AtomicLong(0);
  private static final long MIN_GAP_NS = 5_000_000; // ~200 qps demo throttle

  private PolicySteps() {}
  /** Simple local rate-limiter: drops to previous value if too soon. */
  public static String rateLimit(String s) throws Exception {
    long now = System.nanoTime();
    long last = LAST_NS.get();
    if (now - last < MIN_GAP_NS) return s; // pass through; real impl could ShortCircuit.now(...)
    LAST_NS.set(now);
    return s;
  }
  /** Minimal audit example (no-op; could log/metric). */
  public static String audit(String s) throws Exception { return s; }
}
```

### `CsvSteps` / `JsonSteps`

```java
package com.pipeline.examples.steps;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;

public final class CsvSteps {
  private CsvSteps() {}
  /** Very small CSV parser for "a,b\\n1,2" -> List<Map> with headers. */
  public static List<Map<String,String>> parse(String csv) throws Exception {
    List<Map<String,String>> out = new ArrayList<>();
    if (csv == null || csv.isBlank()) return out;
    String[] lines = csv.strip().split("\\R+");
    String[] headers = lines[0].split(",");
    for (int i=1; i<lines.length; i++) {
      String[] vals = lines[i].split(",");
      Map<String,String> row = new LinkedHashMap<>();
      for (int j=0; j<headers.length && j<vals.length; j++) row.put(headers[j].trim(), vals[j].trim());
      out.add(row);
    }
    return out;
  }
}

package com.pipeline.examples.steps;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;

public final class JsonSteps {
  private static final ObjectMapper M = new ObjectMapper();
  private JsonSteps() {}
  public static String toJson(List<Map<String,String>> rows) throws Exception { return M.writeValueAsString(rows); }
}
```

### `FinanceSteps`  (typed “order flow”)

```java
package com.pipeline.examples.steps;

public final class FinanceSteps {
  private FinanceSteps() {}
  // domain records
  public record Tick(String symbol, double price) {}
  public record Features(double r1, double vol) {}
  public record Score(double value) {}
  public sealed interface OrderResponse permits Accept, Reject {}
  public record Accept(String symbol, int qty, double price) implements OrderResponse {}
  public record Reject(String reason) implements OrderResponse {}

  public static Features computeFeatures(Tick t) throws Exception {
    double r1 = 0.0; double vol = Math.abs(t.price()) * 0.01;
    return new Features(r1, vol);
  }
  public static Score score(Features f) throws Exception { return new Score(Math.max(0, 1.0 - f.vol())); }
  public static OrderResponse decide(Score s) throws Exception {
    return s.value() >= 0.5 ? new Accept("AAPL", 10, 101.25) : new Reject("LowScore");
  }
}
```

### `QuoteSteps` + `ErrorHandlers`

```java
package com.pipeline.examples.steps;

import com.pipeline.core.ShortCircuit;

public final class QuoteSteps {
  private QuoteSteps() {}
  public record Req(String symbol, int qty) {}
  public sealed interface Res permits Ok, Rejected {}
  public record Ok(double px) implements Res {}
  public record Rejected(String reason) implements Res {}

  public static Req validate(Req r) throws Exception {
    if (r.qty() <= 0) return ShortCircuit.now(new Rejected("qty<=0"));
    if (r.symbol() == null || r.symbol().isBlank()) throw new IllegalArgumentException("no symbol");
    return r;
  }
  public static Ok price(Req r) throws Exception {
    if ("FAIL".equalsIgnoreCase(r.symbol())) throw new RuntimeException("pricing backend down");
    return new Ok(101.25);
  }
}

package com.pipeline.examples.steps;

public final class ErrorHandlers {
  private ErrorHandlers() {}
  public static QuoteSteps.Res quoteError(Exception e) {
    return new QuoteSteps.Rejected("PricingError: " + e.getMessage());
  }
}
```

### `ListSteps` / `ArraySteps`

```java
package com.pipeline.examples.steps;

import com.pipeline.core.ShortCircuit;
import java.util.*;
import java.util.stream.Collectors;

public final class ListSteps {
  private ListSteps() {}
  public static List<String> dedup(List<String> in) throws Exception {
    return new ArrayList<>(new LinkedHashSet<>(in));
  }
  public static List<String> sortNatural(List<String> in) throws Exception {
    return in.stream().sorted().collect(Collectors.toList());
  }
  public static List<String> nonEmptyOrShortCircuit(List<String> in) throws Exception {
    return in.isEmpty() ? ShortCircuit.now(in) : in;
  }
}

package com.pipeline.examples.steps;

public final class ArraySteps {
  private ArraySteps() {}
  public record Stats(int count, long sum, double avg, int max) {}
  public static int[] clipNegatives(int[] a) throws Exception {
    int[] out = a.clone();
    for (int i=0;i<out.length;i++) if (out[i] < 0) out[i] = 0;
    return out;
  }
  public static Stats stats(int[] a) throws Exception {
    if (a.length == 0) return new Stats(0, 0, 0, 0);
    long sum = 0; int max = Integer.MIN_VALUE;
    for (int v : a) { sum += v; if (v > max) max = v; }
    return new Stats(a.length, sum, sum / (double)a.length, max);
  }
}
```

------

## Adapter classes for JSON demo (implement `ThrowingFn`)

Under `com.pipeline.examples.adapters`:

```java
package com.pipeline.examples.adapters;

import com.pipeline.core.ThrowingFn;
import com.pipeline.examples.steps.TextSteps;

public final class TextStripStep implements ThrowingFn<String,String> {
  @Override public String apply(String in) throws Exception { return TextSteps.strip(in); }
}

package com.pipeline.examples.adapters;

import com.pipeline.core.ThrowingFn;
import com.pipeline.examples.steps.TextSteps;

public final class TextNormalizeStep implements ThrowingFn<String,String> {
  @Override public String apply(String in) throws Exception { return TextSteps.normalizeWhitespace(in); }
}
```

------

## The 10 examples (create these classes under `com.pipeline.examples`)

> Each class exposes a public static `void run()`; `ExamplesMain` calls them in order. Build pipelines via **method references** only.

1. **Example01_TextClean** — *Unary String; continue-on-error; explicit short-circuit when >280*

   - Build: `Pipeline.builder("ex01").shortCircuit(false)`
   - Steps (in order): `TextSteps::strip`, `TextSteps::normalizeWhitespace`, `TextSteps::truncateAt280`
   - Print the result for: `"  Hello   <b>World</b>  "`.

2. **Example02_ShortCircuitOnException** — *Unary String; shortCircuit=true; error stops early*

   - Steps: `TextSteps::disallowEmoji`, `TextSteps::upper`
   - Input includes an emoji to force an exception; show that output equals the **last good** value (the original input).

3. **Example03_CsvToJson** — *Typed `String -> List<Map<String,String>> -> String`*

   - Use `Pipe.named("ex03")`
   - Steps: `CsvSteps::parse`, `JsonSteps::toJson`
   - Input: `"name,age\nNektarios,49\nTheodore,7"`.

4. **Example04_FinanceOrderFlow** — *Typed domain flow to `OrderResponse`*

   - Types from `FinanceSteps` (`Tick -> Features -> Score -> OrderResponse`)
   - Steps: `FinanceSteps::computeFeatures`, `FinanceSteps::score`, `FinanceSteps::decide`.

5. **Example05_TypedWithFallback** — *Typed with `shortCircuit=true` + `onErrorReturn`*

   - Pipe `<QuoteSteps.Req, QuoteSteps.Res>`
   - Steps: `QuoteSteps::validate`, `QuoteSteps::price`
   - Set `onErrorReturn(ErrorHandlers::quoteError)` (method reference, not lambda).
   - Run with `new Req("FAIL", 10)` and show a `Rejected` message.

6. **Example06_PrePostPolicies** — *Unary String with before/after hooks*

   - Build: `.beforeEach(PolicySteps::rateLimit)`, steps: `TextSteps::strip`, `.afterEach(PolicySteps::audit)`
   - Show it runs and returns processed text.

7. **Example07_ListDedupSort** — *Unary `List<String>`*

   - Steps: `ListSteps::nonEmptyOrShortCircuit`, `ListSteps::dedup`, `ListSteps::sortNatural`
   - Input: `["orange","apple","orange"]`.

8. **Example08_IntArrayStats** — *Typed `int[] -> int[] -> Stats`*

   - Steps: `ArraySteps::clipNegatives`, `ArraySteps::stats`
   - Print the `Stats` record.

9. **Example09_LoadFromJsonConfig** — *Load unary pipeline from JSON (no lambdas)*

   - JSON file at `pipeline-examples/src/main/resources/pipelines/clean_text.json`:

     ```json
     {
       "pipeline": "json_clean_text",
       "type": "unary",
       "shortCircuit": false,
       "steps": [
         {"$local": "com.pipeline.examples.adapters.TextStripStep"},
         {"$local": "com.pipeline.examples.adapters.TextNormalizeStep"}
       ]
     }
     ```

   - Use `com.pipeline.config.PipelineJsonLoader.loadUnary(...)` to build and run it.

10. **Example10_DisruptorIntegration** — *Run Example01 pipeline through DisruptorEngine*

- Build `DisruptorEngine<String>` with buffer 1024 and publish ~50 strings `"hello i"`; sleep briefly; shutdown.

------

## `ExamplesMain` (runner)

Create `com.pipeline.examples.ExamplesMain`:

```java
package com.pipeline.examples;

public final class ExamplesMain {
  public static void main(String[] args) throws Exception {
    System.out.println("== Pipeline Services Examples ==");
    Example01_TextClean.run();
    Example02_ShortCircuitOnException.run();
    Example03_CsvToJson.run();
    Example04_FinanceOrderFlow.run();
    Example05_TypedWithFallback.run();
    Example06_PrePostPolicies.run();
    Example07_ListDedupSort.run();
    Example08_IntArrayStats.run();
    Example09_LoadFromJsonConfig.run();
    Example10_DisruptorIntegration.run();
    System.out.println("-- done --");
  }
}
```

------

## `pipeline-examples/pom.xml` (ensure exec plugin can run the main)

If not already set, in `pipeline-examples/pom.xml` include:

```xml
<build>
  <plugins>
    <plugin>
      <groupId>org.codehaus.mojo</groupId>
      <artifactId>exec-maven-plugin</artifactId>
      <executions>
        <execution>
          <id>run-examples</id>
          <phase>none</phase>
          <goals><goal>java</goal></goals>
          <configuration>
            <mainClass>com.pipeline.examples.ExamplesMain</mainClass>
          </configuration>
        </execution>
      </executions>
    </plugin>
  </plugins>
</build>
```

------

## Acceptance criteria

- No inline lambdas inside example pipeline assembly; all links are **method references** or small `ThrowingFn` classes.
- `./mvnw -q -DskipTests package` succeeds.
- `./mvnw -q -pl pipeline-examples exec:java -Dexec.mainClass=com.pipeline.examples.ExamplesMain` runs and prints output for all 10 (remote calls are **not** required).
- Example09 loads from JSON using adapter classes (shows per‑pipeline JSON is supported without lambdas).

------

## Notes

- Keep examples self-contained; avoid network dependency.
- Use only dependencies already present in the build (Jackson, Micrometer).
- Keep code style clean and JDK 21 compatible.

