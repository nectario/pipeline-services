# Pipeline Services — C++ port

This C++ port follows the shared semantics in `docs/PORTABILITY_CONTRACT.md` and intentionally uses Java-style `camelCase` naming for the public API (`addAction`, `shortCircuit`, ...), so the ports stay easy to compare.

## Execution API
- `Pipeline::run(input)` returns `PipelineResult<T>` (final context + short-circuit flag + errors + timings)
- `Pipeline::execute(input)` is a backwards-compatible alias for `run`

If you need explicit lifecycle control (shared vs pooled vs per-run), use `PipelineProvider`:

```cpp
#include <iostream>
#include <string>

#include "pipeline_services/core/pipeline.hpp"
#include "pipeline_services/core/pipeline_provider.hpp"
#include "pipeline_services/examples/text_steps.hpp"

pipeline_services::core::Pipeline<std::string> buildProgrammaticPooledPipeline() {
  pipeline_services::core::Pipeline<std::string> pipeline("programmatic_pooled", true);
  pipeline.addAction("strip", pipeline_services::examples::strip);
  return pipeline;
}

auto provider = pipeline_services::core::PipelineProvider<std::string>::pooled(
  buildProgrammaticPooledPipeline,
  64
);

auto result = provider.run("  hello   world  ");
std::cout << result.context << std::endl;
```

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

