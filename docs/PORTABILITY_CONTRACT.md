# Pipeline Services — Portability Contract (v1.0)

This document is the **source of truth** for implementing and porting Pipeline Services across programming languages.
It defines the observable behavior, configuration surface, and interfaces that **every language port MUST implement**.

## 1. Core Abstractions

### 1.1 Step
- **Signature:** Unary step with the contract `T -> T` (in dynamically-typed languages, accept/return opaque values).
- **Allowed outcomes:**
  1. **Return** a value of type `T` (the new pipeline value).
  2. **Short-circuit:** signal early completion with a final value.
  3. **Jump:** signal a jump to a **label** (optionally with a delay).
  4. **Error:** raise/throw an error.
- **Side effects:** Allowed but discouraged; must not corrupt pipeline state if the step fails.

### 1.2 Pipeline (unary, labeled)
- A pipeline is an ordered list of labeled steps.
- **Labels**: Unique per pipeline; empty label allowed (non-addressable).
- **`before_each` / `after_each`**: optional lists of unary steps applied to the current value before/after every main step.
- **Jumps**: A step may request a **jump** to a **target label** (immediate or after `delayMillis`).
- **Short-circuit**: A step may **short-circuit** and finish the pipeline with the provided value.
- **Guardrails**:
  - `max_jumps` per run (default **1000**; ports MAY expose a setter).
  - **No jumps into `pre`** (if the language separates pre/steps/post in the runtime) — see §2.4.
- **Run options**: `run(input, start_label?, run_id?)`.
  - `start_label` must refer to an existing label in `steps` (not `pre`). If not present, start at index `0`.
  - `run_id` is an opaque string for metrics correlation.

### 1.3 Typed Pipe (I → O)
- A simple linear pipeline without labels/jumps.
- Short-circuit semantics apply.
- Type enforcement is **compile-time** in statically typed languages; dynamic languages MAY rely on duck typing.

### 1.4 Runtime Pipeline
- Imperative variant with `add_pre`, `step`, `add_post`, `reset`, `value`.
- Short-circuit semantics apply.
- Jumps are **not required** in the runtime variant.

## 2. Control Flow Semantics

### 2.1 Short-circuit
- Invoked by a **dedicated signal** (exception or structured return depending on language).
- Sets the **pipeline value** and **terminates** the run successfully.

### 2.2 Jump
- Invoked by a **dedicated signal** (exception or structured return).
- Contains `label: String` and optional `delayMillis: Int`.
- On jump: increment `jump_count`; if `jump_count > max_jumps` → **error** and terminate per short-circuit policy.
- **Label resolution** is against the **main steps** (not `pre`).

### 2.3 Error Handling
- If `short_circuit = true`: first unhandled step error **terminates** the run with error.
- If `short_circuit = false`: record error, **continue** with the current value unchanged.

### 2.4 Pre/Post Execution
- `pre` steps execute **once** before the first main step.
- `post` steps execute **once** after the last main step.
- Jumps **MUST NOT** target `pre` and **MUST** target a label in the main `steps` section.

## 3. Metrics

Every port MUST expose a `Metrics` interface and a default implementation. At minimum, the following events MUST be supported:

- `pipeline.start(name, runId, startLabel)`
- `pipeline.end(name, runId, durationNanos, success, error?)`
- `step.start(name, runId, index, label)`
- `step.end(name, runId, index, label, durationNanos, success)`
- `step.error(name, runId, index, label, error)`
- `step.jump(name, runId, fromLabel, toLabel, delayMillis)`

**Timing:** use a monotonic clock (e.g., `perf_counter_ns`) for durations.

## 4. JSON Configuration (Canonical)

Top-level object:

```json
{
  "pipeline": "name",
  "type": "unary" | "typed",
  "shortCircuit": true,
  "pre": [ StepNode ],
  "steps": [ StepNode ],
  "post": [ StepNode ]
}
```

**StepNode** is one of:

- `{ "$local": "package.module.ClassWithApply", "label": "optional-label" }`
- `{ "$method": { "ref": "package.module.Class#method" | "package.module:function", "target": "@this" | "@beanId" }, "label": "optional-label" }`
- `{ "$prompt": { /* free-form spec */ } }`
- `{ "$remote": { "endpoint": "...", "method": "POST|GET", "timeoutMillis": 1000, "retries": 0, "headers": {},
                    "toJsonBean": "optionalBeanId", "fromJsonBean": "optionalBeanId" } }`
- With an optional wrapper:
  - `"jumpWhen": { "label": "target-label", "delayMillis": 0, "predicate": StepNode }`

**Beans & Instance Binding**
- `@this` resolves to the loader instance.
- `@beanId` resolves to an object supplied in the loader's bean map.
- `$local` resolves `"ClassWithApply"` to an instance and calls `apply(x)`.

## 5. Prompt Steps

Ports MUST accept a **callable adapter** named `llm_adapter` with interface:

```
adapter(input_value, prompt_spec_dict) -> output_value
```

`prompt_spec_dict` SHOULD include: `name`, `goal`, `rules[]`, `examples[]`, `p50Micros` (optional).

## 6. Remote Steps

Define a `RemoteSpec` with: `endpoint: String`, `timeoutMillis: Int`, `retries: Int`, `headers: Map`, `method: "POST" | "GET"`, `to_json`, `from_json`.
The runtime MUST call `to_json(input_value)` and then invoke the HTTP operation, decode via `from_json`.

## 7. Disruptor Engine (Optional Module)

- Single-consumer engine with a bounded queue.
- `publish(payload)` enqueues without corrupting state if backpressure occurs (ports MAY block or raise on full).
- `shutdown()` stops the worker; `close()` aliases to `shutdown()`.

## 8. Guardrails and Defaults

- `max_jumps` default: **1000**.
- `shortCircuit` default: **true**.
- JSON loader MUST validate unknown labels on `start_label` / jump targets and raise a clear error.

## 9. Naming, Style, and Structure

- Follow each language's canonical style (e.g., snake_case in Python, Mojo; PascalCase for `struct`/classes).
- Public API names MUST match these identifiers where possible: `Pipeline`, `Pipe`, `RuntimePipeline`, `Metrics`, `LoggingMetrics`, `NoopMetrics`, `PipelineRegistry`, `PipelineJsonLoader`.

## 10. Compliance Checklist

- [ ] Implements §1–§4 core semantics
- [ ] Metrics events (§3)
- [ ] JSON forms (§4) incl. `jumpWhen`
- [ ] Prompt adapter contract (§5)
- [ ] Remote spec (§6)
- [ ] Engine behavior (§7) if provided
- [ ] Defaults (§8)
- [ ] Label & `pre`/`post` rules (§2.4)
