# Pipeline Services — Rust port

This Rust port is aligned with the Mojo/Python/TypeScript `pipeline_services` package and the shared semantics in `docs/PORTABILITY_CONTRACT.md`.

## Execution API
- `Pipeline::run(input)` returns `PipelineResult<T>` (final context + short-circuit flag + errors + timings)
- `Pipeline::execute(input)` is a backwards-compatible alias for `run`

If you need explicit lifecycle control (shared vs pooled vs per-run), use `PipelineProvider`:

```rust
use pipeline_services::examples::text_steps::strip;
use pipeline_services::{Pipeline, PipelineProvider};

fn build_programmatic_pooled_pipeline() -> Pipeline<String> {
  let mut pipeline = Pipeline::new("programmatic_pooled", true);
  pipeline.add_action(strip);
  pipeline
}

fn main() {
  let provider = PipelineProvider::pooled(build_programmatic_pooled_pipeline, 64);
  let result = provider.run("  hello   world  ".to_string());
  println!("{}", result.context);
}
```

## Build and test
```bash
cd src/Rust
cargo test
```

## Run examples
```bash
cd src/Rust
cargo run --example example01_text_clean
cargo run --example example02_json_loader
cargo run --example example03_runtime_pipeline
cargo run --example example05_metrics_post_action
```

Remote example (requires a local HTTP server):

```bash
cd src/Rust
python3 -m http.server 8765 --bind 127.0.0.1 -d examples/fixtures
cargo run --example example04_json_loader_remote_get
```
