# Pipeline Services (PS)

**Implementation Requirements — v0.1 (for Codex CLI)**
 Target: **Java 21**, Gradle build, JUnit 5 tests, Apache‑2.0 license

## 0) Guiding principles

- **Simplicity first.** Code‑first API with tiny mental model. JSON is **optional**, and when used it is **one file per pipeline**.
- **One control concept:** `shortCircuit` (boolean) determines how the runtime handles exceptions in steps. Developers can also **force** a short‑circuit explicitly from a step.
- **Three step kinds:**
  1. **local** — handwritten or scaffolded Java step
  2. **prompt** — step whose Java implementation is **generated at build time** from a prompt spec
  3. **remote** — proxy step invoking an external service (HTTP/gRPC), same runtime contract
- **Local‑first performance.** Runs in-process by default; optional Disruptor adapter for hot paths.
- **Deterministic by default** for prompt‑generated code (no I/O, no randomness).
- **Observability built‑in** via Micrometer (metrics) with an easy switch to OpenTelemetry later.

------

## 1) Repository layout (multi-module Gradle)

```
ps/
├─ ps-core/                 # core runtime (Pipeline<T>, Pipe<I,O>, ShortCircuit, Steps, Registry)
├─ ps-config/               # JSON loader + JSON Schemas + example configs
├─ ps-prompt/               # prompt step codegen (Codex integration) + manifest/provenance
├─ ps-remote/               # HTTP (and gRPC placeholder) remote step adapters
├─ ps-disruptor/            # optional Disruptor engine adapter for low latency
├─ ps-examples/             # runnable samples (unary text, typed quote, remote call)
└─ buildSrc/                # Gradle plugin tasks: `psGenerate`, `psVerify`
```

------

## 2) Public API (ps-core)

### 2.1 Core types

```java
package com.ps.core;

@FunctionalInterface
public interface ThrowingFn<I,O> {
  O apply(I in) throws Exception;
}

public final class ShortCircuit {
  private ShortCircuit() {}
  static final class Signal extends RuntimeException {
    final Object value; Signal(Object v) { this.value = v; }
  }
  /** Force an immediate pipeline return with the provided final value. */
  @SuppressWarnings("unchecked")
  public static <T> T now(T finalValue) { throw new Signal(finalValue); }
}
```

> No context object required. Steps can be simple lambdas. If a step wants to end early, it calls `ShortCircuit.now(value)`.

------

### 2.2 Unary pipelines (same input/output type)

```java
package com.ps.core;

import java.util.List;
import java.util.function.Function;

public final class Pipeline<T> {
  private final String name;
  private final List<ThrowingFn<T,T>> steps;
  private final boolean shortCircuit;

  private Pipeline(String name, boolean shortCircuit, List<ThrowingFn<T,T>> steps) { ... }

  /** Var-args builder for convenience (mirrors existing style). */
  @SafeVarargs
  public static <T> Pipeline<T> build(String name, boolean shortCircuit, ThrowingFn<T,T>... steps) { ... }

  /** Builder API (for chaining, pre/post, etc.). */
  public static <T> Builder<T> builder(String name) { ... }
  public static final class Builder<T> {
    public Builder<T> shortCircuit(boolean b) { ... }
    public Builder<T> beforeEach(ThrowingFn<T,T> pre) { ... }
    public Builder<T> step(ThrowingFn<T,T> s) { ... }
    public Builder<T> afterEach(ThrowingFn<T,T> post) { ... }
    public Pipeline<T> build() { ... }
  }

  /** Run the pipeline. Honors shortCircuit flag and ShortCircuit.now(). */
  public T run(T input) { ... }

  public String name() { return name; }
  public boolean shortCircuit() { return shortCircuit; }
  public int size() { return steps.size(); }
}
```

**Required behavior**

- If `shortCircuit == true`: any thrown `Exception` **ends the run immediately** and returns the **last good `T`**.
- If `shortCircuit == false`: **skip the failing step** (keep current value) and continue.
- If a step calls `ShortCircuit.now(x)`: return `x` immediately (regardless of flag).

------

