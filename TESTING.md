# Testing

Pipeline Services has active test coverage across the Java reference implementation and the in-repo reference ports for Python, TypeScript, Rust, Go, C#, and C++.

## Java

Run the full Maven test suite from the repo root:

```bash
./mvnw -q test
```

The Java suite covers the reference implementation in `pipeline-core`, the jump/metrics facade in `pipeline-api`, the JSON loader in `pipeline-config`, and the HTTP adapter in `pipeline-remote`.

Visible `pipeline-core` test classes:
- `PipelineProviderTest`
- `PipelineTest`
- `PooledLocalActionsProgrammaticTest`
- `RuntimePipelineFreezeTest`
- `RuntimePipelineTest`
- `StepsTest`

Visible `pipeline-api` test classes:
- `JumpEngineGuardsTest`
- `JumpEngineTypedTest`
- `JumpEngineUnaryTest`
- `JumpToStartTest`
- `MetricsCompiledPathTest`
- `MetricsErrorTest`
- `MetricsJumpEngineTest`
- `TestMetrics`

Additional Java coverage currently includes:
- `PipelineJsonLoaderBuiltinsTest`
- `PipelineJsonLoaderSingletonModeTest`
- `HttpStepTest`

## Python

```bash
PYTHONPATH=src/Python python -m unittest discover -s src/Python/tests -p 'test_*.py'
```

Visible Python test modules:
- `test_pipeline.py`
- `test_json_loader.py`
- `test_remote_http.py`

## TypeScript

```bash
cd src/typescript
npm ci
npm test
```

## Rust

```bash
cd src/Rust
cargo test
```

## Go

```bash
cd src/Go
go test ./...
```

## C#

```bash
dotnet test src/CSharp/pipeline_services_tests/PipelineServices.Tests.csproj
```

## C++

```bash
cmake -S src/Cpp -B src/Cpp/build
cmake --build src/Cpp/build -j
ctest --test-dir src/Cpp/build --output-on-failure
```

## Mojo

Mojo validation remains manual for now. The toolchain lives under `pipeline_services/pixi.toml`, and the examples documented in [README.md](README.md) are the current manual verification path until the Mojo toolchain is pinned cleanly for GitHub-hosted CI runners.
