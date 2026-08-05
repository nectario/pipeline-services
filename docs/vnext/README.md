# Pipeline Services vNext

This directory defines the shared contract and implementation guidance for the Pipeline Services simplicity reset.

The work is organized contract-first: behavior is defined once, implemented first in Java, and then carried to each port using native language conventions.

## Documents

- [Simplicity Constitution](SIMPLICITY_CONSTITUTION.md) — constraints that keep the framework robust and small.
- [Portability Contract](PORTABILITY_CONTRACT.md) — behavior every language implementation must preserve.
- [API Naming Matrix](API_NAMING_MATRIX.md) — canonical concepts and idiomatic spelling by language.
- [Provider and Router Contract](PROVIDER_ROUTER_CONTRACT.md) — instance lifecycle, pooled selection, and event routing.
- [Java Reference Implementation](JAVA_REFERENCE_IMPLEMENTATION.md) — the Phase 2 Java API and internals.
- [Java Migration Guide](JAVA_MIGRATION.md) — preview-to-vNext Java changes.
- [Java Benchmark Smoke](JAVA_BENCHMARK.md) — informational `run()` versus `runDetailed()` measurements.
- [Phase 3.5 Stabilization](PHASE_3_5_STABILIZATION.md) — final polyglot parity, race-safety, and CI corrections before Phase 4.
- [Conformance Scenarios](../../spec/conformance/vnext/pipeline-core.yaml) — machine-readable behavioral scenarios for every port.

## Status

- Phase 1: shared design contract complete.
- Phase 2: Java reference kernel implemented.
- Phase 3: all language ports migrated to the vNext runtime contract.
- Phase 3.5: stabilization and cross-language verification complete.
- Phase 4: extension consolidation and final legacy cleanup remain.

The stabilized polyglot implementations now share:

- one generic context type;
- one canonical `Pipeline<C>`;
- one `PipelineRunner`;
- one execution-scoped `shortCircuit()` operation;
- direct construction, composition, subclassing, and a thin builder;
- immutable Pipeline plans;
- independent state for nested and overlapping runs;
- `NEW_INSTANCE_PER_EVENT`, `SINGLETON`, and `POOLED` provider modes;
- a separate `PipelineRouter`;
- a zero-infrastructure-dependency Java `pipeline-core` module;
- explicit documented language adaptations where runtime constraints require them.

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

Handwritten local Actions, remote adapters, and LLM-generated Actions all resolve to the same ordinary Action abstraction at runtime.
