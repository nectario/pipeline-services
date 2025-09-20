# Conditional Jumps — API rename

We simplified the API:
- **Old**: `StepsCond.jumpIfAfter(label, predicate, Duration)`
- **New**: `StepsCond.jumpIf(label, predicate, Duration)` (overload)
- Immediate jump remains: `StepsCond.jumpIf(label, predicate)`

The old `jumpIfAfter(...)` still exists as **@Deprecated** and delegates to the new overload.

## Programmatic example

```java
.addAction("check", TextSteps::strip)
.addAction("pre-check",
    StepsCond.jumpIf("check", PricePredicates::isEmptyData, Duration.ofSeconds(10)))
.addAction("normalize", TextSteps::normalizeWhitespace);
```

JSON `jumpWhen` remains unchanged:
```json
"jumpWhen": {
  "label": "await",
  "delayMillis": 5000,
  "predicate": { "$method": { "ref": "com.yourorg.TypedPredicates#needsAwait" } }
}
```
