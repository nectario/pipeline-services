# Porting Notes (Java → Mojo)

This describes the current Mojo port under `src/Mojo/pipeline_services/`.

## High-level mapping
- `pipeline-core` → `src/Mojo/pipeline_services/core/`
- `pipeline-config` → `src/Mojo/pipeline_services/config/`
- `pipeline-remote` → `src/Mojo/pipeline_services/remote/` (minimal helper)
- `pipeline-prompt` → `src/Mojo/pipeline_services/prompt/` (stub)
- `pipeline-disruptor` → `src/Mojo/pipeline_services/disruptor/` (stub)
- examples → `src/Mojo/pipeline_services/examples/`

## Key Mojo constraints (0.26.x)
- Values are move-only by default; `Pipeline` conforms to `Movable` so loaders/builders can return it.
- Avoid module-level mutable globals (toolchain limitations); registries are passed explicitly.
- Avoid closures and nested function defs; actions are plain function pointers.

## API differences vs Java
- Context is `PythonObject` for now to keep the port small and to enable Python interop (JSON parsing, regex helpers, HTTP).
- `$local` resolution uses `PipelineRegistry` instead of reflection-by-name.
- The JSON loader supports `$remote` actions (string or object), plus root-level `remoteDefaults` to avoid repeating base URL/timeouts/headers.
- Exceptions from steps are caught by the pipeline, recorded as `PipelineError`, and optionally mapped back into the context via `Pipeline.on_error_handler`.

## Run
See `src/Mojo/README.md` for `pixi` commands.
