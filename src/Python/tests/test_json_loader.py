import json
from pipeline_services.config.json_loader import PipelineJsonLoader
from pipeline_services.examples.finance_steps import FinanceSteps, Tick

def test_json_unary_with_sections():
    spec = {
        "pipeline": "json_clean_text",
        "type": "unary",
        "shortCircuit": False,
        "pre": [{"$local": "pipeline_services.examples.adapters_text.TextStripStep"}],
        "steps": [
            {"$local": "pipeline_services.examples.adapters_text.TextNormalizeStep"}
        ],
        "post": [{"$local": "pipeline_services.examples.adapters_text.TextStripStep"}]
    }
    p = PipelineJsonLoader()._build(spec)
    assert p.run("  hi   there  ") == "hi there"

class TextStripStep:
    def apply(self, s: str) -> str:
        return s.strip()

class TextNormalizeStep:
    def apply(self, s: str) -> str:
        import re
        return re.sub(r"\s+", " ", s)
