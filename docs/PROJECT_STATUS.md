# Project Status

## Release scope for v0.1.0

`v0.1.0` is an initial public preview of Pipeline Services as a locality-aware software architecture framework.

The release is centered on:
- the Java reference implementation
- the shared behavior contract in `docs/PORTABILITY_CONTRACT.md`
- in-repo reference ports that exercise the contract with tests/examples
- the JSON, remote-adapter, runtime-pipeline, and prompt-to-code flows described in the repo docs

The release does **not** include standalone publication to language-specific package registries.

## Port maturity matrix

| Surface | Status | Notes |
| --- | --- | --- |
| Java (`src/Java/`) | Reference implementation | Primary compatibility anchor for `v0.1.0`; Maven multi-module build and examples are part of the release surface. |
| Python (`src/Python/`) | Contract-aligned reference port | In-repo port with tests/examples; intended to validate the portability contract, not to imply separate packaging/release guarantees. |
| TypeScript (`src/typescript/`) | Contract-aligned reference port | In-repo reference package; marked private to make clear it is not a standalone npm release today. |
| Rust (`src/Rust/`) | Contract-aligned reference port | In-repo port with tests/examples; `publish = false` remains intentional for `v0.1.0`. |
| Go (`src/Go/`) | Contract-aligned reference port | In-repo port with tests/examples; current module path is optimized for repo evaluation rather than external module publication. |
| C# (`src/CSharp/`) | Contract-aligned reference port | In-repo port with tests/examples; not currently positioned as a standalone public NuGet surface. |
| C++ (`src/Cpp/`) | Contract-aligned reference port | In-repo port with examples/tests; part of the contract-validation story, not a package-distribution story. |
| Mojo (`src/Mojo/`, `pipeline_services/`) | Strategic target / experimental | Important runtime-evolution track; manual validation for now while the toolchain remains experimental for hosted CI. |
| `pipeline-disruptor` | Experimental | Present in the repo and examples, but currently single-thread only and not part of the core compatibility promise. |

## Experimental / non-release directories

- `src/Java/pipeline-api-pr/`: incubating Java API work area. It is **not** part of the public release build and is **not** part of the `v0.1.0` compatibility surface.
- `statemachine/`: standalone experimental state-machine prototype. It is **not** part of the main Pipeline Services API promise.
- `archive/`: historical snapshots and work-in-progress material kept for reference. It is **not** part of the supported release surface.
- `pipeline_services/`: Pixi-managed Mojo toolchain workspace used for manual Mojo validation. It supports the Mojo track but is not itself a separate public framework surface.

## What is and is not part of the compatibility promise

The `v0.1.0` compatibility promise is centered on:
- the observable semantics described in [PORTABILITY_CONTRACT.md](PORTABILITY_CONTRACT.md)
- the Java reference implementation modules in the main Maven build
- the canonical JSON pipeline shape (`actions`, `$local`, `$remote`, `remoteDefaults`)
- the documented prompt-to-code flow and generated-pipeline layout

The following are explicitly **not** covered by the `v0.1.0` compatibility promise:
- `src/Java/pipeline-api-pr/`
- `statemachine/`
- `archive/`
- standalone publication metadata for package registries
- stronger threading/performance guarantees for `pipeline-disruptor` beyond its current experimental, single-thread implementation
