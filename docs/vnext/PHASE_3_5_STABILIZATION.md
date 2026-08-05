# Phase 3.5 — Polyglot Stabilization

Phase 3.5 is the final runtime stabilization pass before extension consolidation in Phase 4. It does not redesign the Pipeline kernel. It closes verification and parity gaps found during the final Pro review of the merged Phase 3 work.

## Stabilized contract

```text
Pipeline executes.
PipelineProvider supplies.
PipelineRouter routes.
preActions → actions → postActions
```

Every mature port now provides one plan and one runner, `run()` returning the final context, detailed execution returning diagnostics, run-local short-circuit state, eager fixed-size POOLED providers with round-robin selection, and canonical JSON phase names.

## Corrections

### C# canonical configuration

The C# loader now prefers `preActions`, `actions`, and `postActions`, with `pre`, `steps`, and `post` accepted only as migration aliases. Prompt detection uses the same section resolution. Regression tests cover canonical precedence and `$prompt` directives in every phase.

### C++ vNext implementation

The C++ port now uses the actual vNext kernel rather than the preview control-aware runner:

- ordinary `Context -> Context` Actions;
- execution-scoped `shortCircuit()`;
- immutable plans;
- `run()` and `runDetailed()` through one runner;
- post-action guarantees and nested/thread-local run isolation;
- eager NEW_INSTANCE_PER_EVENT, SINGLETON, and round-robin POOLED providers;
- a separate router;
- canonical JSON, remote, and generated Actions through the same runner.

The vNext conformance executable is compiled and registered with CTest so it cannot silently fall out of CI again.

### Go execution control and assembly safety

Go no longer parses `runtime.Stack()` to derive goroutine IDs and no longer keeps process-global execution maps. An Action that needs control accepts the run-local `PipelineExecution` handle and calls:

```go
execution.ShortCircuit()
```

This is the documented Go syntax adaptation. It preserves the same semantics without relying on unsupported goroutine-local behavior.

Pipeline assembly operations now perform the freeze check and mutation under one lock. CI runs both the normal Go suite and `go test -race ./...`.

### TypeScript asynchronous control lifetime

TypeScript now defines and tests its AsyncLocalStorage rule: descendants share control while the Action’s returned Promise remains unsettled, and lose control authority after settlement.

### Conformance and CI

`spec/conformance/vnext/port-coverage.json` maps shared scenario IDs to native evidence. `tools/check_conformance_coverage.py` validates the map, referenced test files, C++ CTest registration, language jobs, and the Go race-detector step.

The `phase35-gate` job is a stable aggregate check that fails unless every supported port and repository validation job succeeds. Repository administrators can require this single check in branch protection.

## Documented implementation constraints

- Rust currently requires `Context: Clone + 'static` and captures Action panics through `catch_unwind`; `panic = "abort"` is outside that recovery model.
- Mojo remains experimental. Its exact stable toolchain is pinned, and pooled-provider concurrent selection is not yet advertised with the mature-port guarantee.
- C++ currently requires copy-constructible contexts to retain the last successful value after a throwing Action.

## Phase boundary

After Phase 3.5, Phase 4 may consolidate LLM generation, configuration, remote Actions, observability adapters, and legacy APIs around the stabilized kernel. Phase 4 must not add another runner or change the core short-circuit semantics.
