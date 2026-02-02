from __future__ import annotations

from dataclasses import dataclass
from typing import Any, List, Union

from .pipeline import (
    Pipeline,
    RegisteredAction,
    ActionControl,
    StepAction,
    UnaryOperator,
    format_step_name,
    safe_error_to_string,
    to_registered_action,
)
from ..remote.http_step import RemoteSpec, http_step


@dataclass
class RuntimePipeline:
    name: str
    short_circuit_on_exception: bool = True
    current: Any = None

    def __post_init__(self) -> None:
        self.ended = False
        self.pre_actions: List[RegisteredAction] = []
        self.actions: List[RegisteredAction] = []
        self.post_actions: List[RegisteredAction] = []
        self.pre_index = 0
        self.action_index = 0
        self.post_index = 0
        self.control = ActionControl(self.name)

    def value(self) -> Any:
        return self.current

    def reset(self, value: Any) -> None:
        self.current = value
        self.ended = False
        self.control.reset()

    def add_pre_action(self, action: Union[UnaryOperator, StepAction, RemoteSpec]) -> Any:
        if self.ended:
            return self.current
        registered_action = to_registered_action("", action)
        self.pre_actions.append(registered_action)
        output_value = self.apply_action(registered_action, "pre", self.pre_index)
        self.pre_index += 1
        return output_value

    def add_action(self, action: Union[UnaryOperator, StepAction, RemoteSpec]) -> Any:
        if self.ended:
            return self.current
        registered_action = to_registered_action("", action)
        self.actions.append(registered_action)
        output_value = self.apply_action(registered_action, "main", self.action_index)
        self.action_index += 1
        return output_value

    def add_post_action(self, action: Union[UnaryOperator, StepAction, RemoteSpec]) -> Any:
        if self.ended:
            return self.current
        registered_action = to_registered_action("", action)
        self.post_actions.append(registered_action)
        output_value = self.apply_action(registered_action, "post", self.post_index)
        self.post_index += 1
        return output_value

    def freeze(self) -> Pipeline:
        return self.to_immutable()

    def to_immutable(self) -> Pipeline:
        pipeline = Pipeline(self.name, self.short_circuit_on_exception)

        for registered_action in self.pre_actions:
            if registered_action.kind == 0:
                pipeline.add_pre_action(registered_action.unary)
            elif registered_action.kind == 1:
                pipeline.add_pre_action(registered_action.step_action)
            else:
                pipeline.add_pre_action(registered_action.remote_spec)

        for registered_action in self.actions:
            if registered_action.kind == 0:
                pipeline.add_action(registered_action.unary)
            elif registered_action.kind == 1:
                pipeline.add_action(registered_action.step_action)
            else:
                pipeline.add_action(registered_action.remote_spec)

        for registered_action in self.post_actions:
            if registered_action.kind == 0:
                pipeline.add_post_action(registered_action.unary)
            elif registered_action.kind == 1:
                pipeline.add_post_action(registered_action.step_action)
            else:
                pipeline.add_post_action(registered_action.remote_spec)

        return pipeline

    def apply_action(self, registered_action: RegisteredAction, phase: str, index: int) -> Any:
        if self.ended:
            return self.current

        action_name = format_step_name(phase, index, registered_action.name)
        self.control.begin_step(phase, index, action_name)
        try:
            if registered_action.kind == 0:
                if registered_action.unary is None:
                    raise RuntimeError("Registered unary action is missing")
                self.current = registered_action.unary(self.current)
            elif registered_action.kind == 1:
                if registered_action.step_action is None:
                    raise RuntimeError("Registered step action is missing")
                self.current = registered_action.step_action(self.current, self.control)
            else:
                if registered_action.remote_spec is None:
                    raise RuntimeError("Registered remote spec is missing")
                self.current = http_step(registered_action.remote_spec, self.current)
        except Exception as caught_error:
            self.current = self.control.record_error(self.current, safe_error_to_string(caught_error))
            if self.short_circuit_on_exception:
                self.control.short_circuit()
                self.ended = True

        if self.control.is_short_circuited():
            self.ended = True

        return self.current
