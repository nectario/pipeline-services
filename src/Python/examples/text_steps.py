from __future__ import annotations
from ..core.short_circuit import short_circuit

class TextSteps:
    @staticmethod
    def strip(s: str) -> str:
        return "" if s is None else s.strip()

    @staticmethod
    def normalize_whitespace(s: str) -> str:
        import re
        return re.sub(r"\s+", " ", s)

    @staticmethod
    def disallow_emoji(s: str) -> str:
        import re
        if re.search(r"[\u2600-\u26FF\u2700-\u27BF]", s or ""):
            raise ValueError("Emoji not allowed")
        return s

    @staticmethod
    def truncate_at_280(s: str) -> str:
        if s is not None and len(s) > 280:
            short_circuit(s[:280])
        return s