### 2.3 Typed pipelines (different I/O types)

```java
package com.ps.core;

public final class Pipe<I,O> {
  private Pipe() {}
  public static <I> Builder<I> from(Class<I> inType) { return new Builder<>(); }

  public static final class Builder<I> {
    private boolean shortCircuit = true;
    private final java.util.List<ThrowingFn<?,?>> steps = new java.util.ArrayList<>();
    private java.util.function.Function<Exception,?> onErrorReturn;

    public Builder<I> shortCircuit(boolean b) { ... }
    public <O> Builder<I> onErrorReturn(java.util.function.Function<Exception,O> f) { ... }
    public <M> Builder<M> step(ThrowingFn<I,M> s) { ... }
    public <O> Pipe<I,O> to(Class<O> outType) { ... }
  }

  public O run(I in) throws Exception { ... }
}
```

**Required behavior**

- If `shortCircuit == true` and a step throws:
  - If `onErrorReturn` is provided → return that `O`.
  - Else rethrow (no safe way to produce a final `O`).
- If `shortCircuit == false` and a step throws:
  - **Keep current value** and continue; developers should wrap steps that *must* produce an output with a fallback (see helpers below).
- If a step calls `ShortCircuit.now(x)` (where `x` is type‑compatible with final `O`) → return `x` immediately.

------

### 2.4 Helper utilities

```java
package com.ps.core;

public final class Steps {
  private Steps(){}

  /** Unary: ignore errors, pass input through unchanged. */
  public static <T> ThrowingFn<T,T> ignoreErrors(ThrowingFn<T,T> step) { ... }

  /** Typed: if step throws, use fallback to produce required O. */
  public static <I,O> ThrowingFn<I,O> withFallback(ThrowingFn<I,O> step,
                                                   java.util.function.Function<Exception,O> fallback) { ... }
}
```

------

### 2.5 Registry (optional but recommended)

```java
package com.ps.core;

import java.util.Map;
import java.util.Optional;

public final class PipelineRegistry {
  public void register(String key, Pipeline<String> pipeline) { ... }
  public Optional<Pipeline<String>> lookup(String key) { ... }
  public Map<String, Pipeline<String>> asMap() { ... }
  public int size() { ... }
}
```

------

## 3) Step kinds

### 3.1 Local step

- A normal Java lambda or class implementing `ThrowingFn<I,O>`.
- May throw exceptions or call `ShortCircuit.now(finalValue)` to end.

### 3.2 Prompt step (build‑time code generation)

- Declared either **inline in code** via a builder (`Prompt.step(...)`) or **inside the pipeline JSON** (see §4).
- Codex must:
  1. Generate a **final Java class** implementing `ThrowingFn<I,O>`, placed under `ps-prompt/target/generated-sources/ps/`.
  2. Enforce constraints (deterministic, no I/O, no network, no threads, no randomness).
  3. Generate unit tests from examples + property checks.
  4. Optionally generate a tiny JMH microbench (smoke).
  5. Produce a **manifest** (`ps-manifest.json`) capturing:
     - pipeline name, step name, input/output types
     - prompt text, rules, examples
     - model info (id/version), temperature/seed if applicable
     - SHA‑256 of the generated source + compile artifact timestamp

**Prompt builder (code‑side):**

```java
package com.ps.prompt;

import com.ps.core.ThrowingFn;

public final class Prompt {
  public static <I,O> PromptBuilder<I,O> step(Class<I> in, Class<O> out) { ... }

  public static final class PromptBuilder<I,O> {
    public PromptBuilder<I,O> name(String stepName) { ... }
    public PromptBuilder<I,O> goal(String text) { ... }
    public PromptBuilder<I,O> rules(String... lines) { ... }        // "deterministic", "no IO", etc.
    public PromptBuilder<I,O> example(I input, O expected) { ... }  // multiple allowed
    public PromptBuilder<I,O> property(String assertion) { ... }    // e.g., "volatility>=0"
    public PromptBuilder<I,O> p50Micros(int budget) { ... }
    /** Returns a placeholder that Codex replaces with the generated class at build time. */
    public ThrowingFn<I,O> build() { ... }
  }
}
```

