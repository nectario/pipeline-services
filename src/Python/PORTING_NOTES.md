# Porting Notes (Java → Python)

This Python package maps the Java modules into subpackages with feature parity.

## Module mapping

| Java module                  | Python package / module                                 |
|-----------------------------|----------------------------------------------------------|
| `pipeline-core`             | `pipeline_services.core` (`pipeline.py`, `jumps.py`, `short_circuit.py`, `metrics.py`, `steps.py`, `registry.py`) |
| `pipeline-api`              | Folded into `pipeline_services.core.Pipeline`/`Pipe`; JSON/typed features live under `pipeline_services.config.json_loader` |
| `pipeline-config`           | `pipeline_services.config.json_loader`                   |
| `pipeline-remote`           | `pipeline_services.remote.http_step`                    |
| `pipeline-prompt`           | `pipeline_services.prompt.prompt` (adapter-based)       |
| `pipeline-disruptor`        | `pipeline_services.disruptor.engine`                    |
| `pipeline-examples`         | `pipeline_services.examples.*`                           |

## Features covered

- **Unary pipeline (`T → T`)** with labeled steps, `before_each`/`after_each`, `max_jumps` guardrail.
- **Typed pipeline (`I → O`)** via `Pipe` (no labels/jumps as in Java).
- **ShortCircuit** semantics (`short_circuit(value)`).
- **Jumps** (`jump_now(label)`, `jump_after(label, delay_ms)`).
- **Runtime pipeline** (`RuntimePipeline`) for REPL/tools.
- **Metrics** interface with `LoggingMetrics` and `NoopMetrics`.
- **JSON loader** with `pre` / `steps` / `post`, `$local`, `$method`, `$prompt`, `$remote`, and `jumpWhen` support; `@this` and `@beanId` targets are supported for `$method`.
- **Registry** (`PipelineRegistry`).
- **Disruptor-like engine** using a worker thread + bounded queue.
- **Examples + tests** ported to Python idioms.

## JSON specifics

- `$local`: `"package.module.ClassWithApply"` → instantiate and call `apply(x)`.
- `$method`: `{"ref":"package.module.Class#method", "target":"@this|@beanId"}`.
- `$remote`: see `pipeline_services.remote.http_step.RemoteSpec`; provide optional `toJsonBean`/`fromJsonBean` bean ids to the loader.
- `$prompt`: requires a bean named `llm_adapter` (callable `(input, spec_dict) -> output`).
- `jumpWhen`: wraps a step with a predicate that, if true, `jump_now(label)` or `jump_after(label, delayMillis)` *before* executing the step.

## Notable differences

- Python does not enforce generic types at runtime; `Pipe` maintains the API but relies on duck typing.
- The Disruptor port uses `queue.Queue` and a single worker thread; for ultra-low-latency, consider `asyncio` or multiprocessing depending on workload.
- Prompt steps are adapter-driven (no codegen). Provide an `llm_adapter` bean or use the builder with `build(adapter=...)`.

## Running tests

```bash
pytest -q
```

## Example: building from JSON

```python
from pipeline_services.config.json_loader import PipelineJsonLoader

spec = {
  "pipeline": "json_clean_text",
  "type": "unary",
  "shortCircuit": False,
  "steps": [
    {"$local": "pipeline_services.examples.adapters_text.TextStripStep"},
    {"$local": "pipeline_services.examples.adapters_text.TextNormalizeStep"}
  ]
}
p = PipelineJsonLoader()._build(spec)
assert p.run("  hello   world  ") == "hello world"
```

If you'd like 1:1 adapters for additional Java examples, I can add them.
