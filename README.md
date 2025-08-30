# Pipeline Services
**Functional Pipeline Framework for Java 21.**  
Local‑first pipelines with `shortCircuit`, prompt‑to‑code (build time), and optional remote adapters.

 Build **unary** (`T → T`) and **typed** (`I → … → O`) pipelines with one clear control: **`shortCircuit`**.
 Steps can be **local methods**, **build‑time prompt‑generated code** (scaffold in place), or **remote adapters**.

> No runtime LLM calls. JSON config is **optional** and per‑pipeline.

------

## Contents

- [Why](https://chatgpt.com/g/g-p-68b238013c5c819194a696bf35f9f2e9/c/68b1c630-6a80-8327-a8cb-cdb66c9c6ada#why)
- [Features at a glance](https://chatgpt.com/g/g-p-68b238013c5c819194a696bf35f9f2e9/c/68b1c630-6a80-8327-a8cb-cdb66c9c6ada#features-at-a-glance)
- [Modules](https://chatgpt.com/g/g-p-68b238013c5c819194a696bf35f9f2e9/c/68b1c630-6a80-8327-a8cb-cdb66c9c6ada#modules)
- [Install & build](https://chatgpt.com/g/g-p-68b238013c5c819194a696bf35f9f2e9/c/68b1c630-6a80-8327-a8cb-cdb66c9c6ada#install--build)
- [Quick start](https://chatgpt.com/g/g-p-68b238013c5c819194a696bf35f9f2e9/c/68b1c630-6a80-8327-a8cb-cdb66c9c6ada#quick-start)
  - [Unary pipelines with `Pipeline`](https://chatgpt.com/g/g-p-68b238013c5c819194a696bf35f9f2e9/c/68b1c630-6a80-8327-a8cb-cdb66c9c6ada#unary-pipelines-with-pipelinet)
  - [Typed pipelines with `Pipe`](https://chatgpt.com/g/g-p-68b238013c5c819194a696bf35f9f2e9/c/68b1c630-6a80-8327-a8cb-cdb66c9c6ada#typed-pipelines-with-pipeio)
  - [Imperative runtime style with `RuntimePipeline`](https://chatgpt.com/g/g-p-68b238013c5c819194a696bf35f9f2e9/c/68b1c630-6a80-8327-a8cb-cdb66c9c6ada#imperative-runtime-style-with-runtimepipelinet)
  - [Per‑pipeline JSON config (optional)](https://chatgpt.com/g/g-p-68b238013c5c819194a696bf35f9f2e9/c/68b1c630-6a80-8327-a8cb-cdb66c9c6ada#per-pipeline-json-config-optional)
  - [HTTP remote step](https://chatgpt.com/g/g-p-68b238013c5c819194a696bf35f9f2e9/c/68b1c630-6a80-8327-a8cb-cdb66c9c6ada#http-remote-step)
- [Short‑circuit semantics](https://chatgpt.com/g/g-p-68b238013c5c819194a696bf35f9f2e9/c/68b1c630-6a80-8327-a8cb-cdb66c9c6ada#shortcircuit-semantics)
- [Prompt‑generated steps (scaffold)](https://chatgpt.com/g/g-p-68b238013c5c819194a696bf35f9f2e9/c/68b1c630-6a80-8327-a8cb-cdb66c9c6ada#prompt-generated-steps-scaffold)
- [Metrics](https://chatgpt.com/g/g-p-68b238013c5c819194a696bf35f9f2e9/c/68b1c630-6a80-8327-a8cb-cdb66c9c6ada#metrics)
- [Examples](https://chatgpt.com/g/g-p-68b238013c5c819194a696bf35f9f2e9/c/68b1c630-6a80-8327-a8cb-cdb66c9c6ada#examples)
- [Roadmap](https://chatgpt.com/g/g-p-68b238013c5c819194a696bf35f9f2e9/c/68b1c630-6a80-8327-a8cb-cdb66c9c6ada#roadmap)
- [Contributing](https://chatgpt.com/g/g-p-68b238013c5c819194a696bf35f9f2e9/c/68b1c630-6a80-8327-a8cb-cdb66c9c6ada#contributing)
- [License](https://chatgpt.com/g/g-p-68b238013c5c819194a696bf35f9f2e9/c/68b1c630-6a80-8327-a8cb-cdb66c9c6ada#license)

------

## Why

Microservice fatigue is real. Pipelines give you **structured, observable composition** inside a modular monolith without giving up clarity or performance. Every link runs locally by default; you can lift a link to a remote call **only when it pays**.

------

## Features at a glance

- **Code‑first API**. JSON is optional (one file per pipeline).
- **Three step kinds**
  1. **Local** – plain Java methods or small classes
  2. **Prompt** – build‑time “prompt → code” (scaffolded)
  3. **Remote** – HTTP adapter (gRPC placeholder)
- **One control concept:** `shortCircuit`
  - *Implicit:* exceptions end the run (or continue, your choice)
  - *Explicit:* `ShortCircuit.now(value)` ends immediately with `value`
- **Two styles**
  - Immutable builders: `Pipeline<T>` and `Pipe<I,O>`
  - Runtime‑friendly: `RuntimePipeline<T>` (imperative `add*` that returns current value)
- **Observability** via Micrometer (swapable recorder)
- **Low‑latency runner** wrapper (simple single‑thread engine today; Disruptor planned)

------

## Modules

```
pipeline-core        # Core runtime: Pipeline, Pipe, RuntimePipeline, ShortCircuit, Steps, Metrics
pipeline-config      # Optional JSON loader (one JSON per pipeline)
pipeline-remote      # HTTP step adapter (json GET/POST)
pipeline-prompt      # Prompt builder + codegen entrypoint scaffold
pipeline-disruptor   # Lightweight runner wrapper (single-thread for now)
pipeline-examples    # 13 runnable examples (+ main runner)
```

**Packages** are under `com.pipeline.*` (e.g., `com.pipeline.core.Pipeline`).

------

## Install & build

Requirements:

- Java 21+
- Maven 3.9+ (wrapper included)

Build everything:

```bash
./mvnw -q -DskipTests clean package
```

Run all examples:

```bash
./mvnw -q -pl pipeline-examples exec:java \
  -Dexec.mainClass=com.pipeline.examples.ExamplesMain
```

> Windows: use `mvnw.cmd`.

------

## Quick start

### Unary pipelines with `Pipeline<T>`

```java
import com.pipeline.core.Pipeline;
import com.pipeline.core.ShortCircuit;
import com.pipeline.examples.steps.TextSteps;
import com.pipeline.examples.steps.PolicySteps;

Pipeline<String> p =
    Pipeline.builder("clean_text")
        .shortCircuit(false)                 // continue even if a step throws
        .beforeEach(PolicySteps::rateLimit)  // pre-action
        .step(TextSteps::strip)
        .step(TextSteps::normalizeWhitespace)
        .step(TextSteps::truncateAt280)      // may call ShortCircuit.now(truncated)
        .afterEach(PolicySteps::audit)       // post-action
        .build();

String out = p.run("  Hello   World  ");
```

> Prefer the old UBS names? Aliases exist: `addPreAction`, `addStep`, `addPostAction`.

------

### Typed pipelines with `Pipe<I,O>`

```java
import com.pipeline.core.Pipe;
import com.pipeline.examples.steps.FinanceSteps;
import com.pipeline.examples.steps.FinanceSteps.*;

Pipe<Tick, OrderResponse> pipe =
    Pipe.<Tick>named("order_flow")
        .step(FinanceSteps::computeFeatures)  // Tick -> Features
        .step(FinanceSteps::score)            // Features -> Score
        .step(FinanceSteps::decide)           // Score -> OrderResponse
        .to(OrderResponse.class);

OrderResponse out = pipe.run(new Tick("AAPL", 101.25));
```

**Failure policy** (typed):

```java
import com.pipeline.examples.steps.QuoteSteps;
import com.pipeline.examples.steps.ErrorHandlers;

var quote =
  Pipe.<QuoteSteps.Req>named("quote")
      .shortCircuit(true)                                 // exceptions end the run
      .onErrorReturn(ErrorHandlers::quoteError)           // final O when something throws
      .step(QuoteSteps::validate)                         // may ShortCircuit.now(Rejected)
      .step(QuoteSteps::price)
      .to(QuoteSteps.Res.class);
```

------

### Imperative runtime style with `RuntimePipeline<T>`

For interactive sessions or per‑request composition:

```java
import com.pipeline.core.RuntimePipeline;
import com.pipeline.examples.steps.TextSteps;

var rt = new RuntimePipeline<>("adhoc_text", /*shortCircuit=*/false, "  Hello   World  ");
rt.addPreAction(com.pipeline.examples.steps.PolicySteps::rateLimit);
rt.addStep(TextSteps::strip);
rt.addStep(TextSteps::normalizeWhitespace);
rt.addPostAction(com.pipeline.examples.steps.PolicySteps::audit);

String valueNow = rt.value();     // current output
rt.reset("  Another   Input ");   // start a new run
```

You can “freeze” a runtime session into an immutable pipeline:

```java
var frozen = rt.toImmutable(); // or rt.freeze()
```

------

### Per‑pipeline JSON config (optional)

A tiny JSON maps directly to the Java API; one file per pipeline:

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

Load and run:

```java
import com.pipeline.config.PipelineJsonLoader;
try (var in = getClass().getResourceAsStream("/pipelines/json_clean_text.json")) {
  var p = PipelineJsonLoader.loadUnary(in);
  System.out.println(p.run("  Hello   World  "));
}
```

------

### HTTP remote step

```java
import com.pipeline.core.Pipe;
import com.pipeline.remote.http.HttpStep;

record Req(String q) {}
record Res(String body) {}

var spec = new HttpStep.RemoteSpec<Req, Res>();
spec.endpoint = "https://httpbin.org/post";
spec.timeoutMillis = 800;
spec.retries = 1;
spec.headers = java.util.Map.of("X-Demo", "pipeline");
spec.toJson = r -> "{\"q\":\"" + r.q() + "\"}";
spec.fromJson = s -> new Res(s);

Pipe<Req, Res> remote =
  Pipe.<Req>named("remote_demo")
      .step(HttpStep.jsonPost(spec))
      .to(Res.class);

Res out = remote.run(new Req("hello"));
```

------

## `shortCircuit` semantics

**Explicit short‑circuit**

```java
import com.pipeline.core.ShortCircuit;
// from inside any step:
return ShortCircuit.now(finalValue); // ends the whole run immediately with finalValue
```

**Implicit policy**

- `shortCircuit = true` (default)

  - **Unary `Pipeline<T>`**: on exception, return **last good `T`**.
  - **Typed `Pipe<I,O>`**: on exception, if `onErrorReturn` is provided → return that `O`; otherwise rethrow.

- `shortCircuit = false`

  - **Unary**: skip the failing step; continue with current `T`.

  - **Typed**: keep current value; if a step *must* produce `O`, wrap it with a fallback:

    ```java
    import com.pipeline.core.Steps;
    .step(Steps.withFallback(step, e -> defaultO))
    ```

------

## Prompt‑generated steps (scaffold)

The `pipeline-prompt` module contains the `Prompt` builder and a `CodegenMain` entrypoint to support **build‑time** “prompt → code” generation. In **v0.1**, it’s a placeholder that throws a clear exception if used at runtime. The plan is to bind codegen to Maven’s `generate-sources` phase and emit:

- a final class implementing `ThrowingFn<I,O>`
- unit tests from examples/properties
- a manifest with provenance (model, prompt, hash)

------

## Metrics

Micrometer‑based recorder with a simple default (`SimpleMeterRegistry`).
 Counters/timers per step:

```
ps.pipeline.<name>.step.<idx>.<metric>
  - duration (timer)
  - errors (counter)
  - short_circuits (counter)
```

Swap the recorder globally:

```java
import com.pipeline.metrics.Metrics;
import com.pipeline.metrics.MetricsRecorder;

Metrics.setRecorder(new MyRecorder());
```

------

## Examples

All examples are in **`pipeline-examples`** and use **method references** (no inline lambdas):

- `Example01TextClean` – unary `String → String`, continue‑on‑error, explicit truncate short‑circuit
- `Example02ShortCircuitOnException` – unary with implicit short‑circuit
- `Example03CsvToJson` – typed `String → List<Map<...>> → String`
- `Example04FinanceOrderFlow` – typed `Tick → Features → Score → OrderResponse`
- `Example05TypedWithFallback` – typed with `onErrorReturn`
- `Example06PrePostPolicies` – before/after hooks
- `Example07ListDedupSort` – unary `List<String>`
- `Example08IntArrayStats` – typed `int[] → Stats`
- `Example09LoadFromJsonConfig` – per‑pipeline JSON loader
- `Example10DisruptorIntegration` – runs a pipeline through the wrapper engine
- `Example11RuntimeTextClean` – `RuntimePipeline<T>` (imperative)
- `Example12RuntimeListFlow` – runtime + explicit short‑circuit on empty list
- `Example13RuntimeResetAndFreeze` – runtime session reset + freeze into immutable pipeline

Run them all:

```bash
./mvnw -q -pl pipeline-examples exec:java \
  -Dexec.mainClass=com.pipeline.examples.ExamplesMain
```

------

## Roadmap

- **Codegen (v0.2)**: implement Prompt → Java generation in `generate-sources`, emit tests & manifest.
- **Remote (v0.2)**: gRPC adapter + better JSON mapping helpers.
- **Runner (v0.2)**: swap the simple single‑thread engine with a true Disruptor ring buffer (optional).
- **Replay (v0.3)**: deterministic journaling and sandboxed classloader for generated code.
- **Docs/CI**: add JUnit smokes for examples and publish site.

------

## Contributing

PRs welcome. Please keep examples **method‑reference–based** and stick to Java 21 features.
 Coding style: small final classes, records for data, no underscores in class names.

------

## License

Choose your license for the repo (Apache‑2.0 is a good default for OSS libraries).
 If a `LICENSE` file is present in the root, it governs this project.

------

### API Reference (quick)

```java
// Core function type
public interface ThrowingFn<I,O> { O apply(I in) throws Exception; }

// Unary builder
Pipeline<T>               // build once, run many
Pipeline.Builder<T>
  .shortCircuit(boolean)
  .beforeEach(ThrowingFn<T,T>) / .addPreAction(...)
  .step(ThrowingFn<T,T>)       / .addStep(...)
  .afterEach(ThrowingFn<T,T>)  / .addPostAction(...)
  .build();

// Typed builder (tracks I and current type C)
Pipe<I,O>
Pipe.Builder<I,C>
  .shortCircuit(boolean)
  .onErrorReturn(Function<Exception,O>)
  .step(ThrowingFn<? super C, ? extends M>) -> Builder<I,M>
  .to(Class<O>)

// Runtime, imperative (unary only)
RuntimePipeline<T>
  .addPreAction(ThrowingFn<T,T>) -> T
  .addStep(ThrowingFn<T,T>)      -> T
  .addPostAction(ThrowingFn<T,T>)-> T
  .reset(T) / .value()
  .toImmutable() / .freeze()

// Short-circuit
ShortCircuit.now(T finalValue) // ends immediately with finalValue
```

