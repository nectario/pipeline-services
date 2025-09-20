# Pipeline Services — Python Port

A lightweight framework to compose programs as structured, reusable pipelines.
It supports labeled steps, short-circuiting, jump-based control flow, a runtime pipeline,
JSON configuration, pluggable metrics, and optional remote/prompt steps.

## Highlights
- **Unary pipelines** (`T → T`): `Pipeline(name).step(fn, label="...")...`
- **Typed pipelines** (`I → O`): `Pipe(name).step(fn)...`
- **Short-circuiting**: `short_circuit(value)` stops execution and returns `value`.
- **Jumps**: `jump_now(label)` / `jump_after(label, delay_ms)` for polling and control-flow.
- **Metrics**: `LoggingMetrics` and `NoopMetrics` via a minimal interface.
- **JSON loader**: build pipelines from JSON specs (pre/steps/post, `$method`, `$local`, `$prompt`, `$remote`, `jumpWhen`). 
- **Runtime pipeline**: incrementally build and execute pipelines in REPLs/tools.
- **Disruptor-like engine**: queue + worker thread for high-throughput event processing.

## Quick start
```python
from pipeline_services import Pipeline, jump_now, short_circuit

p = Pipeline("clean").step(lambda s: s.strip(), label="strip") \\
                      .step(lambda s: (jump_now("strip"), s)[1], label="loop")
print(p.run("  hi "))
```

### JSON
```json
{
  "pipeline":"json_clean_text",
  "type":"unary",
  "shortCircuit":false,
  "steps":[
    {"$local":"pipeline_services.examples.adapters_text.TextStripStep"},
    {"$local":"pipeline_services.examples.adapters_text.TextNormalizeStep"}
  ]
}
```

```python
from pipeline_services.config.json_loader import PipelineJsonLoader
p = PipelineJsonLoader().load_str(json_spec)
print(p.run("  hello   world  "))  # -> "hello world"
```

### Prompt steps
Provide a callable bean named `llm_adapter` to the JSON loader (or Prompt builder) that accepts `(input, prompt_spec)` and returns an output.

### Remote steps
Use `$remote` with an endpoint and optional (de)serializers referenced by bean id.

## License
MIT (change as you see fit).
