# Pipeline Services — C# port

This folder contains the C# port of Pipeline Services, aligned with the Java reference semantics (`pre → main → post`, `shortCircuitOnException`, `onError`, JSON loader, remote HTTP adapter, timings + metrics post-action).

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

