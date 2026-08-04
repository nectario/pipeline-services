# Pipeline Services vNext

This directory defines the target contract for the next Pipeline Services implementation.

The vNext work is contract-first. These documents describe the intended public model and behavioral invariants before any runtime is replaced. The current `v0.1.0` implementation remains the executable source of truth until the later implementation phases adopt this contract.

## Documents

- [Simplicity Constitution](SIMPLICITY_CONSTITUTION.md) — the design constraints that keep the framework robust and small.
- [Portability Contract](PORTABILITY_CONTRACT.md) — the shared behavior that every language implementation must preserve.
- [API Naming Matrix](API_NAMING_MATRIX.md) — canonical concepts and their idiomatic spelling in each language.
- [Provider and Router Contract](PROVIDER_ROUTER_CONTRACT.md) — pipeline instance lifecycle, pooled selection, and event routing.
- [Conformance Scenarios](../../spec/conformance/vnext/pipeline-core.yaml) — machine-readable behavioral scenarios to implement across ports.

## Status

- Status: design contract
- Runtime status: not yet implemented
- Java remains the reference implementation for the later runtime phase.
- Composition is required in every language.
- Subclassing and builders are supported where idiomatic, but must delegate to the same pipeline plan and runner.

## Target model

```text
Event
  ↓
PipelineRouter
  ↓
PipelineProvider
  ├── NEW_INSTANCE_PER_EVENT
  ├── SINGLETON
  └── POOLED
        └── round robin by default
  ↓
Pipeline<Context>
  ↓
preActions → actions → postActions
  ↓
one execution engine per language
```

Handwritten local actions, remote actions, and LLM-generated actions all resolve to the same ordinary action abstraction at runtime.
