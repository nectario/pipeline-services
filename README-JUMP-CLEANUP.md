# Jump cleanup: only **jumpTo** and **jumpIf**

This drop removes backward-compat and keeps a **simple, two-concept** API:

- **`Pipeline.jumpTo(label)`** — schedule the **next run** to start at a labeled step (one‑shot).
- **`StepsCond.jumpIf(label, predicate[, delay])`** — from **inside the pipeline**,
  conditionally jump to a labeled step; optional delay (polling loops).

JSON continues to support **`jumpWhen`** on steps, which compiles to the same pass‑through wrapper
before the step.

## Programmatic example
```java
var p = Pipeline.<String,String>named("conditional_unary", false)
    .enableJumps(true)
    .addAction("check", TextSteps::strip)
    .addAction("pre-check",
        StepsCond.jumpIf("check", PricePredicates::isEmptyData, Duration.ofMillis(10)))
    .addAction("normalize", TextSteps::normalizeWhitespace);
```

## JSON example (typed)
```json
{
  "label": "score",
  "in": "com.pipeline.examples.steps.FinanceSteps$Features",
  "out": "com.pipeline.examples.steps.FinanceSteps$Score",
  "jumpWhen": {
    "label": "await",
    "delayMillis": 5,
    "predicate": { "$method": { "ref": "com.pipeline.examples.conditions.TypedPredicates#needsAwait" } }
  },
  "$method": { "ref":"com.pipeline.examples.steps.FinanceSteps#score" }
}
```

### Notes & guardrails
- Enable the engine: `pipeline.enableJumps(true)` (pipelines without jumps stay on the fast path).
- Guard loops: `pipeline.maxJumpsPerRun(128)`; jumps into **pre** are rejected.
- Typed safety: jump target input type is checked at runtime for typed JSON.
