from __future__ import annotations

import warnings
from typing import Generic, TypeVar

from .pipeline import Action, Pipeline, PipelineResult, StepAction
from ..remote.http_step import RemoteSpec, http_step

ContextType = TypeVar("ContextType")
RuntimeAction = Action[ContextType] | StepAction[ContextType] | RemoteSpec


def _as_action(action: RuntimeAction[ContextType]) -> Action[ContextType] | StepAction[ContextType]:
    if isinstance(action, RemoteSpec):
        return lambda context: http_step(action, context)
    if not callable(action):
        raise TypeError("Action must be callable or a RemoteSpec")
    return action


class RuntimePipeline(Generic[ContextType]):
    """Deprecated immediate-execution helper backed by the canonical Pipeline runner."""

    def __init__(
        self,
        name: str,
        short_circuit_on_exception: bool = True,
        current: ContextType | None = None,
    ) -> None:
        warnings.warn(
            "RuntimePipeline is deprecated; construct a Pipeline directly.",
            DeprecationWarning,
            stacklevel=2,
        )
        self.name = name
        self.short_circuit_on_exception = short_circuit_on_exception
        self.current = current
        self.ended = False
        self.pre_actions: list[Action[ContextType] | StepAction[ContextType]] = []
        self.actions: list[Action[ContextType] | StepAction[ContextType]] = []
        self.post_actions: list[Action[ContextType] | StepAction[ContextType]] = []
        self.last_result: PipelineResult[ContextType] | None = None

    def value(self) -> ContextType | None:
        return self.current

    def reset(self, value: ContextType) -> None:
        self.current = value
        self.ended = False
        self.last_result = None

    def clear_recorded(self) -> None:
        self.pre_actions.clear()
        self.actions.clear()
        self.post_actions.clear()

    def recorded_pre_action_count(self) -> int:
        return len(self.pre_actions)

    def recorded_action_count(self) -> int:
        return len(self.actions)

    def recorded_post_action_count(self) -> int:
        return len(self.post_actions)

    def add_pre_action(self, action: RuntimeAction[ContextType]) -> ContextType | None:
        return self._add_and_execute("preActions", self.pre_actions, action)

    def add_action(self, action: RuntimeAction[ContextType]) -> ContextType | None:
        return self._add_and_execute("actions", self.actions, action)

    def add_post_action(self, action: RuntimeAction[ContextType]) -> ContextType | None:
        return self._add_and_execute("postActions", self.post_actions, action)

    def freeze(self) -> Pipeline[ContextType]:
        return self.to_immutable()

    def to_immutable(self) -> Pipeline[ContextType]:
        pipeline = Pipeline[ContextType](self.name, self.short_circuit_on_exception)
        for action in self.pre_actions:
            pipeline.add_pre_action(action)
        for action in self.actions:
            pipeline.add_action(action)
        for action in self.post_actions:
            pipeline.add_post_action(action)
        return pipeline.freeze()

    def _add_and_execute(
        self,
        phase: str,
        destination: list[Action[ContextType] | StepAction[ContextType]],
        action: RuntimeAction[ContextType],
    ) -> ContextType | None:
        if self.ended:
            return self.current
        if self.current is None:
            raise ValueError("RuntimePipeline requires a non-None current value")

        normalized = _as_action(action)
        destination.append(normalized)

        pipeline = Pipeline[ContextType](
            f"{self.name}:runtime",
            self.short_circuit_on_exception,
        )
        if phase == "preActions":
            pipeline.add_pre_action(normalized)
        elif phase == "postActions":
            pipeline.add_post_action(normalized)
        else:
            pipeline.add_action(normalized)

        result = pipeline.run_detailed(self.current)
        self.current = result.context
        self.last_result = result
        self.ended = result.short_circuited
        return self.current
