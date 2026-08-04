# Pipeline Services vNext — Portability Contract

## 1. Status and scope

This document defines the target observable behavior for the vNext Pipeline Services implementations.

It is a design contract, not a statement that every current `v0.1.0` port already behaves this way. Later phases will migrate the Java reference implementation and then the remaining ports to this contract.

The contract separates:

- **required semantics**, which every port must preserve;
- **native syntax**, which follows each language’s conventions;
- **optional ergonomics**, such as subclassing or a builder;
- **extensions**, such as JSON, remote actions, prompt-to-code, metrics, and queueing engines.

## 2. Core abstractions

### 2.1 Pipeline

A Pipeline is an ordered, reusable definition of application behavior over one context type:

```text
Pipeline<Context>
```

The core data flow is:

```text
Context → Context
```

A Pipeline has:

- a pipeline name;
- `preActions`;
- `actions`;
- `postActions`;
- an exception policy;
- an optional error handler;
- an optional observer.

### 2.2 Action

An Action conceptually has this signature:

```text
Context action(Context context)
```

The language may represent the action as a function, method reference, delegate, closure, callable object, trait object, interface implementation, or equivalent.

The Action does not normally receive a framework control object.

### 2.3 PipelineResult

The simple execution method returns the final context:

```text
run(context) → context
```

A detailed method returns a diagnostic result:

```text
runDetailed(context) → PipelineResult<context>
```

`PipelineResult` contains at least:

- final context;
- whether the run was short-circuited;
- captured pipeline errors.

Optional timing or observer-specific data may be included without changing execution semantics.

### 2.4 PipelineError

A PipelineError identifies at least:

- pipeline name;
- phase (`preActions`, `actions`, or `postActions`);
- action index;
- action name or label when available;
- captured error or exception value.

## 3. Construction styles

### 3.1 Required: direct construction

Every port must support direct creation of a Pipeline and explicit action registration.

Conceptual example:

```text
pipeline = Pipeline("orderPipeline")
pipeline.addPreAction(validate)
pipeline.addAction(price)
pipeline.addPostAction(audit)
```

### 3.2 Required: composition

Every port must support owning a Pipeline inside another application object or module.

Composition is the universal construction style and is the baseline for ports without inheritance.

### 3.3 Optional but first-class where idiomatic: subclassing

Languages with natural inheritance support should allow a user-defined Pipeline subclass to register actions during construction and call the inherited short-circuit helper.

Subclassing must not use a different runner or different semantics.

### 3.4 Optional: builder

A builder may be provided when idiomatic or requested.

The builder must:

- use the same canonical action names;
- produce the same Pipeline plan as direct construction;
- delegate execution to the same runner;
- expose no builder-only runtime capability.

## 4. Canonical action collections

The language-neutral names are:

```text
preActions
 actions
postActions
```

The canonical registration operations are:

```text
addPreAction
addAction
addPostAction
```

Source-code casing changes by language. Shared JSON and generated intermediate definitions retain the language-neutral camelCase field names.

Terse aliases such as `pre`, `main`, `step`, `post`, `before`, and `after` are not part of the vNext canonical surface.

## 5. Execution semantics

### 5.1 Normal order

For one run:

1. Execute every `preAction` in registration order.
2. If the run has not been short-circuited, execute `actions` in registration order.
3. Execute every `postAction` in registration order.
4. Return the final context.

### 5.2 Context propagation

After an Action returns successfully, its returned context becomes the input to the next Action.

Returning a null or invalid context may be rejected according to the language and type system. The Java reference implementation will reject `null` by default.

### 5.3 Post-action guarantee

Once execution begins, every registered `postAction` is attempted in order regardless of:

- an explicit short circuit;
- a handled exception in `preActions`;
- a handled exception in `actions`;
- a handled exception in an earlier `postAction`.

A process-level failure that prevents any further code execution is outside this guarantee.

## 6. Explicit short circuit

### 6.1 Public operation

