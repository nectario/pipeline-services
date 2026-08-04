# Pipeline Services vNext — Simplicity Constitution

## Purpose

Pipeline Services exists to make application behavior easy to express, review, reuse, test, observe, and move across languages.

Its center must remain smaller than the systems built with it. Capability may grow around the kernel, but it must not grow through the kernel.

## Constitutional rules

### 1. One generic context

The core pipeline model is:

```text
Context → Context
```

Every core action receives the current context and returns the next context of the same type.

The context may be mutable or immutable. Pipeline Services must not require it to extend a framework base class or implement a framework interface.

Type-changing transformation chains may be explored as a separate extension, but they are not part of the vNext core contract.

### 2. One Pipeline concept

Each language exposes one canonical `Pipeline<Context>` concept.

Direct construction and composition are required everywhere. Subclassing is first-class where the language supports it naturally. A builder may be offered as a convenience.

These construction styles must create the same underlying pipeline plan and must use the same runner:

```text
Direct construction ─┐
Composition ─────────┼─> PipelinePlan<Context> ─> PipelineRunner
Subclassing ─────────┤
Builder ─────────────┘
```

No construction style may have exclusive runtime behavior or features.

### 3. One ordered execution model

A pipeline has three explicit action collections:

```text
preActions → actions → postActions
```

The names are intentionally verbose. The public API should prefer clarity over terse aliases.

The normal behavior is:

1. Execute every `preAction` in order.
2. Execute `actions` in order until complete or short-circuited.
3. Execute every `postAction` in order.

`postActions` always execute after pipeline execution begins, including after an explicit short circuit or a handled action exception.

### 4. One public short-circuit operation

The public control operation is exactly one function:

```text
shortCircuit()
```

Its spelling follows the language’s normal naming convention, such as `short_circuit()` in Python and Rust.

`shortCircuit()`:

- may only be called while a pipeline run is active;
- marks the current run, not the reusable Pipeline object;
- does not carry the context;
- does not immediately discard the current action’s returned context;
- skips remaining main `actions` after the current action returns;
- never skips remaining `postActions`;
- fails clearly when called outside an active run.

The action updates and returns the context normally:

```java
if (!context.isValid()) {
    shortCircuit();
    return context.reject("Invalid request");
}
```

An action that never controls execution remains a plain function with no framework dependency.

### 5. Actions may live anywhere

An action may be:

- an instance method on a Pipeline subclass;
- an instance method on a composed service;
- a static or module-level function;
- a lambda or closure;
- a method on an injected dependency;
- a generated action;
- a remote-action adapter.

The core action signature remains conceptually:

```text
Context action(Context context)
```

An action that calls `shortCircuit()` has only the minimal dependency needed to access that one execution-scoped operation.

### 6. One execution engine per language

Direct pipelines, composed pipelines, subclasses, builders, JSON-loaded pipelines, remote actions, and generated actions must all execute through the same runner in each language.

The following are prohibited in the stable implementation:

- a separate builder runner;
- a separate JSON runner;
- a separate typed runner inside the core;
- a separate jump runner inside the core;
- duplicated short-circuit or error semantics.

### 7. Polyglot semantics, native syntax

The supported languages share concepts and behavior, not identical character sequences.

Examples:

```text
Java / TypeScript: addPreAction(), shortCircuit()
Python / Rust / Mojo: add_pre_action(), short_circuit()
C#: AddPreAction(), ShortCircuit()
Go: AddPreAction(), ShortCircuit()
```

The same pipeline should be immediately recognizable in every port while still looking natural in that language.

Shared configuration uses one canonical language-neutral vocabulary and does not change casing per implementation.

### 8. Direct construction first; builder optional

Documentation and generated examples lead with direct construction, composition, or subclassing.

A builder is supported where useful, but it must:

- use the same verbose names as the direct API;
- be a thin assembly facade;
- produce the same pipeline plan;
- add no builder-only runtime features;
- avoid alternate vocabulary such as `pre`, `step`, or `post`.

