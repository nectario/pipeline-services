# Porting Notes (Java → Mojo)

## Design goals
- Preserve the original API concepts (unary `Pipeline`, typed `Pipe`, runtime pipeline, metrics, JSON config, jumps, short-circuit).
- Follow Mojo style rules: `fn`, typed parameters, `struct`, snake_case for functions/vars, PascalCase for structs, no `let`, readable code.
- Avoid "fancy" one-liners and keep variable names descriptive.

## Key differences driven by Mojo
- **Errors**: Mojo uses a single `Error` type; `fn` doesn't raise by default. We mark functions that may raise with `raises` and use `try/except` where needed. citeturn1view0
- **Control signals**: To keep step signatures as `T -> T`, we emulate Java's exceptions with thread-local flags and then raise a simple error. The pipeline inspects the thread-local signal to perform jumps and short-circuits.
- **Collections**: We use the standard `List` and `Dict` where appropriate. citeturn2search1turn6search2
- **Timing**: We use `perf_counter_ns` and `sleep` from the `time` module. citeturn2search2
- **Python interop**: JSON parsing, dynamic imports, and HTTP calls use Python interop (`json`, `importlib`, `urllib`). citeturn5view0

## Module mapping
- `pipeline-core` → `pipeline_services/core` (`pipeline.mojo`, `jumps.mojo`, `short_circuit.mojo`, `metrics.mojo`, `steps.mojo`, `registry.mojo`)
- `pipeline-config` → `pipeline_services/config/json_loader.mojo`
- `pipeline-remote` → `pipeline_services/remote/http_step.mojo`
- `pipeline-prompt` → `pipeline_services/prompt/prompt.mojo`
- `pipeline-disruptor` → `pipeline_services/disruptor/engine.mojo`
- Examples → `pipeline_services/examples/*`

## Verified docs links
- Mojo changelog: https://docs.modular.com/mojo/changelog/  (opened successfully) citeturn0search12
- Mojo API reference: https://docs.modular.com/mojo/lib/      (opened successfully) citeturn0search15

## Compatibility notes
- The JSON loader preserves `$local`, `$method`, `$prompt`, `$remote`, and `jumpWhen` semantics.
- Typed predicate results in `jumpWhen` are interpreted through Python truthiness; adapt as needed.
- The Disruptor engine is a pragmatic port using Python `queue`/`threading` until Mojo exposes stable native equivalents.
