# Java Benchmark Smoke

Pipeline Services includes a small informational benchmark harness at:

```text
src/Java/pipeline-examples/src/main/java/com/pipeline/examples/Benchmark01CorePipeline.java
```

It compares three equivalent three-step paths:

1. direct Java method calls;
2. `Pipeline.run()`;
3. `Pipeline.runDetailed()`.

The benchmark is executed after the Java test suite in CI. It is explicitly **not** a performance threshold and is not a substitute for JMH, production profiling, or application-specific latency testing.

## GitHub Actions run 51

Environment:

- GitHub-hosted Ubuntu runner;
- Temurin JDK 21.0.11;
- 20,000 warm-up iterations;
- 100,000 measured iterations;
- three simple String Actions.

Observed output:

```text
directNsPerOp=255.1366
pipelineRunNsPerOp=632.58404
pipelineRunDetailedNsPerOp=830.97599
pipelineRunOverDirect=2.4793935483972116
runDetailedOverRun=1.3136214913041437
```

Interpretation:

- The simple `run()` path completed in roughly 633 ns per three-Action execution on this runner.
- `runDetailed()` was approximately 31% slower than `run()` because it records per-Action timing diagnostics.
- The direct-call comparison is useful as a rough lower-bound reference, not as a stable cross-machine baseline.
- No performance guarantee should be inferred from one shared CI runner.

The important architectural result is that ordinary `run()` avoids diagnostic timing allocations, while `runDetailed()` pays explicitly for the additional information it returns.

## Running locally

After compiling the repository:

```bash
./mvnw -q test
java \
  -cp "src/Java/pipeline-examples/target/classes:src/Java/pipeline-core/target/classes" \
  com.pipeline.examples.Benchmark01CorePipeline
```

For serious low-latency analysis, use JMH and benchmark the actual context type, Action count, error policy, observer, provider mode, allocation profile, and concurrency pattern used by the application.
