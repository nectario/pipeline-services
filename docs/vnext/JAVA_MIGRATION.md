# Java Preview-to-vNext Migration

This guide covers the Java API changes introduced by the vNext kernel.

## 1. `run()` now returns the context

Before:

```java
PipelineResult<String> result = pipeline.run(input);
String output = result.context();
```

After, for ordinary execution:

```java
String output = pipeline.run(input);
```

Use `runDetailed()` when diagnostics are required:

```java
PipelineResult<String> result = pipeline.runDetailed(input);
```

The two methods use the same runner.

## 2. Prefer one-argument Actions

Before:

```java
pipeline.addAction((context, control) -> {
    control.shortCircuit();
    return context;
});
```

After:

```java
import static com.pipeline.core.PipelineExecution.shortCircuit;

pipeline.addAction(context -> {
    shortCircuit();
    return context;
});
```

The preview `StepAction<C>` and `ActionControl<C>` overloads remain temporarily available as compatibility adapters, but new code should use the canonical one-argument Action shape.

`shortCircuit()` must be called while the Action body is actively executing. It cannot be invoked from an observer, error handler, or detached asynchronous task.

## 3. Actions may still live anywhere

```java
public final class OrderActions {
    private OrderActions() {}

    public static OrderContext validate(OrderContext context) {
        if (!context.isValid()) {
            shortCircuit();
            return context.reject("Invalid order");
        }
        return context;
    }
}
```

Registration remains an ordinary method reference:

```java
pipeline.addAction(OrderActions::validate);
```

## 4. Checked exceptions need no wrapper

```java
static Context load(Context context) throws IOException {
    return context;
}

pipeline.addAction(MyActions::load);
```

The runner records the exception and applies `shortCircuitOnException`.

## 5. Provider terminology and behavior

Preview names map as follows:

| Preview | vNext |
| --- | --- |
| `shared(...)` | `singleton(...)` |
| `perRun(...)` | `newInstancePerEvent(...)` |
| `pooled(...)` leased object pool | `pooled(...)` fixed reusable instances |

The vNext modes are:

```java
PipelineProviderMode.NEW_INSTANCE_PER_EVENT
PipelineProviderMode.SINGLETON
PipelineProviderMode.POOLED
```

POOLED selects from eagerly created instances, round robin by default. It has no borrow/release or waiting behavior.

## 6. Provider execution

Before:

```java
String output = provider.run(input).context();
```

After:

```java
String output = provider.run(input);
```

Diagnostics:

```java
PipelineResult<String> result = provider.runDetailed(input);
```

## 7. Global metrics are removed from the core

The preview `com.pipeline.metrics.Metrics`, `MetricsRecorder`, and `SimpleMetricsRecorder` classes are removed.

Attach one observer instead:

```java
pipeline.observer(myObserver);
```

Micrometer, logging, tracing, and other integrations should implement `PipelineObserver` outside the core module.

## 8. Pipeline plans freeze

A Pipeline freezes on first execution or explicit `freeze()`.

```java
pipeline.run(input);
pipeline.addAction(nextAction); // throws IllegalStateException
```

Complete assembly before shared execution.

## 9. Name-only programmatic placeholders are removed

Before:

```java
pipeline.addAction("todo");
```

That overload silently registered an identity Action and is no longer part of the canonical API.

Use an explicit identity Action only when genuinely intended:

```java
pipeline.addAction("todo", context -> context);
```

JSON may continue to resolve an explicit `identity` built-in during the configuration migration.

## 10. Stateful action pooling

The preview provider-level `withPooledLocalActions(...)` mechanism is removed from the canonical provider API.

The vNext POOLED mode pools Pipeline instances, not leased Action instances. User Actions must be concurrency-safe when a provider mode permits overlapping use.

Loader-specific preview lifecycle support remains temporarily isolated inside `pipeline-config` and will be addressed with the extension cleanup.

## 11. RuntimePipeline

`RuntimePipeline<T>` remains temporarily available but is deprecated. It now delegates each immediate execution to the canonical runner and can still freeze its recorded Actions into a normal Pipeline.

New application code should prefer ordinary Pipeline construction.

## 12. Higher-level facade

`com.pipeline.api.Pipeline<I, C>` remains a legacy extension for typed transformations, labels, and jumps.

The vNext core is the single-context `com.pipeline.core.Pipeline<C>`. Type-changing chains and arbitrary jump workflows are outside the core contract and will be separated during extension cleanup.

## 13. Diagnostic accessor names

Preview diagnostic vocabulary that used `step` or a generic `index` now uses explicit Action terminology:

```java
pipelineError.actionIndex();
pipelineError.actionName();
actionTiming.actionIndex();
actionTiming.actionName();
```

This aligns Java with the vNext `preActions`, `actions`, and `postActions` vocabulary.
