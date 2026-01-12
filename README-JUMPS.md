# Labeled Jumps (Polling / Workflows)

Jumps are an opt-in control-flow feature provided by `pipeline-api`.
From inside a step you can signal “jump to label X” (optionally after a delay).

## API

```java
import com.pipeline.api.Pipeline;
import com.pipeline.core.Jumps;

var p = Pipeline.<String>named("unary_loop", /*shortCircuit=*/false)
    .enableJumps(true)
    .maxJumpsPerRun(128)
    .sleeper(ms -> {}) // tests: don't actually sleep
    .addAction("await", s -> {
      if (!s.endsWith(":ready")) Jumps.now("await"); // self-loop
      return s;
    })
    .addAction("done", s -> s + ":done");

System.out.println(p.run("x"));
```

## Notes
- Jumps are disabled by default; enable them with `pipeline.enableJumps(true)`.
- Guardrails:
  - `maxJumpsPerRun(n)` prevents runaway loops
  - Jumping into `pre` is rejected
- For typed pipelines (type changes), use `run(input, Out.class)` so the engine can validate jump target types.

