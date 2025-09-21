from .core.pipeline import Pipeline, Pipe, RuntimePipeline
from .core.metrics import Metrics, NoopMetrics, LoggingMetrics
from .config.json_loader import PipelineJsonLoader
from .remote.http_step import RemoteSpec, http_step
from .prompt.prompt import PromptBuilder
from .disruptor.engine import DisruptorEngine
from .core.registry import PipelineRegistry
from .core.steps import ignore_errors, with_fallback
from .core.jumps import jump_now, jump_after
from .core.short_circuit import short_circuit

struct PipelineServices:
    fn pipeline(self, name: String, short_circuit: Bool = True, metrics: Metrics = NoopMetrics()) -> Pipeline:
        return Pipeline(name, short_circuit, metrics)

    fn pipe(self, name: String, short_circuit: Bool = True, metrics: Metrics = NoopMetrics()) -> Pipe:
        return Pipe(name, short_circuit, metrics)

    fn runtime_pipeline(self, name: String, short_circuit: Bool = True) -> RuntimePipeline:
        return RuntimePipeline(name, short_circuit)

    fn json_loader(self) -> PipelineJsonLoader:
        return PipelineJsonLoader()

    fn logging_metrics(self) -> LoggingMetrics:
        return LoggingMetrics()

    fn noop_metrics(self) -> NoopMetrics:
        return NoopMetrics()

    fn remote_spec(self, endpoint: String) -> RemoteSpec:
        return RemoteSpec(endpoint)

    fn http_remote(self, spec: RemoteSpec) -> fn(input_value: PythonObject) raises -> PythonObject:
        return http_step(spec)

    fn prompt_builder(self) -> PromptBuilder:
        return PromptBuilder()

    fn engine(self, name: String, capacity: Int = 8192, pipeline_callable: fn(PythonObject) raises -> PythonObject) -> DisruptorEngine:
        return DisruptorEngine(name, pipeline_callable, capacity)
