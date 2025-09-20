from __future__ import annotations
from .text_steps import TextSteps

class TextStripStep:
    def apply(self, s: str) -> str:
        return TextSteps.strip(s)

class TextNormalizeStep:
    def apply(self, s: str) -> str:
        return TextSteps.normalize_whitespace(s)
