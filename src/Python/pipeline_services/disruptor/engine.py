from __future__ import annotations

from dataclasses import dataclass
from typing import Any


@dataclass
class DisruptorEngine:
    name: str

    def publish(self, value: Any) -> None:
        raise NotImplementedError("DisruptorEngine is not implemented in this Python port yet")

