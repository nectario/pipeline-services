# Pipeline Services — C++ port

This C++ port is aligned with the Mojo/Python/TypeScript/Rust `pipeline_services` ports and the shared semantics in `docs/PORTABILITY_CONTRACT.md`.

## Build and test
```bash
cd src/Cpp
cmake -S . -B build
cmake --build build -j
ctest --test-dir build
```

## Run examples
```bash
cd src/Cpp
cmake -S . -B build
cmake --build build -j
./build/example01_text_clean
./build/example02_json_loader
./build/example03_runtime_pipeline
./build/example05_metrics_post_action
./build/benchmark01_pipeline_run
```

Remote example (requires a local HTTP server):

```bash
cd src/Cpp
python3 -m http.server 8765 --bind 127.0.0.1 -d examples/fixtures
./build/example04_json_loader_remote_get
```

