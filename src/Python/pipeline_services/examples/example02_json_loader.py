from __future__ import annotations

from pipeline_services.config.json_loader import PipelineJsonLoader
from pipeline_services.core.registry import PipelineRegistry
from pipeline_services.examples.text_steps import normalize_whitespace, strip


def main() -> None:
    registry = PipelineRegistry()
    registry.register_unary("strip", strip)
    registry.register_unary("normalize_whitespace", normalize_whitespace)

    json_text = """
{
  "pipeline": "example02_json_loader",
  "type": "unary",
  "shortCircuitOnException": true,
  "steps": [
    {"$local": "strip"},
    {"$local": "normalize_whitespace"}
  ]
}
"""

    loader = PipelineJsonLoader()
    pipeline = loader.load_str(json_text, registry)
    output_value = pipeline.run("  Hello   JSON  ")
    print(output_value)


if __name__ == "__main__":
    main()