**Codex tasks**

- `./gradlew psGenerate`: scan classpath for `Prompt.step(...).build()` usage **or** load pipeline JSON files in `ps-config/` and emit generated sources + tests + manifest.
- `./gradlew psVerify`: compile + run tests; fail build if any generated step violates constraints.

### 3.3 Remote step

- A proxy that calls an external endpoint and returns `O`.
- Two protocols for v0.1:
  - `http`: JSON POST/GET
  - `grpc`: placeholder interface + TODO; wire in later
- Required options:
  - `endpoint`, `method`, `timeoutMillis` (default 1000), `retries` (default 0)
  - Headers (static map), query params, **serialization hooks** (map `I` → request body; map response body → `O`)
- Error mapping:
  - Network/timeouts map to `Exception` (triggering short‑circuit if `shortCircuit==true`).
  - If `shortCircuit==false`, developers can wrap with `Steps.withFallback(...)`.

**HTTP adapter signature**

```java
package com.ps.remote.http;

import com.ps.core.ThrowingFn;
public final class HttpStep {
  public static <I,O> ThrowingFn<I,O> jsonPost(RemoteSpec<I,O> spec) { ... }
  public static <I,O> ThrowingFn<I,O> jsonGet(RemoteSpec<I,O> spec) { ... }

  public static final class RemoteSpec<I,O> {
    public String endpoint;
    public int timeoutMillis = 1000;
    public int retries = 0;
    public java.util.Map<String,String> headers = java.util.Map.of();
    public java.util.function.Function<I, String> toJson;   // I -> JSON body or query
    public java.util.function.Function<String, O> fromJson; // JSON -> O
  }
}
```

------

## 4) Pipeline JSON (ps-config)

**One JSON per pipeline.** Steps are defined inline (no per‑step files). JSON mirrors the code API.

### 4.1 Schema (informal)

```jsonc
{
  "pipeline": "string",           // unique name
  "type": "unary" | "typed",
  "inType": "fqcn or simple",     // required if type == "typed"
  "outType": "fqcn or simple",    // required if type == "typed"
  "shortCircuit": true,           // default true
  "beforeEach": [                 // optional unary steps applied to the whole pipeline
    { "$local": "com.example.RateLimit" },
    { "$prompt": { /* same structure as prompt step below */ } }
  ],
  "steps": [
    { "$local": "com.example.Normalize" },
    {
      "$prompt": {
        "name": "computeFeatures",
        "inType": "com.example.Tick",
        "outType": "com.example.Features",
        "goal": "Compute rolling returns and volatility (deterministic, no I/O).",
        "rules": ["deterministic", "no IO", "no randomness", "latency.p50<=100us"],
        "examples": [
          { "in": { /* Tick */ }, "out": { /* Features */ } }
        ],
        "properties": ["out.volatility >= 0"]
      }
    },
    {
      "$remote": {
        "protocol": "http",
        "method": "POST",
        "endpoint": "https://api.example.com/score",
        "timeoutMillis": 800,
        "retries": 1,
        "headers": { "X-API-Key": "${SCORE_KEY}" },
        "toJson": "inline or class ref",      // Codex may scaffold a mapper class if 'inline'
        "fromJson": "inline or class ref"
      }
    }
  ],
  "afterEach": [
    { "$local": "com.example.Audit" }
  ]
}
```

**Loader behavior**

- Resolve class names via ClassLoader when `$local` is used.
- For `$prompt`, feed prompt spec to Codex codegen (psGenerate), compile, then replace with the generated class.
- For `$remote`, build an adapter instance using `ps-remote` helpers.
- Support `${ENV_VAR}` substitution in strings.

**Note:** JSON is **optional**; any pipeline can be built programmatically with the same semantics.

------

## 5) Disruptor adapter (ps-disruptor, optional)

- Provide a wrapper that accepts a `Pipeline<T>` or `Pipe<I,O>` and executes it inside an LMAX Disruptor ring buffer for low‑latency use cases.
- Required API:

