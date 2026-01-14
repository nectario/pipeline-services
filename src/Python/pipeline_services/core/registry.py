from __future__ import annotations

from dataclasses import dataclass, field
from typing import Dict

from .pipeline import StepAction, UnaryOperator


@dataclass
class PipelineRegistry:
    unary_actions: Dict[str, UnaryOperator] = field(default_factory=dict)
    step_actions: Dict[str, StepAction] = field(default_factory=dict)

    def register_unary(self, name: str, action: UnaryOperator) -> None:
        self.unary_actions[name] = action

    def register_action(self, name: str, action: StepAction) -> None:
        self.step_actions[name] = action

    def has_unary(self, name: str) -> bool:
        return name in self.unary_actions

    def has_action(self, name: str) -> bool:
        return name in self.step_actions

    def get_unary(self, name: str) -> UnaryOperator:
        if name in self.unary_actions:
            return self.unary_actions[name]
        raise ValueError("Unknown unary action: " + name)

    def get_action(self, name: str) -> StepAction:
        if name in self.step_actions:
            return self.step_actions[name]
        raise ValueError("Unknown step action: " + name)