The only normal explicit control operation is:

```text
shortCircuit()
```

Its native spelling is defined in the naming matrix.

### 6.2 Active-run requirement

`shortCircuit()` may only be called while an Action is executing inside an active Pipeline run.

Calling it outside an active run must fail clearly. It must not silently affect a later run.

### 6.3 Run-local state

The operation marks the current execution state, not the reusable Pipeline instance.

This is required for:

- singleton PipelineProvider mode;
- pooled PipelineProvider mode;
- overlapping runs;
- nested pipelines.

### 6.4 Stop-after-current-action behavior

`shortCircuit()` does not immediately discard the current Action’s returned context.

The Action returns normally. The returned context becomes the current context. The runner then applies the phase rules.

### 6.5 Phase behavior

When called from a `preAction`:

- the current `preAction` returns normally;
- remaining `preActions` still execute;
- all main `actions` are skipped;
- every `postAction` executes.

When called from a main `action`:

- the current Action returns normally;
- remaining main `actions` are skipped;
- every `postAction` executes.

When called from a `postAction`:

- the current Action returns normally;
- remaining `postActions` still execute;
- the detailed result reports that the run was short-circuited.

### 6.6 Actions declared anywhere

An Action may call the execution-scoped short-circuit function regardless of where the Action is declared.

Subclass convenience methods and Pipeline instance convenience methods must delegate to the same run-local operation.

### 6.7 Nested runs

When Pipeline A invokes Pipeline B, `shortCircuit()` targets the innermost active run.

After Pipeline B completes, a subsequent call from Pipeline A’s Action targets Pipeline A.

## 7. Exception semantics

### 7.1 Exception capture

An unhandled Action exception is converted into a PipelineError and supplied to the optional error handler.

If the Action throws before returning, the last successfully produced context remains current unless the error handler returns an updated context.

### 7.2 `shortCircuitOnException = true`

The error is recorded and the current run is marked short-circuited.

Phase behavior follows the same rules as an explicit short circuit:

- remaining `preActions` still execute when the error occurs in `preActions`;
- remaining main `actions` are skipped;
- every `postAction` executes;
- remaining `postActions` still execute when the error occurs in `postActions`.

### 7.3 `shortCircuitOnException = false`

The error is recorded, the current context is preserved or updated by the error handler, and execution continues with the next Action in that phase.

### 7.4 Error handler

An optional error handler may transform the current context after an error:

```text
onError(context, pipelineError) → context
```

The handler must return a valid context.

## 8. Plan lifecycle and concurrency

### 8.1 Assembly and freeze

A Pipeline may be assembled through direct registration, composition, subclass construction, a builder, or a loader.

Before shared execution, its structural plan becomes immutable.

After freezing, structural mutation must fail clearly.

### 8.2 Per-run execution state

Every `run` or `runDetailed` call receives an independent execution state containing at least:

- current context;
- short-circuit state;
- captured errors;
- current phase and action identity.

Run state must not leak to another event or subsequent run.

### 8.3 Action thread safety

Pipeline Services isolates framework execution state. It does not automatically make user Action objects thread-safe.

When a PipelineProvider mode permits overlapping use of one Pipeline instance, Actions and referenced dependencies must be stateless, immutable, synchronized, otherwise concurrency-safe, or intentionally isolated by the application.

## 9. PipelineProvider

### 9.1 Responsibility

PipelineProvider creates or selects a Pipeline instance for an event.

It does not route event types and does not implement Pipeline action semantics.

### 9.2 Required modes

The language-neutral modes are:

```text
NEW_INSTANCE_PER_EVENT
SINGLETON
POOLED
```

Native enum or constant spelling follows the language’s conventions.

### 9.3 New instance per event

For every provider request, create a new Pipeline instance from the configured factory.

This mode provides the strongest instance isolation and incurs construction/allocation cost per event.

### 9.4 Singleton

Create or accept one Pipeline instance and return that same instance for every provider request.

