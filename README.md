# Pipeline Services
Portable, local‑first pipeline framework with reference implementations in multiple languages (Java, Mojo, Python, TypeScript, Rust, C++, Go, C#).

This repo is organized around a shared, language-agnostic behavior contract (`docs/PORTABILITY_CONTRACT.md`) so ports can stay consistent on:
- short-circuit semantics
- exception capture vs continue
- JSON pipeline configuration shape (`actions`, `$local`, `$remote`, `remoteDefaults`)
- built-in timings/metrics hooks

## Core model (recommended)
- A pipeline is an ordered list of **actions** that transform a context value.
- Two action shapes are supported across ports:
  - Unary: `C → C`
  - Control-aware: `(C, control) → C` (explicit short-circuit + error recording)
- A pipeline has three phases: `pre` → `main` → `post`.

## Design goals
- **Simplicity and clarity first**: the common path should read like a list of actions (method refs/lambdas or JSON).
- **Portability by contract**: behavior is defined once in `docs/PORTABILITY_CONTRACT.md` and re-implemented per language.
- **Robustness without ceremony**: exceptions are captured; stop-vs-continue is a pipeline setting; post-actions still run.
- **Low-friction remote actions**: meaningful defaults (`remoteDefaults`) with per-action overrides; avoid repeating config.
- **Metrics out of the box**: timings are captured and can be emitted via a post-action (keeps the core clean).

## Java modules (Maven)
```
pipeline-core        # Pipeline<C>, StepAction<C>, StepControl<C>, PipelineResult<C>, RuntimePipeline<T>, metrics
pipeline-config      # Minimal JSON loader for unary String pipelines
pipeline-remote      # HTTP action adapter (json GET/POST)
pipeline-prompt      # Prompt builder + codegen entrypoint scaffold (build-time)
pipeline-api         # Higher-level facade (labels/jumps/beans/inline JSON + optional metrics)
pipeline-disruptor   # Runner wrapper (single-thread for now)
pipeline-examples    # Runnable examples (+ main runner)
```

## Ports
- Java (reference): `src/Java/` (Maven multi-module)
- Mojo: `src/Mojo/pipeline_services/` (runs via `pipeline_services/pixi.toml`)
- Python: `src/Python/pipeline_services/` (runs via `python3 -m ...`)
- TypeScript: `src/typescript/` (npm package)
- Rust: `src/Rust/` (Cargo crate)
- C++: `src/Cpp/` (C++20 + CMake)
- Go: `src/Go/` (Go modules)
- C#: `src/CSharp/` (dotnet projects)

## Why Mojo
Mojo is a primary target for a future “fast, portable pipeline runtime” story: compile-time performance, predictable execution, and an ecosystem that can still interop with Python when needed.

This repo includes a Mojo port that follows the shared behavior contract so semantics stay comparable across languages.

## Quick start

### Java (reference implementation)
Requirements: Java 21+, Maven 3.9+ (wrapper included)

```bash
./mvnw -q clean test
```

Run all examples:

```bash
./mvnw -q -pl pipeline-examples exec:java -Dexec.mainClass=com.pipeline.examples.ExamplesMain
```

Example (`Pipeline<C>`)

```java
import com.pipeline.core.Pipeline;
import com.pipeline.core.PipelineResult;
import com.pipeline.examples.steps.PolicySteps;
import com.pipeline.examples.steps.TextSteps;

Pipeline<String> pipeline = new Pipeline<>("clean_text", /*shortCircuitOnException=*/true)
    .addPreAction(PolicySteps::rateLimit)
    .addAction(TextSteps::strip)
    .addAction(TextSteps::normalizeWhitespace)
    .addAction((s, control) -> {
      if (s.length() > 280) {
        control.shortCircuit();        // explicit short-circuit (stops MAIN actions)
        return s.substring(0, 280);
      }
      return s;
    })
    .addPostAction(PolicySteps::audit);

PipelineResult<String> result = pipeline.execute("  Hello   World  ");
System.out.println(result.context());
```

### Mojo port
Mojo toolchain lives under `pipeline_services/pixi.toml`.

```bash
cd pipeline_services
pixi run mojo run -I ../src/Mojo ../src/Mojo/pipeline_services/examples/example01_text_clean.mojo
pixi run mojo run -I ../src/Mojo ../src/Mojo/pipeline_services/examples/example02_json_loader.mojo
pixi run mojo run -I ../src/Mojo ../src/Mojo/pipeline_services/examples/example05_metrics_post_action.mojo
```

Notes:
- JSON loading uses a registry for `$local` actions and supports `$remote` HTTP actions.

### Python port
```bash
cd src/Python
python3 -m pipeline_services.examples.example01_text_clean
python3 -m pipeline_services.examples.example02_json_loader
python3 -m pipeline_services.examples.example05_metrics_post_action
python3 -m pipeline_services.examples.benchmark01_pipeline_run
```

### TypeScript port
```bash
cd src/typescript
npm install
npm run build
npm test
node dist/src/pipeline_services/examples/example01_text_clean.js
```

### Rust port
```bash
cd src/Rust
cargo test
cargo run --example example01_text_clean
```

### C++ port
```bash
cd src/Cpp
cmake -S . -B build
cmake --build build -j
ctest --test-dir build
./build/example01_text_clean
```

### Go port
```bash
cd src/Go
go test ./...
go run ./examples/example01_text_clean
```

### C# port
```bash
cd src/CSharp
dotnet test ./pipeline_services_tests/PipelineServices.Tests.csproj
dotnet run --project pipeline_services_examples -- example01_text_clean
```

## Semantics (portable)
- Explicit short-circuit: inside a `StepAction<C>`, call `control.shortCircuit()`.
- `shortCircuitOnException=true`: an action exception records an error and short-circuits MAIN actions.
- `shortCircuitOnException=false`: an action exception records an error and continues.
- Pre/post actions always run fully (not stopped by short-circuit) by default.
- No checked exceptions in `StepAction<C>`; exceptions are captured in `PipelineResult<C>`.
- Optional: attach errors to your context via `Pipeline.onError((ctx, err) -> /*return updated ctx*/)`; default is no-op.

## JSON config (portable shape)
Canonical JSON form (across ports):

```json
{
  "pipeline": "json_clean_text",
  "type": "unary",
  "shortCircuitOnException": true,
  "actions": [
    { "$local": "com.pipeline.examples.adapters.TextStripStep" },
    { "$local": "com.pipeline.examples.adapters.TextNormalizeStep" }
  ]
}
```

Notes:
- `"steps"` is accepted as a legacy alias for `"actions"`.
- Root-level `"remoteDefaults"` can be used to avoid repeating remote configuration across many `"$remote"` actions.

Java loader (`pipeline-config`) is intentionally minimal and currently targets unary **String** pipelines:

```java
import com.pipeline.config.PipelineJsonLoader;

try (var in = getClass().getResourceAsStream("/pipelines/json_clean_text.json")) {
  var pipeline = PipelineJsonLoader.loadUnary(in);
  System.out.println(pipeline.run("  Hello   World  "));
}
```

## Remote action (HTTP)
Use `pipeline-remote` to turn an HTTP call into a `StepAction<C>`:

```java
import com.pipeline.core.Pipeline;
import com.pipeline.remote.http.HttpStep;

record Ctx(String q, String body) {}

var spec = new HttpStep.RemoteSpec<Ctx>();
spec.endpoint = "https://httpbin.org/post";
spec.timeoutMillis = 800;
spec.retries = 1;
spec.toJson = ctx -> "{\"q\":\"" + ctx.q() + "\"}";
spec.fromJson = (ctx, body) -> new Ctx(ctx.q(), body);

var pipeline = new Pipeline<Ctx>("remote_demo", true).addAction(HttpStep.jsonPost(spec));
Ctx out = pipeline.run(new Ctx("hello", null));
```

If you have many remote actions, use `HttpStep.RemoteDefaults` so you don’t repeat base URL, timeouts, retries, headers, and client wiring.

## Runtime / imperative sessions
`RuntimePipeline<T>` is an imperative, single-threaded helper for REPL/tools:

```java
import com.pipeline.core.RuntimePipeline;
import com.pipeline.examples.steps.TextSteps;

var runtimePipeline = new RuntimePipeline<>("adhoc_text", /*shortCircuitOnException=*/false, "  Hello   World  ");
runtimePipeline.addAction(TextSteps::strip);
runtimePipeline.addAction(TextSteps::normalizeWhitespace);
System.out.println(runtimePipeline.value());
```

## Advanced: labels + jumps + inline JSON (`pipeline-api`)
`pipeline-api` provides a higher-level facade for polling/workflows (labels + `Jumps.now/after`) and JSON that can target `@this` / beans.
See `README-JUMPS.md` and `README-API-QUICKSTART.md`.

## Examples
Examples live in `pipeline-examples` and show:
- Core `Pipeline<C>` (pre/actions/post, short-circuit, continue-on-error)
- JSON loader (`PipelineJsonLoader`)
- HTTP remote step (`HttpStep`)
- Jump engine + metrics (`pipeline-api`)
- Runtime sessions (`RuntimePipeline<T>`)
