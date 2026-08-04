# Pipeline Services vNext — Provider and Router Contract

## Purpose

This document separates three responsibilities that must remain distinct:

```text
PipelineRouter routes.
PipelineProvider supplies.
Pipeline executes.
```

The separation comes from the original low-latency Pipeline Services design and is preserved because each abstraction answers a different question:

| Abstraction | Question |
| --- | --- |
| `Pipeline` | What actions should execute, and with what semantics? |
| `PipelineProvider` | Which Pipeline instance should execute this event? |
| `PipelineRouter` | Which PipelineProvider should receive this event? |

## PipelineProvider

### Responsibility

A PipelineProvider creates or selects a Pipeline instance according to an explicit lifecycle mode.

The minimal conceptual contract is:

```text
getPipeline() → Pipeline<Context>
mode() → PipelineProviderMode
```

A provider may also expose a convenience `run(context)` operation, but it must delegate to the selected Pipeline and must not create another execution engine.

### Required modes

```text
NEW_INSTANCE_PER_EVENT
SINGLETON
POOLED
```

### New instance per event

A new Pipeline instance is created from the configured factory for every provider request.

Conceptual behavior:

```text
getPipeline():
    return pipelineFactory()
```

Properties:

- strongest Pipeline-instance isolation;
- suitable for stateful Pipeline or Action instances;
- construction and allocation occur per event;
- no instance reuse is implied.

### Singleton

One Pipeline instance is created or supplied during provider initialization and returned for every request.

Conceptual behavior:

```text
getPipeline():
    return singletonPipeline
```

Properties:

- no Pipeline allocation in the event path;
- the Pipeline plan is frozen before shared use;
- framework execution state remains per run;
- Action implementations and dependencies must be safe for any overlapping use allowed by the application.

### Pooled

A fixed number of Pipeline instances is eagerly created during provider initialization.

Each provider request selects one existing instance.

Properties:

- no Pipeline allocation in the normal event path;
- no borrow or release operation;
- no blocking for an available instance;
- no ownership lease;
- no queue;
- no worker or task scheduling;
- no implied thread pool;
- no implied exclusivity.

The default selection policy is round robin.

Example sequence for a pool of four:

```text
Event 1 → Pipeline 0
Event 2 → Pipeline 1
Event 3 → Pipeline 2
Event 4 → Pipeline 3
Event 5 → Pipeline 0
```

After the sequence wraps, overlapping events may execute against the same Pipeline instance. The provider selects instances; it does not serialize execution.

### Why the mode is named `POOLED`

`POOLED` describes the lifecycle fact: a fixed collection of reusable Pipeline instances exists.

Round robin describes only the default selection algorithm. Future implementations may add other selection policies without renaming the lifecycle mode, such as:

- key or event affinity;
- stable hash selection;
- thread affinity;
- least-busy selection;
- application-provided selection.

A public selection-strategy API should not be added until a second real strategy is implemented. Internally, the design should avoid treating round robin as the permanent definition of pooled mode.

### Distinction from exclusive object pools

The current preview implementation uses borrow/release object-pool behavior under the name `POOLED`. vNext intentionally changes that semantic to the original fixed-instance selection model.

If exclusive leasing remains useful later, it must receive a separate, explicit name such as:

```text
EXCLUSIVE_POOL
```

or:

```text
LEASED_POOL
```

It must not silently share the vNext meaning of `POOLED`.

## Initialization and validation

### Factory validation

Provider construction must fail clearly when:

- the factory is missing;
- the factory returns no Pipeline;
- pooled instance count is less than one;
- eager creation of a singleton or pooled instance fails.

### Freeze

Singleton and pooled Pipeline plans are frozen before the provider is made available for routing.

New-instance-per-event Pipelines freeze before their first run or immediately after factory creation, according to the language implementation.

### Pooled eager construction

Pooled mode creates all configured instances during initialization rather than lazily during the event path. This makes startup failure explicit and keeps normal selection predictable.

## Default round-robin selection

The required observable behavior for a pool of size `N` is:

```text
selectionIndex(event k) = k mod N
```

The implementation must handle counter wraparound safely.

The selector must not:

- allocate a task object per selection;
- use a blocking queue;
- create threads;
- wait for execution completion;
- infer that an instance is idle or busy.

Ports may use an atomic counter, lock, event-loop-local counter, or another native mechanism that preserves the sequence and concurrency safety expected by that language.

## Pipeline execution state

Provider modes reuse Pipeline definitions and instances in different ways, but every call to `run` receives independent framework execution state.

The following must never be stored as one shared mutable flag on the Pipeline instance:

- short-circuit requested;
- current context;
- current phase;
- current action index;
- current errors.

This isolation is what makes no-argument `shortCircuit()` compatible with singleton and pooled provider modes.

## PipelineRouter

### Responsibility

A PipelineRouter examines an event and selects a PipelineProvider.

The minimal conceptual contract is:

```text
getPipelineProvider(event) → PipelineProvider<Context>
```

Routing may use:

- event type;
- instrument or symbol;
- tenant;
- source system;
- message schema;
- application-specific predicates.

### Non-responsibilities

A PipelineRouter does not:

- construct Pipeline actions;
- execute the Pipeline action loop;
- redefine short-circuit or error semantics;
- manage Pipeline instance lifecycle internally;
- own a task queue or worker pool.

### Router and provider composition

A typical flow is:

```text
provider = router.getPipelineProvider(event)
pipeline = provider.getPipeline()
result = pipeline.run(context)
```

A router may expose a convenience route-and-run method, but it must delegate through the provider and Pipeline without introducing a second runner.

## Configuration separation

Behavior, lifecycle, and routing are separate documents or sections.

### Pipeline behavior

```json
{
  "pipeline": "tradeTrainingPipeline",
  "shortCircuitOnException": true,
  "preActions": [],
  "actions": [],
  "postActions": []
}
```

### Provider lifecycle

```json
{
  "provider": "tradeTrainingPipelineProvider",
  "pipeline": "tradeTrainingPipeline",
  "mode": "pooled",
  "instanceCount": 8
}
```

### Router mapping

```json
{
  "routes": {
    "TRADE": "tradeTrainingPipelineProvider",
    "QUOTE": "quotePipelineProvider"
  }
}
```

A deployment may store these concerns together physically, but their semantic boundaries remain explicit.

## Required conformance behavior

Every port must verify:

- new-instance-per-event returns distinct Pipeline identities;
- singleton returns the same Pipeline identity;
- pooled eagerly creates exactly the configured number of Pipelines;
- pooled selection begins at the first instance and wraps in round-robin order;
- pooled selection performs no borrow/release or availability wait;
- provider convenience execution delegates to the selected Pipeline;
- router returns the expected provider for representative events;
- provider choice does not alter Pipeline action or short-circuit semantics;
- concurrent runs do not share framework execution state.
