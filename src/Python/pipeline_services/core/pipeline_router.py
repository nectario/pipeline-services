from __future__ import annotations

from typing import Callable, Generic, TypeVar

from .pipeline_provider import PipelineProvider

EventType = TypeVar("EventType")
ContextType = TypeVar("ContextType")


class PipelineRouter(Generic[EventType, ContextType]):
    """Selects a PipelineProvider for an event without changing provider lifecycle."""

    def __init__(
        self,
        route: Callable[[EventType], PipelineProvider[ContextType]],
    ) -> None:
        if not callable(route):
            raise TypeError("route must be callable")
        self._route = route

    def get_pipeline_provider(
        self,
        event: EventType,
    ) -> PipelineProvider[ContextType]:
        provider = self._route(event)
        if provider is None:
            raise ValueError("route returned None")
        return provider
