from __future__ import annotations

import os
import queue
import threading
from dataclasses import dataclass
from enum import Enum
from typing import Callable, Generic, Optional, TypeVar, Union

from .pipeline import Pipeline, PipelineResult

ContextType = TypeVar("ContextType")


class PipelineProviderMode(Enum):
    SHARED = "shared"
    POOLED = "pooled"
    PER_RUN = "perRun"


def default_pool_max() -> int:
    processor_count = os.cpu_count() or 1
    computed = processor_count * 8
    return min(256, max(1, computed))


@dataclass
class PipelinePool(Generic[ContextType]):
    max_size: int
    factory: Callable[[], Pipeline[ContextType]]

    def __post_init__(self) -> None:
        if self.max_size < 1:
            raise ValueError("max_size must be >= 1")
        self.available: queue.Queue[Pipeline[ContextType]] = queue.Queue(maxsize=self.max_size)
        self.created_count = 0
        self.lock = threading.Lock()

    def borrow(self) -> Pipeline[ContextType]:
        try:
            return self.available.get_nowait()
        except queue.Empty:
            with self.lock:
                if self.created_count < self.max_size:
                    self.created_count += 1
                    pipeline = self.factory()
                    if pipeline is None:
                        raise ValueError("factory returned None")
                    return pipeline
            return self.available.get()

    def release(self, pipeline: Pipeline[ContextType]) -> None:
        if pipeline is None:
            return
        try:
            self.available.put_nowait(pipeline)
        except queue.Full:
            return


class PipelineProvider(Generic[ContextType]):
    def __init__(
        self,
        mode: PipelineProviderMode,
        shared_pipeline: Optional[Pipeline[ContextType]] = None,
        pipeline_pool: Optional[PipelinePool[ContextType]] = None,
        pipeline_factory: Optional[Callable[[], Pipeline[ContextType]]] = None,
    ):
        self.mode = mode
        self.shared_pipeline = shared_pipeline
        self.pipeline_pool = pipeline_pool
        self.pipeline_factory = pipeline_factory

    @classmethod
    def shared(cls, pipeline_or_factory: Union[Pipeline[ContextType], Callable[[], Pipeline[ContextType]]]) -> PipelineProvider[ContextType]:
        pipeline: Optional[Pipeline[ContextType]] = None
        if callable(pipeline_or_factory):
            pipeline = pipeline_or_factory()
        else:
            pipeline = pipeline_or_factory
        if pipeline is None:
            raise ValueError("pipeline must not be None")
        return cls(PipelineProviderMode.SHARED, shared_pipeline=pipeline)

    @classmethod
    def pooled(
        cls,
        factory: Callable[[], Pipeline[ContextType]],
        pool_max: Optional[int] = None,
    ) -> PipelineProvider[ContextType]:
        max_size = pool_max if pool_max is not None else default_pool_max()
        pipeline_pool = PipelinePool[ContextType](max_size=max_size, factory=factory)
        return cls(PipelineProviderMode.POOLED, pipeline_pool=pipeline_pool)

    @classmethod
    def per_run(cls, factory: Callable[[], Pipeline[ContextType]]) -> PipelineProvider[ContextType]:
        return cls(PipelineProviderMode.PER_RUN, pipeline_factory=factory)

    def run(self, input_value: ContextType) -> PipelineResult:
        if self.mode == PipelineProviderMode.SHARED:
            if self.shared_pipeline is None:
                raise ValueError("shared_pipeline is not set")
            return self.shared_pipeline.run(input_value)

        if self.mode == PipelineProviderMode.POOLED:
            if self.pipeline_pool is None:
                raise ValueError("pipeline_pool is not set")
            borrowed_pipeline = self.pipeline_pool.borrow()
            try:
                return borrowed_pipeline.run(input_value)
            finally:
                self.pipeline_pool.release(borrowed_pipeline)

        if self.pipeline_factory is None:
            raise ValueError("pipeline_factory is not set")
        pipeline = self.pipeline_factory()
        if pipeline is None:
            raise ValueError("pipeline_factory returned None")
        return pipeline.run(input_value)
