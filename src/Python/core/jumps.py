from __future__ import annotations

class JumpSignal(Exception):
    def __init__(self, label: str, delay_ms: int = 0):
        if not label or not str(label).strip():
            raise ValueError("label must be non-empty")
        super().__init__(f"jump to '{label}' after {int(delay_ms)}ms")
        self.label = str(label)
        self.delay_ms = max(0, int(delay_ms))

def jump_now(label: str) -> None:
    raise JumpSignal(label, 0)

def jump_after(label: str, delay_ms: int) -> None:
    raise JumpSignal(label, delay_ms)
