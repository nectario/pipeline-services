# Pipeline Services — Go port

This directory contains the contract-aligned Go reference port of Pipeline Services. It follows the vNext semantics while keeping the public API idiomatic for Go.

## Execution API

```go
output := pipeline.Run(input)
result := pipeline.RunDetailed(input)
```

- `Run` returns the final context through the canonical runner.
- `RunDetailed` uses the same runner and returns errors, action timings, and short-circuit state.
- A Pipeline freezes on first execution or explicit `Freeze()`.

## Explicit Go short-circuit control

Go intentionally uses a small explicit execution handle for Actions that need to short-circuit:

```go
func validateOrder(
    context OrderContext,
    execution core.PipelineExecution,
) OrderContext {
    if !context.Valid {
        execution.ShortCircuit()
        return context.Reject("invalid order")
    }
    return context
}

pipeline.AddAction(validateOrder)
```

This is the one deliberate syntax deviation from the ambient `shortCircuit()` spelling used by several other ports. Go does not expose supported goroutine-local storage, so parsing goroutine IDs or relying on process-global execution state would be fragile and unnecessarily expensive. The semantics remain identical:

- the operation is valid only while the current Action is executing;
- the Action returns its updated context normally;
- remaining main Actions are skipped;
- all postActions still run;
- nested and overlapping executions remain isolated.

The package-level `core.ShortCircuit()` function is retained only as a migration guard and fails with an instruction to use `PipelineExecution`.

## PipelineProvider

```go
provider := core.Pooled(buildPipeline, 8)
output := provider.Run(input)
```

Provider modes are:

```text
NEW_INSTANCE_PER_EVENT
SINGLETON
POOLED
```

POOLED eagerly constructs a fixed set of Pipelines and selects round robin. It is not a thread pool and does not use borrow/release or exclusive ownership.

## Build and test

```bash
cd src/Go
go test ./...
go test -race ./...
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