```java
package com.ps.disruptor;

public final class DisruptorEngine<T> implements AutoCloseable {
  public DisruptorEngine(String name, int bufferSize, com.ps.core.Pipeline<T> pipeline, com.ps.metrics.MetricsRecorder metrics) { ... }
  public void publish(T payload) { ... }
  public void close() { shutdown(); }
  public void shutdown() { ... }
}
```

- If a step calls `ShortCircuit.now(...)` or throws and `shortCircuit==true`, the event handler must **return immediately**; increment a counter `ps.short_circuits` for the pipeline.

------

## 6) Observability (Micrometer baseline)

- Module `ps-core` exposes timers and counters through a simple `MetricsRecorder` SPI:

```java
package com.ps.metrics;

import io.micrometer.core.instrument.MeterRegistry;

public interface MetricsRecorder {
  void onStepSuccess(String pipeline, String stepName, long nanos);
  void onStepError(String pipeline, String stepName, Throwable t);
  void onShortCircuit(String pipeline, String stepName);
  MeterRegistry registry();
}
```

- Default implementation: Micrometer `SimpleMeterRegistry`.
- Metric names (prefix `ps.pipeline.<name>.step.<idx>.<shortname>.*`):
  - `duration` (timer), `errors` (counter), `short_circuits` (counter).

------

## 7) Error & short‑circuit semantics (must‑pass tests)

**Unary `Pipeline<T>`**

- `shortCircuit=true`
  - If step throws → return last good `T`.
  - If step calls `ShortCircuit.now(x)` → return `x`.
- `shortCircuit=false`
  - If step throws → skip step, continue with current `T`.
  - If step calls `ShortCircuit.now(x)` → return `x`.

**Typed `Pipe<I,O>`**

- `shortCircuit=true`
  - If step throws → if `onErrorReturn` present, return it; else rethrow.
  - If step calls `ShortCircuit.now(x)` → return `x` (must be assignable to `O`).
- `shortCircuit=false`
  - If step throws → keep current value; use `Steps.withFallback` when next step needs an `O`.

------

## 8) Build integration (Gradle)

**Tasks (in buildSrc plugin)**

- `psGenerate`
  - Scan bytecode for `Prompt.step(...).build()`; collect pipeline JSONs in `ps-config/`.
  - For each **prompt** step: generate Java source + tests + manifest into `ps-prompt/build/generated/sources/ps/`.
  - For each **remote** step with inline mapping: scaffold mapper classes (toJson/fromJson) if not provided.
- `psVerify`
  - Compile generated code and run tests (fail the build on any failure).
  - Optionally run a JMH smoke bench when `-PpsBench` is set.

**Outputs**

- `ps-prompt/build/ps-manifest.json` (array of generated steps with provenance).
- Java classes available on the main classpath via `sourceSets.main.java.srcDir`.

------

## 9) Security/Determinism rules for generated code

- Disallow: `java.io`, `java.net`, `java.nio.file`, `System.currentTimeMillis()`, randomness (`java.util.Random`, `SecureRandom`).
- Disallow reflection and thread creation.
- Limit imports to: `java.lang`, `java.util` (whitelist: math, arrays, primitives).
- Enforce with a static code scanner (simple AST/string checks) before compile.
- Every generated class must be **`final`**, no static mutable state, and **no external dependencies**.

------

## 10) Examples (ps-examples)

### 10.1 Unary text cleaner (local + prompt + short‑circuit)

```java
import static com.ps.core.ShortCircuit.now;
import static com.ps.core.Steps.ignoreErrors;

var p = Pipeline.build("clean_text", false,
  (String s) -> s.strip(),
  ignoreErrors((String s) -> riskyNormalize(s)),              // continues on error
  (String s) -> s.length() > 280 ? now(s.substring(0, 280)) : s
);
System.out.println(p.run("  Hello   <b>World</b>  "));
```

### 10.2 Typed quote pipeline (local + prompt)

