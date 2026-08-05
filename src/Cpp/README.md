# Pipeline Services — C++ port

This directory contains the contract-aligned C++20 reference port. It uses the same vNext runtime model as Java while following the project’s PascalCase/camelCase convention.

## Execution API

```cpp
pipeline_services::Pipeline<std::string> pipeline("cleanText");
pipeline.addAction(strip);

std::string output = pipeline.run("  hello  ");
auto detailed = pipeline.runDetailed("  hello  ");
```

The port now provides:

- ordinary `Context -> Context` Actions;
- one immutable Pipeline plan and one runner;
- execution-scoped `pipeline_services::shortCircuit()`;
- `run()` returning the final context;
- `runDetailed()` returning diagnostics;
- post-action guarantees, nested-run isolation, and thread-local concurrent-run isolation;
- eager `NEW_INSTANCE_PER_EVENT`, `SINGLETON`, and round-robin `POOLED` providers;
- a separate `PipelineRouter`;
- canonical JSON, remote, and generated Actions using the same runner.

Preview `pipeline_services::core` aliases and control-aware Actions remain only as compatibility adapters.

## Provider example

```cpp
auto provider = pipeline_services::PipelineProvider<std::string>::pooled(
    buildPipeline,
    8);

std::string output = provider.run("  hello  ");
```

POOLED is a fixed reusable instance set. It does not borrow, release, block, schedule work, or promise exclusive ownership.

## Build and test

```bash
cd src/Cpp
cmake -S . -B build
cmake --build build -j
ctest --test-dir build --output-on-failure
```

Both the integration suite and `vnext_pipeline_test.cpp` are compiled and registered with CTest.