### 9. PipelineProvider supplies instances

`PipelineProvider` controls how Pipeline instances are created or selected.

The required modes are:

```text
NEW_INSTANCE_PER_EVENT
SINGLETON
POOLED
```

`POOLED` means a fixed set of reusable Pipeline instances. Round robin is the default selection policy, but `POOLED` does not permanently prescribe one selection algorithm.

A pooled provider is not a thread pool. It performs no task scheduling, worker management, queueing, borrowing, releasing, or exclusivity unless a separately named extension explicitly adds those behaviors.

### 10. PipelineRouter routes events

`PipelineRouter` selects the appropriate `PipelineProvider` for an event.

It does not:

- execute pipeline actions;
- create provider lifecycle policies;
- manage worker threads;
- merge routing and provisioning into one opaque service.

The responsibility chain is:

```text
PipelineRouter routes.
PipelineProvider supplies.
Pipeline executes.
```

### 11. LLM-generated actions remain first-class

The prompt-to-code capability is a defining Pipeline Services feature and must be preserved.

The intended flow is:

```text
$prompt source specification
        ↓
LLM/code-generation phase
        ↓
language-native generated action
        ↓
ordinary registered action at runtime
```

The runtime does not need a special execution path for generated actions.

Handwritten local actions, generated actions, and remote actions all converge on the same core action abstraction.

No implicit runtime LLM call is introduced merely because a pipeline source contains `$prompt`.

### 12. Infrastructure stays outside the kernel

The stable core should not require infrastructure dependencies merely to execute a local pipeline.

The following belong in adapters or extensions rather than the execution kernel:

- JSON parsing;
- HTTP clients;
- LLM providers and code generation;
- Micrometer, Prometheus, or logging integrations;
- object pools and thread pools;
- reflection-based dependency construction;
- workflow jumps, delays, retries, and resumability;
- Disruptor or other queueing engines.

Extensions resolve their behavior into ordinary actions or observe the one runner.

### 13. Plans freeze before shared execution

A Pipeline is assembled while mutable and becomes an immutable execution plan before shared use.

After freezing:

- action ordering cannot change;
- singleton and pooled providers can safely reuse the plan;
- per-run control state remains isolated from the reusable plan;
- callers receive a clear error if they attempt structural mutation.

Action implementations remain responsible for their own thread safety when a provider mode permits overlapping runs on the same Pipeline instance.

### 14. Observability is optional and unified

The core may expose one small observer contract for pipeline and action lifecycle events.

Observability must not require:

- a global mutable recorder;
- two competing metrics APIs;
- detailed timing allocation on every run when no observer requests it;
- separate execution paths.

Logging, metrics, tracing, and low-latency telemetry attach through adapters.

### 15. Compatibility does not preserve accidental complexity forever

The project is still in preview. Compatibility adapters may temporarily bridge existing APIs to vNext, but they must delegate to the new kernel and have an explicit retirement path.

Aliases, duplicate runners, duplicate loaders, and silent identity placeholders are not permanent compatibility promises.

## Simplicity test

A proposed core feature should be rejected or moved outward when it cannot answer “yes” to all of these questions:

1. Does it preserve one generic context?
2. Does it use the one Pipeline runner?
3. Can every supported language express it clearly?
4. Does it keep `shortCircuit()` as the only normal control operation?
5. Can local, generated, and remote actions remain ordinary actions?
6. Can a new user understand the common path without reading infrastructure code?

## Compact form

```text
One context.
One Pipeline.
One runner.
One shortCircuit() operation.

Direct construction first.
Composition everywhere.
Subclassing where idiomatic.
Builder supported, but secondary.

Verbose semantic names.
Native language casing.
One shared configuration vocabulary.

PipelineRouter routes.
PipelineProvider supplies.
Pipeline executes.

Local, remote, and LLM-generated actions
all become ordinary pipeline actions.
```
