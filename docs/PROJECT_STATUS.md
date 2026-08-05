# Project Status

## vNext simplicity reset

Pipeline Services has completed the kernel and polyglot stabilization stages:

- **Phase 1 complete:** simplicity constitution, portability contract, naming matrix, provider/router contract, and shared scenarios.
- **Phase 2 complete:** Java reference kernel.
- **Phase 3 complete:** polyglot kernel migration.
- **Phase 3.5 complete:** parity corrections, race-safety work, executable test wiring, and aggregate CI gate.
- **Phase 4 pending:** consolidate LLM generation, configuration, remote Actions, observability, and legacy extensions around the stabilized kernel.

See [`docs/vnext/`](vnext/README.md), especially the [Phase 3.5 stabilization report](vnext/PHASE_3_5_STABILIZATION.md).

## Release scope for v0.1.0

`v0.1.0` remains an initial public preview. The repository contains the reference implementations and tests, but standalone publication to Maven Central, PyPI, npm, crates.io, NuGet, or other registries remains outside the current release scope.

## Port maturity matrix

| Surface | Status | Notes |
| --- | --- | --- |
| Java (`src/Java/`) | vNext reference | Canonical kernel, subclassing/composition/builder, run-local control, providers, router, and zero-infrastructure core. |
| Python (`src/Python/`) | vNext reference port | ContextVar-backed execution scope, canonical configuration, providers, router, remote and generated Actions. |
| TypeScript (`src/typescript/`) | vNext reference port | AsyncLocalStorage-backed execution with documented descendant lifetime semantics. |
| Rust (`src/Rust/`) | vNext reference port | Requires `Context: Clone + 'static`; Action panic recovery uses `catch_unwind`. |
| Go (`src/Go/`) | vNext reference port | Uses an explicit `PipelineExecution` handle for safe short circuit; race-detector CI is required. |
| C# (`src/CSharp/`) | vNext reference port | AsyncLocal-backed control and canonical JSON/prompt phase handling. |
| C++ (`src/Cpp/`) | vNext reference port | C++20 kernel, thread-local run scope, eager providers, router, and two CTest conformance suites. |
| Mojo (`src/Mojo/`, `pipeline_services/`) | Strategic / experimental | Stable toolchain pinned and tested; concurrent pooled selection is not yet a mature-port guarantee. |
| `pipeline-config` | Transitional extension | Resolves canonical JSON into ordinary Actions; preview aliases remain temporarily. |
| `pipeline-remote` | Transitional extension | Remote operations execute as ordinary Actions through the canonical runner. |
| `pipeline-prompt` | Strategic extension | Prompt-to-code remains first-class and emits ordinary language-native Actions. |
| `pipeline-api` | Legacy extension | Typed chains, labels, and jumps remain outside the vNext core contract. |
| `RuntimePipeline` | Deprecated compatibility helper | Delegates to the canonical runner. |
| `pipeline-disruptor` | Experimental | Queueing wrapper outside core semantics and compatibility guarantees. |

## Core compatibility boundary

The shared conceptual surface is:

```text
Pipeline<Context>
Action<Context>
shortCircuit / native execution-control binding
PipelineResult<Context>
PipelineProvider<Context>
PipelineRouter<Event, Context>
PipelineObserver
```

```text
run(context) → context
runDetailed(context) → PipelineResult<context>
```

Go’s `execution.ShortCircuit()` is the one intentional syntax adaptation. Mojo remains explicitly experimental. All mature ports preserve the same phase, context, provider, routing, and post-action semantics.

## Conformance and CI

The language-neutral scenarios live in `spec/conformance/vnext/pipeline-core.yaml`. Their native evidence is declared in `spec/conformance/vnext/port-coverage.json` and validated by `tools/check_conformance_coverage.py`.

The `phase35-gate` GitHub Actions job is the aggregate repository check. It succeeds only when Java, Python, TypeScript, Rust, Go including the race detector, C#, C++, Mojo, and repository hygiene all succeed.

## Experimental and non-release directories

- `src/Java/pipeline-api-pr/`: incubating Java API work area.
- `statemachine/`: separate state-machine experiment.
- `archive/`: historical snapshots and work-in-progress material.
- `pipeline_services/`: Pixi-managed Mojo workspace.
