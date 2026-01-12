# Pipeline Services — Portability Contract (v2)

This document is the source of truth for implementing Pipeline Services across languages (Java, Python, Mojo, …).
It defines observable behavior and the minimal API surface a port should expose.

## 1. Core Abstractions

### 1.1 `StepAction<C>` + `StepControl<C>`

Ports should support two step shapes:
- **Unary**: `C -> C` (simple transforms)
- **Control-aware**: `(C, control) -> C` (explicit short-circuit + error recording)

`StepControl<C>` provides:
- `shortCircuit()`: request early termination of MAIN actions
- `isShortCircuited()`: query current state
- `recordError(ctx, exception) -> ctx`: record an error and optionally return an updated ctx (via callback hook)
- `errors() -> [PipelineError]`: the accumulated errors

### 1.2 `Pipeline<C>`

A pipeline has three ordered phases:
- **pre**: runs once (always runs fully)
- **main**: runs once (stops early if short-circuited)
- **post**: runs once (always runs fully)

Pipelines are configured with:
- `name: String`
- `shortCircuitOnException: boolean` (default `true`)
- optional `onError(ctx, error) -> ctx` callback (default: no-op passthrough)

### 1.3 `PipelineResult<C>` + `PipelineError`

Executing a pipeline returns:
- `context: C` (final value)
- `shortCircuited: boolean`
- `errors: [PipelineError]` (may be empty)

`PipelineError` should include at least:
- pipeline name
- phase (`pre|main|post`)
- index + step label/name (if available)
- captured exception/error value

## 2. Control Flow Semantics

### 2.1 Explicit short-circuit
If a MAIN step calls `control.shortCircuit()`:
- MAIN stops after the current step completes
- POST still executes fully
- The result reports `shortCircuited=true`

### 2.2 Exception handling (`shortCircuitOnException`)
If a step throws/raises an exception:
- The error is recorded (append to `errors`)
- The `onError(ctx, error)` callback is invoked to optionally update the ctx
- If `shortCircuitOnException=true`, MAIN is short-circuited
- If `shortCircuitOnException=false`, MAIN continues

Ports should not require checked/declared exceptions on steps (Java: no checked throws on `StepAction`).

### 2.3 Pre/Post execution
- PRE always runs fully, even if `control.shortCircuit()` is set during PRE
- MAIN runs only if not already short-circuited
- POST always runs fully, even if short-circuited during MAIN

(Ports MAY make this configurable later, but the default must match the above.)

## 3. JSON Configuration (Canonical)

Minimal canonical form:

```json
{
  "pipeline": "name",
  "type": "unary",
  "shortCircuitOnException": true,
  "steps": [
    { "$local": "package.ClassImplementingUnaryOrStepAction" }
  ]
}
```

Notes:
- `shortCircuit` is an allowed legacy alias for `shortCircuitOnException`.
- `$local` should resolve a class/type by name and instantiate it (no-arg ctor in Java).
- A `$local` step may implement either the unary callable or the control-aware callable.

## 4. Extensions (Optional per port)

### 4.1 Labeled jumps
Some ports may provide labeled jumps (polling/workflows). If implemented:
- steps can signal a jump to a label (optionally with delay)
- there must be a guardrail `maxJumpsPerRun`
- jumps into PRE are rejected

### 4.2 Prompt steps (LLM scaffolding)
Ports may support build-time “prompt → code” generation. Runtime LLM calls are optional and must be explicit.

### 4.3 Remote steps
Ports may provide a `RemoteSpec<C>` that maps `C` to request JSON and maps `(C, responseJson)` back to `C`.

## 5. Naming

Public API names should match where possible:
- `Pipeline`, `StepAction`, `StepControl`, `PipelineResult`, `PipelineError`, `PipelineJsonLoader`

