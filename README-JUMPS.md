# Labeled Jumps (Polling / Data-availability)

This PR adds a **jump-by-label** engine so a step can **wait for data** by looping to a label:

```java
import com.pipeline.core.Jumps;

if (!repo.isReady(id)) {
  Jumps.after("await", Duration.ofSeconds(10)); // re-enter step labeled 'await'
}
```

- **Opt-in**: `pipeline.enableJumps(true)` (pipelines without jumps remain on the fast path).
- **Guards**: `maxJumpsPerRun(128)` prevents runaway loops; jumps into **pre** are disallowed.
- **Typed safety**: typed JSON checks that your jump target's `in` type matches the current value.

## API

```java
// Enable & configure
pipeline.enableJumps(true)
        .maxJumpsPerRun(128)
        .sleeper(ms -> Thread.sleep(ms)); // overrideable (tests)

// From inside a step:
Jumps.now("label");
Jumps.after("label", Duration.ofSeconds(10));

// Optional external helpers (debugging):
pipeline.jumpTo("label");              // next run starts at label (one-shot)
pipeline.runFrom(input, "label");      // unary
pipeline.runFrom(input, "label", Out.class); // typed
```

## JSON

Use the `"label"` field you already have. Example (typed):

```json
{
  "pipeline": "await_features",
  "type": "typed",
  "inType": "com.pipeline.examples.steps.FinanceSteps$Features",
  "outType": "com.pipeline.examples.steps.FinanceSteps$Score",
  "steps": [
    { "label":"await",
      "in":"...$Features","out":"...$Features",
      "$method":{"ref":"com.pipeline.examples.polling.AvailabilityTyped#awaitFeatures"} },
    { "label":"score",
      "in":"...$Features","out":"...$Score",
      "$method":{"ref":"com.pipeline.examples.steps.FinanceSteps#score"} }
  ]
}
```

## Included examples

- `ExampleUnaryPollingJump` — unary self-loop using a small `Availability.awaitJob` helper.
- `ExampleTypedPollingJson` — typed JSON self-loop from `Features` to `Features`, then score.

Override sleeper in examples so they **don't actually sleep**:

```java
pipeline.enableJumps(true).sleeper(ms -> {}); // test-friendly, no delay
```

## Notes

- Keep your jumped-to step **idempotent**.
- Prefer **self-loops** or jumps to a preparatory step that returns the same type.
- Jumps to **pre** are blocked; jumps to `steps` or `post` are allowed.
- Short-circuit semantics are preserved: a jump is a control signal, not an error.
