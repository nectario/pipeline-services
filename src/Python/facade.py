from typing import Optional, Any
from .core.pipeline import Pipeline, Pipe, RuntimePipeline
from .core.metrics import Metrics, LoggingMetrics, NoopMetrics
try:
    from .config.json_loader import PipelineJsonLoader  # optional, if present
except Exception:
    PipelineJsonLoader = None  # type: ignore
try:
    from .remote.http_step import RemoteSpec, http_step  # optional
except Exception:
    RemoteSpec = None  # type: ignore
    http_step = None  # type: ignore
try:
    from .prompt.prompt import PromptBuilder  # optional
except Exception:
    PromptBuilder = None  # type: ignore
try:
    from .disruptor.engine import DisruptorEngine  # optional
except Exception:
    DisruptorEngine = None  # type: ignore
try:
    from .core.registry import PipelineRegistry  # optional
except Exception:
    PipelineRegistry = None  # type: ignore
from .core.steps import ignore_errors, with_fallback  # type: ignore
from .core.jumps import jump_now, jump_after  # type: ignore
from .core.short_circuit import short_circuit  # type: ignore

class PipelineServices:
    @staticmethod
    def pipeline(name: str, *, short_circuit: bool = True, metrics: Optional[Metrics] = None) -> Pipeline:
        return Pipeline(name, short_circuit, metrics)

    @staticmethod
    def pipe(name: str, *, short_circuit: bool = True, metrics: Optional[Metrics] = None) -> Pipe:
        return Pipe(name, short_circuit, metrics)

    @staticmethod
    def runtime_pipeline(name: str, *, short_circuit: bool = True) -> RuntimePipeline:
        return RuntimePipeline(name, short_circuit=short_circuit)

    @staticmethod
    def json_loader(**options: Any):
        if PipelineJsonLoader is None:
            raise RuntimeError("PipelineJsonLoader not available in this build")
        return PipelineJsonLoader(**options)

    @staticmethod
    def logging_metrics(level: str = "info") -> LoggingMetrics:
        return LoggingMetrics(level=level)  # type: ignore

    @staticmethod
    def noop_metrics() -> NoopMetrics:
        return NoopMetrics()  # type: ignore

    @staticmethod
    def remote_spec(**kwargs: Any):
        if RemoteSpec is None:
            raise RuntimeError("RemoteSpec not available in this build")
        return RemoteSpec(**kwargs)  # type: ignore

    @staticmethod
    def http_remote(spec: Any):
        if http_step is None:
            raise RuntimeError("http_step not available in this build")
        return http_step(spec)  # type: ignore

    @staticmethod
    def prompt_builder():
        if PromptBuilder is None:
            raise RuntimeError("PromptBuilder not available in this build")
        return PromptBuilder()  # type: ignore

    @staticmethod
    def engine(name: str, pipeline_callable, capacity: int = 8192):
        if DisruptorEngine is None:
            raise RuntimeError("DisruptorEngine not available in this build")
        return DisruptorEngine(name, pipeline_callable, capacity)  # type: ignore

    @staticmethod
    def registry():
        if PipelineRegistry is None:
            raise RuntimeError("PipelineRegistry not available in this build")
        return PipelineRegistry()  # type: ignore

    # Namespaced helpers
    class steps:
        ignore_errors = staticmethod(ignore_errors)
        with_fallback = staticmethod(with_fallback)

    class jumps:
        jump_now = staticmethod(jump_now)
        jump_after = staticmethod(jump_after)

    class control:
        short_circuit = staticmethod(short_circuit)

PipelineServices = PipelineServices()
