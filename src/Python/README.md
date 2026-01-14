# Pipeline Services — Python port

This is the Python port of Pipeline Services, kept intentionally close to the Mojo `pipeline_services` package.
It implements the shared semantics described in `docs/PORTABILITY_CONTRACT.md`.

## What’s implemented
- `Pipeline` with `pre` / `actions` / `post`
- two action shapes:
  - unary: `fn(ctx) -> ctx`
  - control-aware: `fn(ctx, control) -> ctx`
- exception capture + `shortCircuitOnException` semantics (errors recorded; stop-vs-continue is configurable)
- JSON loader with `$local` (via `PipelineRegistry`) and `$remote` (HTTP) actions
- `RemoteDefaults` to avoid repeating base URL / timeout / retries / headers across many remote actions
- `RuntimePipeline` for incremental/interactive building
- `print_metrics` as a post-action (action timings and pipeline latency)
- example scripts + a simple end-to-end benchmark

## Run examples
From the repo root:

```bash
cd src/Python
python3 -m pipeline_services.examples.example01_text_clean
python3 -m pipeline_services.examples.example02_json_loader
python3 -m pipeline_services.examples.example05_metrics_post_action
python3 -m pipeline_services.examples.benchmark01_pipeline_run
```

Remote example (requires a local HTTP server):

```bash
cd src/Python
python3 -m http.server 8765 --bind 127.0.0.1 -d pipeline_services/examples/fixtures
python3 -m pipeline_services.examples.example04_json_loader_remote_get
```
