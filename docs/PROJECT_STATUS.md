# Project Status

## vNext simplicity reset

Pipeline Services is being migrated in four gated phases.

- **Phase 1 complete:** simplicity constitution, portability contract, naming matrix, provider/router contract, and machine-readable conformance scenarios.
- **Phase 2 complete in Java:** the Java reference kernel implements the vNext core model.
- **Phase 3 pending:** migrate the remaining language ports to the vNext runtime semantics.
- **Phase 4 pending:** consolidate JSON, remote, LLM, observability, workflow, and other extensions around the final kernel.

The vNext documents are indexed in [`docs/vnext/README.md`](vnext/README.md).

The repository is temporarily in a mixed migration state:

- `com.pipeline.core.Pipeline<C>` is the Java vNext reference implementation.
- Java preview control-aware overloads remain as adapters into the same runner.
- Python, TypeScript, Rust, Go, C#, C++, and Mojo retain their current preview implementations until Phase 3.
- The old [`PORTABILITY_CONTRACT.md`](PORTABILITY_CONTRACT.md) documents the pre-vNext preview surface.
- [`vnext/PORTABILITY_CONTRACT.md`](vnext/PORTABILITY_CONTRACT.md) is the target shared contract and the current Java core contract.

## Release scope for v0.1.0

`v0.1.0` remains an initial public preview of Pipeline Services as a locality-aware software architecture framework.

The release includes:

- the Java reference implementation;
- in-repo language ports;
- shared JSON configuration;
- remote Action adapters;
- prompt-to-code and generated Action support;
- examples and conformance tests.

Standalone publication to Maven Central, PyPI, npm, crates.io, NuGet, or other package registries remains outside the current release scope.

## Port maturity matrix

| Surface | Status | Notes |
| --- | --- | --- |
| Java (`src/Java/`) | vNext reference implementation | Canonical single-context Pipeline, one runner, execution-scoped short circuit, provider modes, router, and zero-infrastructure-dependency core. |
| Python (`src/Python/`) | Preview reference port | In-repo port with tests; Phase 3 vNext migration pending. |
| TypeScript (`src/typescript/`) | Preview reference port | In-repo private package with tests; Phase 3 migration pending. |
| Rust (`src/Rust/`) | Preview reference port | In-repo non-published crate with tests; Phase 3 migration pending. |
| Go (`src/Go/`) | Preview reference port | In-repo module with tests; Phase 3 migration pending. |
| C# (`src/CSharp/`) | Preview reference port | In-repo project with tests; Phase 3 migration pending. |
| C++ (`src/Cpp/`) | Preview reference port | In-repo implementation with examples/tests; Phase 3 migration pending. |
| Mojo (`src/Mojo/`, `pipeline_services/`) | Strategic / experimental | Important runtime-evolution track; Phase 3 migration and manual toolchain validation pending. |
| `pipeline-config` | Transitional extension | Resolves JSON into the canonical Java Pipeline; selected preview aliases and Action lifecycle features remain during migration. |
| `pipeline-remote` | Transitional extension | Remote operations execute as ordinary Actions through the canonical Java runner. |
| `pipeline-prompt` | Strategic extension | Prompt-to-code remains first-class and generates ordinary language-native Actions. |
| `pipeline-api` | Legacy extension | Typed chains, labels, and jumps remain outside the vNext core contract. Unary compiled execution delegates to the canonical runner. |
| `RuntimePipeline` | Deprecated compatibility helper | Interactive immediate execution now delegates to the canonical runner. |
| `pipeline-disruptor` | Experimental | Queueing wrapper; not part of core semantics or compatibility guarantees. |

## Java vNext compatibility boundary

The canonical Java surface is centered on:

```text
Pipeline<C>
Action<C>
PipelineExecution.shortCircuit()
PipelineResult<C>
PipelineProvider<C>
PipelineRouter<E, C>
PipelineObserver
```

The common execution method is:

```text
run(context) → context
```

The diagnostic method is:

```text
runDetailed(context) → PipelineResult<context>
```

The Java core temporarily retains preview `StepAction<C>` and `ActionControl<C>` overloads as compatibility adapters. They do not introduce a second execution engine.

See:

- [Java Reference Implementation](vnext/JAVA_REFERENCE_IMPLEMENTATION.md)
- [Java Migration Guide](vnext/JAVA_MIGRATION.md)

## Experimental and non-release directories

- `src/Java/pipeline-api-pr/`: incubating Java API work area, outside the supported release build.
- `statemachine/`: separate state-machine experiment, outside the Pipeline core contract.
- `archive/`: historical snapshots and work-in-progress material.
- `pipeline_services/`: Pixi-managed Mojo workspace for manual validation.

## Compatibility promise

During the phased migration:

- Java core behavior is defined by `docs/vnext/PORTABILITY_CONTRACT.md`.
- Non-Java preview behavior remains defined by `docs/PORTABILITY_CONTRACT.md` until each port is migrated.
- Shared JSON and prompt-to-code artifacts remain supported through transitional adapters.
- Experimental workflow, state-machine, queueing, and package-publication surfaces are not part of the core compatibility promise.
