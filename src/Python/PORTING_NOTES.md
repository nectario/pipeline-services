# Porting Notes (Mojo → Python)

The Python port under `src/Python/pipeline_services/` mirrors the Mojo `pipeline_services` package as closely as practical.
The goal is semantic parity (not Python-idiomatic expansions): keep behavior stable across ports and rely on the shared contract.

## High-level mapping (Mojo → Python)
- `core/pipeline.mojo` → `pipeline_services/core/pipeline.py`
- `core/registry.mojo` → `pipeline_services/core/registry.py`
- `core/runtime_pipeline.mojo` → `pipeline_services/core/runtime_pipeline.py`
- `core/metrics_actions.mojo` → `pipeline_services/core/metrics_actions.py`
- `config/json_loader.mojo` → `pipeline_services/config/json_loader.py`
- `remote/http_step.mojo` → `pipeline_services/remote/http_step.py`
- examples → `pipeline_services/examples/`

## What’s intentionally in-scope
- `Pipeline` with `pre/actions/post`
- unary and control-aware actions
- exception capture with `shortCircuitOnException`
- `$local` via `PipelineRegistry` (no reflection)
- `$remote` via `urllib.request` + `RemoteDefaults`
- timings + `print_metrics` post action

## Running
```bash
cd src/Python
python3 -m pipeline_services.examples.example01_text_clean
```
