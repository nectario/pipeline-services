# Pipeline Services — Mojo port

This folder contains the Mojo port of Pipeline Services. Phase 3 now targets the current stable Mojo `1.0.0b2` toolchain through `pixi`, rather than the former nightly `0.26.x` environment.

The vNext core is being compiled directly in GitHub Actions while the remaining provider, router, configuration, remote-action, and conformance layers are migrated to the same semantics as the other language ports.

## Current vNext direction

- One ordinary `PythonObject -> PythonObject` Action shape.
- `run()` returns the final context.
- `run_detailed()` returns `PipelineResult` diagnostics.
- `short_circuit()` is scoped to the currently executing Action.
- `preActions`, `actions`, and `postActions` retain the shared portability semantics.
- Pipeline plans freeze before shared execution.
- Mojo-native snake_case naming is used throughout.

## Toolchain

The pinned environment is defined in `pipeline_services/pixi.toml`:

```bash
cd pipeline_services
pixi install
pixi run mojo --version
pixi run mojo run -I ../src/Mojo ../src/Mojo/pipeline_services/examples/example00_import.mojo
```

The former Mojo implementation remains useful as migration reference for JSON, remote Actions, prompt-generated Actions, and examples, but its nightly-era syntax is not considered the vNext API.
