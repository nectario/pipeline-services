# Pipeline Services — Mojo port

This folder contains the Mojo port of Pipeline Services, targeting Mojo `0.26.x` via `pixi`.

## What’s implemented
- Core runtime: `Pipeline`, `ActionControl` (legacy: `StepControl`), `PipelineResult`, `PipelineError`
- Two action shapes:
  - unary: `fn(ctx: PythonObject) -> PythonObject`
  - control-aware: `fn(ctx: PythonObject, mut control: ActionControl) -> PythonObject`
- Exception capture + `shortCircuitOnException` semantics (see `docs/PORTABILITY_CONTRACT.md`)
- Minimal JSON loader using a registry:
  - `$local` actions from `PipelineRegistry`
  - `$remote` HTTP actions (GET/POST) via `urllib.request`
  - `pre|actions|post` sections (`steps` is accepted as an alias for `actions`)
  - root-level `remoteDefaults` (base URL, timeout, retries, headers)
- Timings captured per action + `print_metrics` post action
- End-to-end benchmark example (`benchmark01_pipeline_run.mojo`)

## What’s stubbed / pending
- Prompt + Disruptor modules are placeholders for later prompt code generation + async runner work

## Layout
- `src/Mojo/pipeline_services/core/` — core pipeline runtime
- `src/Mojo/pipeline_services/config/` — JSON loader
- `src/Mojo/pipeline_services/examples/` — smoke examples

## Run (pixi)
Mojo toolchain lives under `pipeline_services/pixi.toml`.

```bash
cd pipeline_services
pixi run mojo run -I ../src/Mojo ../src/Mojo/pipeline_services/examples/example01_text_clean.mojo
pixi run mojo run -I ../src/Mojo ../src/Mojo/pipeline_services/examples/example02_json_loader.mojo
pixi run mojo run -I ../src/Mojo ../src/Mojo/pipeline_services/examples/example03_runtime_pipeline.mojo
pixi run mojo run -I ../src/Mojo ../src/Mojo/pipeline_services/examples/example05_metrics_post_action.mojo
pixi run mojo run -I ../src/Mojo ../src/Mojo/pipeline_services/examples/benchmark01_pipeline_run.mojo
```

Remote JSON loader example:

```bash
cd pipeline_services
python3 -m http.server 8765 --bind 127.0.0.1 -d ../src/Mojo/pipeline_services/examples/fixtures
pixi run mojo run -I ../src/Mojo ../src/Mojo/pipeline_services/examples/example04_json_loader_remote_get.mojo
```
