# Pipeline Services — TypeScript port

This TypeScript port is aligned with the Mojo/Python `pipeline_services` package and the shared semantics in `docs/PORTABILITY_CONTRACT.md`.

## What’s implemented
- `Pipeline` with `pre` / `actions` / `post`
- two action shapes:
  - unary: `(ctx) => ctx`
  - control-aware: `(ctx, control) => ctx` (explicit short-circuit)
- exception capture + `shortCircuitOnException` semantics (errors recorded; stop-vs-continue is configurable)
- JSON loader with `$local` (via `PipelineRegistry`) and `$remote` (HTTP) actions
- `RemoteDefaults` to avoid repeating base URL / timeout / retries / headers across many remote actions
- `RuntimePipeline` for incremental/interactive building
- `print_metrics` as a post-action (action timings and pipeline latency)

## Install, build, test
```bash
cd src/typescript
npm install
npm run build
npm test
```

## Run examples
```bash
cd src/typescript
node dist/src/pipeline_services/examples/example01_text_clean.js
node dist/src/pipeline_services/examples/example02_json_loader.js
node dist/src/pipeline_services/examples/example05_metrics_post_action.js
node dist/src/pipeline_services/examples/benchmark01_pipeline_run.js
```

Remote example (requires a local HTTP server):

```bash
cd src/typescript
python3 -m http.server 8765 --bind 127.0.0.1 -d src/pipeline_services/examples/fixtures
node dist/src/pipeline_services/examples/example04_json_loader_remote_get.js
```