The Pipeline plan must be frozen before concurrent shared use.

### 9.5 Pooled

Eagerly create a fixed number of reusable Pipeline instances and select one for each provider request.

Round robin is the default selection behavior.

`POOLED` defines a lifecycle model, not one permanent selection algorithm. Future versions may support additional selection policies without renaming the mode.

Pooled mode does not imply:

- thread creation;
- a thread pool;
- task scheduling;
- a work queue;
- backpressure;
- borrow/release semantics;
- exclusive ownership;
- waiting for an available Pipeline.

Two overlapping events may execute against the same selected Pipeline instance after the selection sequence wraps.

### 9.6 Provider execution convenience

A provider may offer a convenience `run` method, but it must be equivalent to:

```text
provider.getPipeline().run(context)
```

It must not introduce another runner.

## 10. PipelineRouter

### 10.1 Responsibility

PipelineRouter examines an event and selects the appropriate PipelineProvider.

Conceptually:

```text
getPipelineProvider(event) → PipelineProvider
```

### 10.2 Separation of responsibilities

The required chain is:

```text
PipelineRouter routes.
PipelineProvider supplies.
Pipeline executes.
```

A router does not redefine provider lifecycle or pipeline action semantics.

## 11. Shared configuration vocabulary

The canonical JSON shape uses the same field names in every language:

```json
{
  "pipeline": "orderPipeline",
  "shortCircuitOnException": true,
  "preActions": [],
  "actions": [],
  "postActions": []
}
```

A port may temporarily accept legacy aliases during migration, but generators and documentation must emit only the canonical fields.

Pipeline behavior configuration, provider lifecycle configuration, and router configuration are separate concerns.

Example provider configuration:

```json
{
  "pipeline": "orderPipeline",
  "mode": "pooled",
  "instanceCount": 8
}
```

Example router configuration:

```json
{
  "routes": {
    "TRADE": "tradePipelineProvider",
    "QUOTE": "quotePipelineProvider"
  }
}
```

## 12. Local, remote, and generated actions

### 12.1 Ordinary runtime action

Every resolved Action executes through the ordinary Action abstraction regardless of origin.

### 12.2 Remote actions

A remote adapter may perform HTTP or another transport operation, but it must present itself to the runner as an ordinary Action.

Transport configuration and retries stay outside the core runner.

### 12.3 LLM prompt-to-code

`$prompt` remains a build-time or explicit generation directive.

The required flow is:

1. Preserve the source prompt specification.
2. Generate language-native Action code and tests.
3. Produce or update a compiled pipeline definition that references the generated local Action.
4. Register and execute the generated Action through the ordinary runner.

The runtime must not implicitly invoke an LLM merely because source configuration contains `$prompt`.

An explicitly authored runtime LLM Action is allowed, but it is still an ordinary local or remote Action from the runner’s perspective.

## 13. Observability

A port may expose one observer interface for events such as:

- pipeline started;
- action started;
- action completed;
- action failed;
- run short-circuited;
- pipeline completed.

Observer attachment must not change Pipeline semantics or create another execution path.

Logging, Micrometer, Prometheus, tracing, and low-latency telemetry are adapters.

## 14. Out of core scope

The following are not part of the vNext core semantic contract:

- arbitrary labeled jumps;
- waits, resumability, and workflow state machines;
- task or thread pools;
- exclusive object leasing;
- reflection-based dependency injection;
- JSON parsing implementation details;
- transport implementation details;
- LLM provider implementation details;
- metrics backend implementation details;
- Disruptor or queueing engine behavior;
- type-changing pipeline chains.

These may exist as extensions provided they compile into ordinary Actions or observe the one runner without changing its invariants.

## 15. Conformance

Each supported port must implement the scenarios in:

```text
spec/conformance/vnext/pipeline-core.yaml
```

A port is vNext-conformant only when all required scenarios applicable to that language pass.

Language-specific ergonomic features may have additional tests but may not contradict this contract.
