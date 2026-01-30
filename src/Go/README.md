# Pipeline Services — Go port

This Go port mirrors the Java reference semantics (`pre → main → post`, `shortCircuitOnException`, `onError`, JSON loader, remote HTTP adapter) while using idiomatic exported Go identifiers.

## Execution API
- `Pipeline.Run(input)` returns `PipelineResult[T]` (final context + short-circuit flag + errors + timings)
- `Pipeline.Execute(input)` is a backwards-compatible alias for `Run`

If you need explicit lifecycle control (shared vs pooled vs per-run), use `PipelineProvider`:

```go
package main

import (
  "fmt"

  "pipeline-services-go/pipeline_services/core"
  "pipeline-services-go/pipeline_services/examples"
)

func buildProgrammaticPooledPipeline() *core.Pipeline[string] {
  pipeline := core.NewPipeline[string]("programmatic_pooled", true)
  pipeline.AddActionNamed("strip", examples.Strip)
  return pipeline
}

func main() {
  provider := core.NewPooledPipelineProvider[string](buildProgrammaticPooledPipeline, 64)
  result := provider.Run("  hello   world  ")
  fmt.Println(result.Context)
}
```

## Build and test
```bash
cd src/Go
go test ./...
```

## Run examples
```bash
cd src/Go
go run ./examples/example01_text_clean
go run ./examples/example02_json_loader
go run ./examples/example03_runtime_pipeline
go run ./examples/example05_metrics_post_action
go run ./examples/benchmark01_pipeline_run
```

Remote example (requires a local HTTP server):

```bash
cd src/Go
python3 -m http.server 8765 --bind 127.0.0.1 -d examples/fixtures
go run ./examples/example04_json_loader_remote_get
```
