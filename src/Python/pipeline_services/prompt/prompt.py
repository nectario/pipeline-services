from __future__ import annotations

from dataclasses import dataclass
from typing import Any, Callable, Optional


@dataclass
class PromptStep:
    prompt_spec: Any

    def run(self, input_value: Any, adapter: Optional[Callable[[Any, Any], Any]]) -> Any:
        if adapter is None:
            raise ValueError("No prompt adapter provided")
        return adapter(input_value, self.prompt_spec)

