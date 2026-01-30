# Pipeline Services — C# port

This folder contains the C# port of Pipeline Services, aligned with the Java reference semantics (`pre → main → post`, `shortCircuitOnException`, `onError`, JSON loader, remote HTTP adapter, timings + metrics post-action).

## Execution API
- `Pipeline.Run(input)` returns `PipelineResult<T>` (final context + short-circuit flag + errors + timings)
- `Pipeline.Execute(input)` is a backwards-compatible alias for `Run`

If you need explicit lifecycle control (shared vs pooled vs per-run), use `PipelineProvider`:

```csharp
using System;

using PipelineServices.Core;
using PipelineServices.Examples;

public static class PipelineProviderDemo
{
    public static Pipeline<string> BuildProgrammaticPooledPipeline()
    {
        Pipeline<string> pipeline = new Pipeline<string>("programmatic_pooled", shortCircuitOnException: true);
        pipeline.AddAction("strip", TextActions.Strip);
        return pipeline;
    }
}

PipelineProvider<string> provider = PipelineProvider<string>.Pooled(PipelineProviderDemo.BuildProgrammaticPooledPipeline, poolMax: 64);
PipelineResult<string> result = provider.Run("  hello   world  ");
Console.WriteLine(result.Context);
```

## Build and test
```bash
cd src/CSharp
dotnet test ./pipeline_services_tests/PipelineServices.Tests.csproj
```

## Run examples
```bash
cd src/CSharp
dotnet run --project pipeline_services_examples -- example01_text_clean
dotnet run --project pipeline_services_examples -- example02_json_loader
dotnet run --project pipeline_services_examples -- example03_runtime_pipeline
dotnet run --project pipeline_services_examples -- example05_metrics_post_action
dotnet run --project pipeline_services_examples -- benchmark01_pipeline_run
```

Remote example (requires a local HTTP server):

```bash
cd src/CSharp
python3 -m http.server 8765 --bind 127.0.0.1 -d pipeline_services_examples/fixtures
dotnet run --project pipeline_services_examples -- example04_json_loader_remote_get
```
