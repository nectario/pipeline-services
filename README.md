# Pipeline Services

[![CI](https://github.com/nectario/pipeline-services/actions/workflows/ci.yml/badge.svg)](https://github.com/nectario/pipeline-services/actions/workflows/ci.yml)

Pipeline Services is a polyglot application-architecture framework for expressing behavior as a clear sequence of Actions over one context.

```text
preActions → actions → postActions
```

It is designed to be robust, local-first, portable, and above all simple.

## Project status

`v0.1.0` remains an initial public preview.

The simplicity reset is organized in four phases:

- **Phase 1 complete:** shared vNext contract, naming matrix, provider/router contract, and conformance scenarios.
- **Phase 2 complete in Java:** the Java reference kernel now implements the vNext model.
- **Phase 3 pending:** migrate Python, TypeScript, Rust, Go, C#, C++, and Mojo to the same semantics with native language formatting.
- **Phase 4 pending:** consolidate configuration, remote, LLM, observability, and other extensions around the final kernel.

Key documents:

- [vNext overview](docs/vnext/README.md)
- [Simplicity Constitution](docs/vnext/SIMPLICITY_CONSTITUTION.md)
- [Portability Contract](docs/vnext/PORTABILITY_CONTRACT.md)
- [API Naming Matrix](docs/vnext/API_NAMING_MATRIX.md)
- [Java Reference Implementation](docs/vnext/JAVA_REFERENCE_IMPLEMENTATION.md)
- [Java Migration Guide](docs/vnext/JAVA_MIGRATION.md)
- [Java Benchmark Smoke](docs/vnext/JAVA_BENCHMARK.md)
- [Project Status](docs/PROJECT_STATUS.md)

## The core idea

A Pipeline operates on one generic context type:

```text
Pipeline<Context>
Context → Context
```

An Action is an ordinary function:

```text
Context action(Context context)
```

Actions may be declared anywhere. They may be handwritten, remote adapters, or generated from an LLM prompt. Once resolved, every Action executes through the same runner.

The shared behavior is:

1. Run every `preAction` in registration order.
2. Run main `actions` until completion or `shortCircuit()`.
3. Run every `postAction`, even after short circuit or handled Action errors.
4. Return the final context.

## Java quick start

Requirements: Java 21+ and Maven 3.9+; the Maven wrapper is included.

```bash
./mvnw -q clean test
```

### Direct construction

Direct construction is the primary Java style:

```java
import com.pipeline.core.Pipeline;

Pipeline<String> pipeline = new Pipeline<String>("cleanText")
    .addPreAction(String::strip)
    .addAction(value -> value.replaceAll("\\s+", " "))
    .addAction(String::toUpperCase)
    .addPostAction(value -> value + "|");

String output = pipeline.run("  Hello   World  ");
```

### Short circuit

The public control operation is one function:

```java
import static com.pipeline.core.PipelineExecution.shortCircuit;

static OrderContext validateOrder(OrderContext context) {
    if (!context.isValid()) {
        shortCircuit();
        return context.reject("Invalid order");
    }

    return context;
}
```

Register it like any other Action:

```java
pipeline.addAction(OrderActions::validateOrder);
```

`shortCircuit()`:

- may only be called while a Pipeline Action body is actively executing;
- affects the innermost active run;
- lets the current Action return its updated context normally;
- skips remaining main Actions;
- never skips remaining postActions;
- remains isolated across nested and overlapping runs.

Observer callbacks, error handlers, and detached asynchronous work cannot use ambient `shortCircuit()` to control a run. The decision must be made on the active Action execution path before that Action returns.

### Checked exceptions

Java Actions may declare checked exceptions directly:

```java
static OrderContext loadCustomer(OrderContext context) throws IOException {
    return context;
}

pipeline.addAction(OrderActions::loadCustomer);
```

The Pipeline records the error and applies `shortCircuitOnException`.

### Simple and detailed execution

The common path returns the final context:

```java
OrderContext output = pipeline.run(input);
```

Diagnostics use the same runner:

```java
PipelineResult<OrderContext> result = pipeline.runDetailed(input);

result.context();
result.shortCircuited();
result.errors();
result.actionTimings();
```

`run()` avoids per-Action timing allocations. `runDetailed()` collects them.

## Construction styles

All construction styles create the same immutable Pipeline plan and use the same runner.

### Composition

```java
public final class OrderProcessor {
    private final Pipeline<OrderContext> pipeline;

    public OrderProcessor() {
        pipeline = new Pipeline<OrderContext>("orderPipeline")
            .addPreAction(this::validate)
            .addAction(this::price)
            .addPostAction(this::audit);
    }

    public OrderContext process(OrderContext context) {
        return pipeline.run(context);
    }
}
```

### Subclassing

```java
public final class OrderPipeline extends Pipeline<OrderContext> {
    public OrderPipeline() {
        super("orderPipeline");

        addPreAction(this::validate);
        addAction(this::price);
        addPostAction(this::audit);
    }
}
```

### Builder

```java
Pipeline<OrderContext> pipeline =
    Pipeline.<OrderContext>builder("orderPipeline")
        .addPreAction(OrderActions::validate)
        .addAction(OrderActions::price)
        .addPostAction(OrderActions::audit)
        .build();
```

The builder is optional and deliberately thin. It has no alternate vocabulary or runtime behavior.

## Pipeline plans and concurrency

A Pipeline freezes explicitly or on first execution. Structural mutation after freezing fails clearly.

Each `run()` and `runDetailed()` receives independent framework state. A frozen Pipeline can therefore be reused concurrently, provided the user-supplied Actions and dependencies are themselves concurrency-safe.

## PipelineProvider

`PipelineProvider` controls Pipeline-instance lifecycle and selection:

```java
PipelineProvider<OrderContext> perEvent =
    PipelineProvider.newInstancePerEvent(OrderPipeline::new);

PipelineProvider<OrderContext> singleton =
    PipelineProvider.singleton(new OrderPipeline());

PipelineProvider<OrderContext> pooled =
    PipelineProvider.pooled(OrderPipeline::new, 8);
```

The modes are:

```text
NEW_INSTANCE_PER_EVENT
SINGLETON
POOLED
```

POOLED eagerly creates a fixed collection of reusable Pipeline instances and selects round robin by default.

It does **not** imply:

- a thread pool;
- task scheduling;
- a work queue;
- borrow/release semantics;
- waiting for an available instance;
- exclusive ownership.

Future versions may add other selection strategies without renaming the POOLED lifecycle mode.

## PipelineRouter

`PipelineRouter` examines an event and selects a provider:

```java
PipelineRouter<TradingEvent, TradingContext> router = event ->
    switch (event.type()) {
        case TRADE -> tradePipelineProvider;
        case QUOTE -> quotePipelineProvider;
    };
```

The responsibility chain is intentionally explicit:

```text
PipelineRouter routes.
PipelineProvider supplies.
Pipeline executes.
```

## Observability

`PipelineObserver` is the one core observability seam:

```java
pipeline.observer(observer);
```

Observer failures cannot change Pipeline behavior. Logging, Micrometer, tracing, Prometheus, and low-latency telemetry belong in adapters outside `pipeline-core`.

The Java `pipeline-core` module has no runtime infrastructure dependencies.

## JSON configuration

The shared canonical shape is:

```json
{
  "pipeline": "cleanText",
  "shortCircuitOnException": true,
  "preActions": [],
  "actions": [
    { "$local": "strip" },
    { "$local": "normalizeWhitespace" }
  ],
  "postActions": []
}
```

JSON uses the same camelCase vocabulary across languages. Source APIs follow each language’s native convention, such as `addPreAction()` in Java and `add_pre_action()` in Python.

The current Java loader continues to accept selected preview aliases during migration. Loaders resolve configuration into ordinary Actions and the canonical Pipeline runner.

## Remote Actions

`pipeline-remote` turns an HTTP operation into an ordinary Action:

```java
HttpStep.RemoteSpec<Context> spec = new HttpStep.RemoteSpec<>();
spec.endpoint = "https://example.com/endpoint";
spec.timeoutMillis = 800;
spec.retries = 1;
spec.toJson = Context::toJson;
spec.fromJson = Context::withResponse;

Pipeline<Context> pipeline = new Pipeline<Context>("remoteDemo")
    .addAction(HttpStep.jsonPost(spec));
```

Transport behavior remains outside the core runner.

## LLM prompt-to-code

The LLM capability remains first-class.

A source Pipeline may contain a `$prompt` specification:

```text
$prompt source specification
        ↓
LLM/code-generation phase
        ↓
language-native generated Action and tests
        ↓
compiled Pipeline definition referencing $local
        ↓
the ordinary Pipeline runner
```

Run prompt compilation:

```bash
python3 tools/prompt_codegen.py --pipelines-dir pipelines
```

The runtime does not implicitly invoke an LLM merely because source configuration contains `$prompt`. An explicitly authored runtime LLM Action is also valid and still appears to the runner as an ordinary local or remote Action.

Generated code follows the naming and formatting conventions of each target language.

## Java modules

```text
pipeline-core        Canonical Pipeline, Action, result, provider, router, and observer
pipeline-config      JSON loader and Action resolution
pipeline-remote      HTTP Action adapters
pipeline-prompt      Prompt-to-code helpers and generated Action support
pipeline-api         Legacy higher-level typed/jump facade
pipeline-disruptor   Experimental queueing wrapper
pipeline-examples    Examples and benchmark harnesses
```

`RuntimePipeline<T>` remains temporarily available as a deprecated interactive helper. It delegates execution to the canonical runner.

The higher-level `com.pipeline.api.Pipeline<I, C>` remains a legacy extension for typed chains and arbitrary jumps. Those concepts are outside the vNext core contract.

## Ports

- Java: reference vNext implementation (`src/Java/`)
- Python: in-repo reference port (`src/Python/`), Phase 3 migration pending
- TypeScript: in-repo reference port (`src/typescript/`), Phase 3 migration pending
- Rust: in-repo reference port (`src/Rust/`), Phase 3 migration pending
- Go: in-repo reference port (`src/Go/`), Phase 3 migration pending
- C#: in-repo reference port (`src/CSharp/`), Phase 3 migration pending
- C++: in-repo reference port (`src/Cpp/`), Phase 3 migration pending
- Mojo: strategic and experimental port (`src/Mojo/`), Phase 3 migration pending

The ports share semantics and conceptual vocabulary, while preserving native casing and formatting. See the [API Naming Matrix](docs/vnext/API_NAMING_MATRIX.md).

## Running the port tests

```bash
# Java
./mvnw -q test

# Python
PYTHONPATH=src/Python python -m unittest discover -s src/Python/tests -p "test_*.py"

# TypeScript
cd src/typescript && npm ci && npm test

# Rust
cd src/Rust && cargo test

# Go
cd src/Go && go test ./...

# C#
dotnet test src/CSharp/pipeline_services_tests/PipelineServices.Tests.csproj

# C++
cmake -S src/Cpp -B src/Cpp/build
cmake --build src/Cpp/build -j
ctest --test-dir src/Cpp/build --output-on-failure
```

## Experimental and historical areas

- `src/Java/pipeline-api-pr/`: incubating Java API work, outside the supported release build.
- `statemachine/`: separate state-machine experiment, not part of the core Pipeline contract.
- `archive/`: historical snapshots and work-in-progress material.
- `pipeline-disruptor`: experimental queueing wrapper, not part of the core semantics.

## Design principle

Pipeline Services can remain broad in capability while being narrow in its mental model:

```text
One context.
One Pipeline concept.
One runner per language.
One shortCircuit() operation.
PipelineRouter routes.
PipelineProvider supplies.
Pipeline executes.
LLM-generated code becomes ordinary Actions.
```
