# Pipeline Services — Rust port

This Rust port is aligned with the Mojo/Python/TypeScript `pipeline_services` package and the shared semantics in `docs/PORTABILITY_CONTRACT.md`.

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