```java
record Req(String symbol, int qty) {}
sealed interface Res permits Ok,Rejected {}
record Ok(double px) implements Res {}
record Rejected(String reason) implements Res {}

var pipe =
  Pipe.from(Req.class)
      .step((Req r) -> { if (r.qty()<=0) return ShortCircuit.now(new Rejected("qty<=0")); return r; })
      .step(com.ps.prompt.Prompt.step(Req.class, Ok.class)
            .name("price")
            .goal("Return a deterministic demo price for a symbol")
            .rules("deterministic","no IO")
            .example(new Req("AAPL",10), new Ok(101.25))
            .build())
      .shortCircuit(true)
      .onErrorReturn(e -> new Rejected("PricingError: "+e.getMessage()))
      .to(Res.class);

Res r = pipe.run(new Req("AAPL", 10)); // Ok(101.25)
```

### 10.3 Pipeline JSON (unary)

```json
{
  "pipeline": "clean_text",
  "type": "unary",
  "shortCircuit": false,
  "beforeEach": [],
  "steps": [
    { "$local": "com.example.Strip" },
    { "$prompt": {
        "name": "normalize",
        "inType": "java.lang.String",
        "outType": "java.lang.String",
        "goal": "Normalize whitespace, remove HTML tags. Deterministic, no I/O.",
        "rules": ["deterministic","no IO"],
        "examples": [["  Hello <b>World</b>  ", "Hello World"]]
    } }
  ],
  "afterEach": []
}
```

------

## 11) Tests (must implement)

- **Unary behavior**
  - `shortCircuit=true` returns last good value on thrown exception.
  - `shortCircuit=false` continues and reaches subsequent steps.
  - `ShortCircuit.now(x)` returns `x` immediately in both modes.
- **Typed behavior**
  - With `shortCircuit=true` and `onErrorReturn`, returns mapped `O`.
  - Without `onErrorReturn`, rethrows.
  - With `shortCircuit=false`, `Steps.withFallback` recovers to keep type‑safety.
- **Prompt steps**
  - Generated class compiles, passes provided examples and properties.
  - Disallowed APIs are rejected (static check).
- **Remote steps**
  - Timeout → exception → short‑circuit when flag true; verified counters incremented.

------

## 12) Metrics & logging

- Increment `ps.pipeline.<name>.step.<n>.<id>.short_circuits` when:
  - A `ShortCircuit.now` is triggered, or
  - A step throws and `shortCircuit==true`.
- Record step duration as a timer; record errors as a counter.
- Minimal SLF4J logging: pipeline start/finish at DEBUG, error with cause at WARN.

------

## 13) Compatibility with existing low‑latency code

- The unary API mirrors your current pattern (`Pipeline.build(name, shortCircuit, steps...)`).
- The Disruptor adapter executes `pipeline.run(payload)` inside the handler and respects short‑circuit semantics.

------

## 14) Roadmap (for Codex to keep in mind)

- **v0.1** (this spec): core, JSON loader, prompt codegen, HTTP remote, Disruptor adapter, metrics, examples, tests.
- **v0.2**: template pipelines (parameterized before/after sets), OpenTelemetry spans, gRPC support.
- **v0.3**: replay mode (deterministic journaling) and sandboxed classloader for generated code (defense‑in‑depth).

------

## 15) Acceptance criteria (done = true when)

1. All public APIs above exist and match semantics.
2. `psGenerate` produces compilable Java for prompt steps, unit tests, and a manifest.
3. `psVerify` runs and enforces constraints; build fails on violations.
4. Example pipelines run as documented and tests pass.
5. Metrics counters/timers are exposed via Micrometer’s `SimpleMeterRegistry`.
6. Disruptor adapter runs a unary pipeline at ~sub‑millisecond p50 on a synthetic load (non‑binding target; smoke test only).

------

### Notes for Codex

- Keep identifiers concise and consistent (`shortCircuit` everywhere).
- Prefer **final classes**, immutable fields, and records where suitable.
- Avoid any dependency creep in `ps-core`. Remote HTTP can use Java 21 `HttpClient`.
- When generating code for prompt steps, stick to **pure, allocation‑light** algorithms; fail generation if constraints are ambiguous.
- Variable names must be descriptive.
- Do not use single name variables unless it's an i in a for loop. 

