# Built-in Metrics & Logging

`pipeline-api` supports optional **per-run/step** metrics:

- **Per run**: `pipeline.start` / `pipeline.end` (duration, success/error)
- **Per step**: `step.start` / `step.end` (duration), `step.error`
- **Jumps**: `step.jump` events with from/to label and delay

### How to enable

```java
import com.pipeline.api.Pipeline;
import com.pipeline.core.metrics.LoggingMetrics;

var p = new Pipeline<String,String>()
    .metrics(new LoggingMetrics())    // <— enable logging metrics
    .enableJumps(true);
```

`LoggingMetrics` uses `java.util.logging` so it works out of the box without extra deps.

### Custom metrics

Implement `com.pipeline.core.metrics.Metrics` and pass your sink to `pipeline.metrics(new YourMetrics())`.
For Micrometer/Prometheus, write a small adapter that increments counters and records timers.

### Notes

- Pipelines that **don’t** enable jumps still get per-step metrics on the compiled path.
- Per-step wrappers are installed at seal time for the compiled core, and inline for the jump engine.
- Run-scoped correlation is provided via a `runId` passed to the `RunScope`.

### Disruptor example

See `pipeline-examples/disruptor` for both **programmatic** and **JSON** pipelines emitting metrics.
Add to `pipeline-examples` module POM:

```xml
<dependency>
  <groupId>com.lmax</groupId>
  <artifactId>disruptor</artifactId>
  <version>3.4.4</version>
</dependency>
```
