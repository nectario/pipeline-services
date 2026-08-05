from __future__ import annotations

import os
import threading
import warnings
from enum import Enum
from typing import Callable, Generic, Optional, TypeVar

from .pipeline import Pipeline, PipelineResult

ContextType = TypeVar("ContextType")
PipelineFactory = Callable[[], Pipeline[ContextType]]


class PipelineProviderMode(Enum):
    NEW_INSTANCE_PER_EVENT = "newInstancePerEvent"
    SINGLETON = "singleton"
    POOLED = "pooled"


def default_instance_count() -> int:
    return max(1, os.cpu_count() or 1)


def default_pool_max() -> int:
    """Deprecated preview alias for the default retained instance count."""
    return default_instance_count()


class PipelineProvider(Generic[ContextType]):
    def __init__(
        self,
        mode: PipelineProviderMode,
        *,
        pipeline_factory: Optional[PipelineFactory[ContextType]] = None,
        singleton_pipeline: Optional[Pipeline[ContextType]] = None,
        pooled_pipelines: tuple[Pipeline[ContextType], ...] = (),
    ) -> None:
        self._mode = mode
        self._pipeline_factory = pipeline_factory
        self._singleton_pipeline = singleton_pipeline
        self._pooled_pipelines = pooled_pipelines
        self._selection_lock = threading.Lock()
        self._next_selection = 0

    @classmethod
    def new_instance_per_event(
        cls,
        factory: PipelineFactory[ContextType],
    ) -> "PipelineProvider[ContextType]":
        if factory is None:
            raise ValueError("factory must not be None")
        return cls(
            PipelineProviderMode.NEW_INSTANCE_PER_EVENT,
            pipeline_factory=factory,
        )

    @classmethod
    def singleton(
        cls,
        pipeline_or_factory: Pipeline[ContextType] | PipelineFactory[ContextType],
    ) -> "PipelineProvider[ContextType]":
        pipeline = (
            pipeline_or_factory()
            if callable(pipeline_or_factory) and not isinstance(pipeline_or_factory, Pipeline)
            else pipeline_or_factory
        )
        if pipeline is None or not isinstance(pipeline, Pipeline):
            raise ValueError("pipeline must not be None")
        return cls(
            PipelineProviderMode.SINGLETON,
            singleton_pipeline=pipeline.freeze(),
        )

    @classmethod
    def pooled(
        cls,
        factory: PipelineFactory[ContextType],
        instance_count: Optional[int] = None,
    ) -> "PipelineProvider[ContextType]":
        if factory is None:
            raise ValueError("factory must not be None")
        count = default_instance_count() if instance_count is None else instance_count
        if count < 1:
            raise ValueError("instance_count must be >= 1")

        pipelines: list[Pipeline[ContextType]] = []
        for _ in range(count):
            pipeline = factory()
            if pipeline is None:
                raise ValueError("factory returned None")
            pipelines.append(pipeline.freeze())

        return cls(
            PipelineProviderMode.POOLED,
            pooled_pipelines=tuple(pipelines),
        )

    @classmethod
    def shared(
        cls,
        pipeline_or_factory: Pipeline[ContextType] | PipelineFactory[ContextType],
    ) -> "PipelineProvider[ContextType]":
        warnings.warn(
            "shared() is deprecated; use singleton().",
            DeprecationWarning,
            stacklevel=2,
        )
        return cls.singleton(pipeline_or_factory)

    @classmethod
    def per_run(
        cls,
        factory: PipelineFactory[ContextType],
    ) -> "PipelineProvider[ContextType]":
        warnings.warn(
            "per_run() is deprecated; use new_instance_per_event().",
            DeprecationWarning,
            stacklevel=2,
        )
        return cls.new_instance_per_event(factory)

    @property
    def mode(self) -> PipelineProviderMode:
        return self._mode

    @property
    def instance_count(self) -> int:
        if self._mode is PipelineProviderMode.NEW_INSTANCE_PER_EVENT:
            return 0
        if self._mode is PipelineProviderMode.SINGLETON:
            return 1
        return len(self._pooled_pipelines)

    def get_pipeline(self) -> Pipeline[ContextType]:
        if self._mode is PipelineProviderMode.NEW_INSTANCE_PER_EVENT:
            if self._pipeline_factory is None:
                raise RuntimeError("pipeline_factory is not set")
            pipeline = self._pipeline_factory()
            if pipeline is None:
                raise ValueError("pipeline_factory returned None")
            return pipeline.freeze()

        if self._mode is PipelineProviderMode.SINGLETON:
            if self._singleton_pipeline is None:
                raise RuntimeError("singleton_pipeline is not set")
            return self._singleton_pipeline

        if not self._pooled_pipelines:
            raise RuntimeError("pooled_pipelines is empty")
        with self._selection_lock:
            selection = self._next_selection
            self._next_selection += 1
        return self._pooled_pipelines[selection % len(self._pooled_pipelines)]

    def run(self, context: ContextType) -> ContextType:
        return self.get_pipeline().run(context)

    def run_detailed(self, context: ContextType) -> PipelineResult[ContextType]:
        return self.get_pipeline().run_detailed(context)
