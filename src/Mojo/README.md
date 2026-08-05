# Pipeline Services — Mojo port

This folder contains the Mojo port of Pipeline Services. Phase 3 targets the current stable Mojo `1.0.0b2` toolchain through `pixi`, rather than the former nightly `0.26.x` environment.

## Implemented vNext surface

- One ordinary `PythonObject -> PythonObject` Action shape.
- Native Mojo functions and dynamic Python-backed callables both resolve to ordinary Actions.
- `run()` returns the final context.
- `run_detailed()` returns `PipelineResult` diagnostics.
- `short_circuit()` is scoped to the currently executing Action.
- `preActions`, `actions`, and `postActions` retain the shared portability semantics.
- Pipeline plans freeze before shared execution.
- `PipelineProvider` supports `new_instance_per_event`, `singleton`, and eager round-robin `pooled` modes.
- `PipelineRouter` remains separate from provider lifecycle.
- Canonical JSON, remote Actions, prompt-generated Actions, and the RuntimePipeline compatibility facade all use the same runner.
- Mojo-native snake_case naming is used throughout.

## Toolchain and verification

The pinned environment is defined in `pipeline_services/pixi.toml`:

```bash
cd pipeline_services
pixi install
pixi run mojo --version
pixi run mojo run -I ../src/Mojo ../src/Mojo/tests/vnext_pipeline_test.mojo
```

The conformance executable covers normal and detailed execution, phase-specific short-circuiting, exception policies, post-action guarantees, nested execution, observer isolation, provider modes, routing, canonical JSON, remote Actions, generated Actions, and RuntimePipeline delegation.

The former nightly-era implementation is no longer the active Mojo API. Its public concepts were migrated to the stable toolchain rather than preserved through obsolete syntax.
