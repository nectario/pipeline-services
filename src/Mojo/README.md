# Pipeline Services — Mojo Port

This is a Mojo port of the Pipeline Services framework. It closely mirrors the Java/Python APIs while following Mojo style:
- All functions use `fn` and have typed parameters.
- All user-defined types are `struct` (no classes).
- Snake_case function and variable names; PascalCase struct names.
- No `let` (uses `var`).
- No fancy one-liners.

## Modules
- `pipeline_services/core` — pipelines, metrics, jumps, helpers.
- `pipeline_services/config` — JSON loader for pipelines (`$local`, `$method`, `$prompt`, `$remote`, `jumpWhen`).
- `pipeline_services/remote` — HTTP step (via Python interop `urllib.request`).
- `pipeline_services/prompt` — prompt builder that calls a user-provided `llm_adapter` bean.
- `pipeline_services/disruptor` — simple single-consumer engine using Python `queue`/`threading`.
- `pipeline_services/examples` — text and finance examples.

## Build & Run
- Requires a recent Mojo toolchain and Python available for interop.
- Run examples with the Mojo CLI, e.g.:
  ```
  mojo run pipeline_services/examples/disruptor_stock_alerts_example.mojo
  ```

### Notes on control flow
Mojo errors are untyped (`Error`) and `fn` functions are non-raising by default.
This port uses thread-local flags to implement `short_circuit`, `jump_now`, and `jump_after`:
- Steps call `short_circuit(value)` or `jump_*()` (which raise), and the pipeline catches and interprets these as control signals.
- Values/labels are stored in a Python `threading.local()` so the Disruptor engine remains thread-safe.

See `PORTING_NOTES.md` for more detail.
