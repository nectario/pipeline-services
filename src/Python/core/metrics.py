from __future__ import annotations
import logging
import time
from typing import Protocol, Optional

class RunScope(Protocol):
    def on_step_start(self, index: int, label: str) -> None: ...
    def on_step_end(self, index: int, label: str, elapsed_nanos: int, success: bool) -> None: ...
    def on_step_error(self, index: int, label: str, error: BaseException) -> None: ...
    def on_jump(self, from_label: str, to_label: str, delay_ms: int) -> None: ...
    def on_pipeline_end(self, success: bool, elapsed_nanos: int, error: Optional[BaseException]) -> None: ...

class Metrics(Protocol):
    def on_pipeline_start(self, pipeline_name: str, run_id: str, start_label: Optional[str]) -> RunScope: ...

class NoopMetrics:
    def on_pipeline_start(self, pipeline_name: str, run_id: str, start_label: Optional[str]) -> RunScope:
        class _Run:
            def on_step_start(self, i, l): pass
            def on_step_end(self, i, l, e, s): pass
            def on_step_error(self, i, l, err): pass
            def on_jump(self, f, t, d): pass
            def on_pipeline_end(self, success, nanos, err): pass
        return _Run()

class LoggingMetrics:
    """Simple out-of-the-box metrics using :mod:`logging`."""
    def __init__(self, logger: Optional[logging.Logger]=None, level: int=logging.INFO):
        self.log = logger or logging.getLogger("pipeline")
        self.level = level

    def on_pipeline_start(self, pipeline_name: str, run_id: str, start_label: Optional[str]) -> RunScope:
        log, level = self.log, self.level
        if log.isEnabledFor(level):
            log.log(level, f"pipeline.start name={pipeline_name} runId={run_id} startLabel={start_label}")

        class _Run:
            def on_step_start(self, index, label):
                if log.isEnabledFor(level):
                    log.log(level, f"step.start name={pipeline_name} runId={run_id} index={index} label={label}")
            def on_step_end(self, index, label, elapsed_nanos, success):
                if log.isEnabledFor(level):
                    log.log(level, f"step.end   name={pipeline_name} runId={run_id} index={index} label={label} durMs={elapsed_nanos/1_000_000:.3f} success={success}")
            def on_step_error(self, index, label, error):
                log.warning(f"step.error name={pipeline_name} runId={run_id} index={index} label={label}", exc_info=error)
            def on_jump(self, from_label, to_label, delay_ms):
                if log.isEnabledFor(level):
                    log.log(level, f"step.jump  name={pipeline_name} runId={run_id} from={from_label} to={to_label} delayMs={delay_ms}")
            def on_pipeline_end(self, success, nanos, error):
                if error is None:
                    if log.isEnabledFor(level):
                        log.log(level, f"pipeline.end name={pipeline_name} runId={run_id} durMs={nanos/1_000_000:.3f} success={success}")
                else:
                    log.warning(f"pipeline.end name={pipeline_name} runId={run_id} durMs={nanos/1_000_000:.3f} success={success}", exc_info=error)
        return _Run()
