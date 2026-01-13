# Pipeline Services
Functional pipeline framework for Java 21: local‑first execution, optional JSON config, optional remote actions, and build‑time prompt scaffolding (no runtime LLM calls).

## Core model (recommended)
- A pipeline is `com.pipeline.core.Pipeline<C>` where each action is `C → C`.
- Add actions in three ways:
  - Method reference / lambda: `pipeline.addAction(UnaryOperator<C>)`
  - Control‑aware action: `pipeline.addAction(StepAction<C>)`
  - JSON config: `pipeline-config` (`PipelineJsonLoader`)

## Modules
```
pipeline-core        # Pipeline<C>, StepAction<C>, StepControl<C>, PipelineResult<C>, RuntimePipeline<T>, metrics
pipeline-config      # Minimal JSON loader for unary String pipelines
pipeline-remote      # HTTP action adapter (json GET/POST)
pipeline-prompt      # Prompt builder + codegen entrypoint scaffold (build-time)
pipeline-api         # Higher-level facade (labels/jumps/beans/inline JSON + optional metrics)
pipeline-disruptor   # Runner wrapper (single-thread for now)
pipeline-examples    # Runnable examples (+ main runner)
```

## Build
Requirements: Java 21+, Maven 3.9+ (wrapper included)

```bash
./mvnw -q clean test
```

Run all examples:

```bash
./mvnw -q -pl pipeline-examples exec:java -Dexec.mainClass=com.pipeline.examples.ExamplesMain
```

## Quick start (`Pipeline<C>`)

```java
import com.pipeline.core.Pipeline;
import com.pipeline.core.PipelineResult;
import com.pipeline.examples.steps.PolicySteps;
import com.pipeline.examples.steps.TextSteps;

Pipeline<String> p = new Pipeline<>("clean_text", /*shortCircuitOnException=*/true)
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

PipelineResult<String> result = p.execute("  Hello   World  ");
System.out.println(result.context());
```

## Short-circuit + error semantics
- Explicit short-circuit: inside a `StepAction<C>`, call `control.shortCircuit()`.
- `shortCircuitOnException=true`: an action exception records an error and short-circuits MAIN actions.
- `shortCircuitOnException=false`: an action exception records an error and continues.
- Pre/post actions always run fully (not stopped by short-circuit) by default.
- No checked exceptions in `StepAction<C>`; exceptions are captured in `PipelineResult<C>`.
- Optional: attach errors to your context via `Pipeline.onError((ctx, err) -> /*return updated ctx*/)`; default is no-op.

## JSON config (optional)
`pipeline-config` is a minimal loader for unary **String** pipelines:

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

```java
import com.pipeline.config.PipelineJsonLoader;

try (var in = getClass().getResourceAsStream("/pipelines/json_clean_text.json")) {
  var p = PipelineJsonLoader.loadUnary(in);
  System.out.println(p.run("  Hello   World  "));
}
```

Notes:
- `"steps"` is accepted as an alias for `"actions"` (both Java and Mojo loaders).
- JSON pipelines may define root-level `"remoteDefaults"` and then reference remote actions via `"$remote"`.

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

var p = new Pipeline<Ctx>("remote_demo", true).addAction(HttpStep.jsonPost(spec));
Ctx out = p.run(new Ctx("hello", null));
```

If you have many remote actions, use `HttpStep.RemoteDefaults` so you don’t repeat base URL, timeouts, retries, headers, and client wiring.

## Runtime / imperative sessions
`RuntimePipeline<T>` is an imperative, single-threaded helper for REPL/tools:

```java
import com.pipeline.core.RuntimePipeline;
import com.pipeline.examples.steps.TextSteps;

var rt = new RuntimePipeline<>("adhoc_text", /*shortCircuitOnException=*/false, "  Hello   World  ");
rt.addAction(TextSteps::strip);
rt.addAction(TextSteps::normalizeWhitespace);
System.out.println(rt.value());
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

## Mojo port (experimental)
The Mojo port lives in `src/Mojo/pipeline_services/` and uses `pixi` via `pipeline_services/pixi.toml`:

```bash
cd pipeline_services
pixi run mojo run -I ../src/Mojo ../src/Mojo/pipeline_services/examples/example01_text_clean.mojo
```
