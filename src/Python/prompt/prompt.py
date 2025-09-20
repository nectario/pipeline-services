from __future__ import annotations
from dataclasses import dataclass, field
from typing import Any, Callable, Generic, TypeVar, List, Optional

I = TypeVar('I'); O = TypeVar('O')

@dataclass
class PromptSpec(Generic[I, O]):
    name: str = 'promptStep'
    goal: str = ''
    rules: List[str] = field(default_factory=list)
    examples: List[tuple[I, O]] = field(default_factory=list)
    p50_micros: int = 0

class Prompt:
    @staticmethod
    def step(_in: type, _out: type):
        return PromptBuilder()

class PromptBuilder:
    def __init__(self):
        self._spec = PromptSpec()

    def name(self, name: str) -> 'PromptBuilder':
        self._spec.name = name; return self
    def goal(self, goal: str) -> 'PromptBuilder':
        self._spec.goal = goal; return self
    def rule(self, rule: str) -> 'PromptBuilder':
        self._spec.rules.append(rule); return self
    def example(self, _in, _out) -> 'PromptBuilder':
        self._spec.examples.append((_in, _out)); return self
    def p50_micros(self, micros: int) -> 'PromptBuilder':
        self._spec.p50_micros = int(micros); return self

    def build(self, *, adapter: Callable[[Any, dict], Any] | None = None):
        spec_dict = {
            'name': self._spec.name,
            'goal': self._spec.goal,
            'rules': list(self._spec.rules),
            'examples': [{'in': i, 'out': o} for i, o in self._spec.examples],
            'p50Micros': self._spec.p50_micros,
        }
        if adapter is None:
            def _raise(_): raise NotImplementedError("Prompt-generated code not available; provide an adapter to 'build()'")
            return _raise
        def _call(x, _adapter=adapter, _spec=spec_dict):
            return _adapter(x, _spec)
        return _call
