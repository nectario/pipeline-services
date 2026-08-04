# Java Reference Implementation

The Java implementation is the reference realization of the Pipeline Services vNext contract.

Its center is intentionally small:

```text
Pipeline<C>
Action<C>
PipelineExecution.shortCircuit()
PipelineResult<C>
PipelineProvider<C>
PipelineRouter<E, C>
```

`pipeline-core` has no runtime dependency on JSON, HTTP, logging, Micrometer, prompt tooling, or any other infrastructure library.

## 1. Ordinary Actions

An Action receives one context and returns the next context:

```java
@FunctionalInterface
public interface Action<C> {
    C execute(C context) throws Exception;
}
```

Actions may be instance methods, static methods, lambdas, method references, callable objects, remote adapters, or LLM-generated classes.

```java
static OrderContext validate(OrderContext context) {
    return context;
}
```

Checked exceptions are accepted directly and are handled by the Pipeline error policy.

## 2. Direct construction

```java
Pipeline<OrderContext> pipeline =
    new Pipeline<OrderContext>("orderPipeline")
        .addPreAction(OrderActions::validateRequest)
        .addAction(OrderActions::loadCustomer)
        .addAction(OrderActions::priceOrder)
        .addPostAction(OrderActions::audit);
```

Direct construction is the primary Java API.

## 3. Composition

```java
public final class OrderProcessor {
    private final Pipeline<OrderContext> pipeline;

    public OrderProcessor() {
        pipeline = new Pipeline<OrderContext>("orderPipeline")
            .addPreAction(this::validateRequest)
            .addAction(this::priceOrder)
            .addPostAction(this::audit);
    }

    public OrderContext process(OrderContext context) {
        return pipeline.run(context);
    }

    private OrderContext validateRequest(OrderContext context) {
        return context;
    }

    private OrderContext priceOrder(OrderContext context) {
        return context;
    }

    private OrderContext audit(OrderContext context) {
        return context;
    }
}
```

Composition uses exactly the same Pipeline plan and runner as every other construction style.

## 4. Subclassing

```java
public final class OrderPipeline extends Pipeline<OrderContext> {
    public OrderPipeline() {
        super("orderPipeline");

        addPreAction(this::validateRequest);
        addAction(this::priceOrder);
        addPostAction(this::audit);
    }

    private OrderContext validateRequest(OrderContext context) {
        return context;
    }

    private OrderContext priceOrder(OrderContext context) {
        return context;
    }

    private OrderContext audit(OrderContext context) {
        return context;
    }
}
```

The canonical `Pipeline<C>` is subclassable. Subclassing does not introduce a separate engine.

## 5. Builder

The builder is optional syntax over the same Pipeline instance:

```java
Pipeline<OrderContext> pipeline =
    Pipeline.<OrderContext>builder("orderPipeline")
        .addPreAction(OrderActions::validateRequest)
        .addAction(OrderActions::priceOrder)
        .addPostAction(OrderActions::audit)
        .build();
```

It has no builder-only runtime feature or alternate vocabulary.

## 6. Short circuit

An Action may be declared anywhere and call the execution-scoped function:

```java
import static com.pipeline.core.PipelineExecution.shortCircuit;

static OrderContext validate(OrderContext context) {
    if (!context.isValid()) {
        shortCircuit();
        return context.reject("Invalid request");
    }

    return context;
}
```

`shortCircuit()`:

- may only be called during an active Pipeline run;
- marks the innermost active run;
- allows the current Action to return its updated context normally;
- skips remaining main Actions;
- never skips remaining postActions;
- is isolated across nested and overlapping runs.

A subclass may call the inherited `shortCircuit()` convenience method. A Pipeline instance also exposes the same convenience method. Both delegate to the same execution-scoped operation.

## 7. Running a Pipeline

The common path returns the final context:

```java
OrderContext result = pipeline.run(input);
```

The diagnostic path uses the same runner and returns errors, short-circuit state, and timings:

```java
PipelineResult<OrderContext> result = pipeline.runDetailed(input);
```

`run()` does not allocate per-Action timing records. `runDetailed()` does.

## 8. Plan lifecycle

A Pipeline remains mutable while it is being assembled. It freezes:

- explicitly through `freeze()`;
- when a builder calls `build()`;
- when a PipelineProvider accepts or creates a shared instance;
- automatically on first `run()` or `runDetailed()`.

After freezing, structural mutation fails clearly.

Each run receives independent execution state. User Action objects still remain responsible for their own thread safety.

## 9. PipelineProvider

```java
PipelineProvider<OrderContext> perEvent =
    PipelineProvider.newInstancePerEvent(OrderPipeline::new);

PipelineProvider<OrderContext> singleton =
    PipelineProvider.singleton(new OrderPipeline());

PipelineProvider<OrderContext> pooled =
    PipelineProvider.pooled(OrderPipeline::new, 8);
```

Provider modes are:

```text
NEW_INSTANCE_PER_EVENT
SINGLETON
POOLED
```

POOLED eagerly creates a fixed collection of Pipeline instances and selects round robin by default. It does not borrow, release, wait, schedule, create threads, or guarantee exclusive ownership.

## 10. PipelineRouter

```java
PipelineRouter<TradingEvent, TradingContext> router = event ->
    switch (event.type()) {
        case TRADE -> tradePipelineProvider;
        case QUOTE -> quotePipelineProvider;
    };
```

The responsibility chain is:

```text
PipelineRouter routes.
PipelineProvider supplies.
Pipeline executes.
```

## 11. Observability

`PipelineObserver` is the single core observability seam:

```java
pipeline.observer(observer);
```

Observer failures are ignored and cannot change Pipeline semantics. Logging, Micrometer, tracing, and low-latency telemetry belong in adapters implementing this interface.

## 12. Compatibility boundary

The Java core temporarily retains overloads for the preview `StepAction<C>` and `ActionControl<C>` shape. They adapt into the canonical Action and the same runner.

`RuntimePipeline<T>` is retained as a deprecated interactive helper and now delegates immediate execution to the canonical runner.

The higher-level `com.pipeline.api.Pipeline<I, C>` facade remains a legacy extension for typed chains and jumps. Its unary compiled path delegates diagnostics to the canonical core runner, but typed chains and arbitrary jumps are not part of the vNext core contract.
