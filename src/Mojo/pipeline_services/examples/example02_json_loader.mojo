from std.python import PythonObject

from pipeline_services.config.json_loader import PipelineJsonLoader
from pipeline_services.core.registry import PipelineRegistry
from pipeline_services.examples.text_steps import normalize_whitespace, strip


def main() raises:
    var registry = PipelineRegistry()
    registry.register_action("strip", strip)
    registry.register_action("normalize_whitespace", normalize_whitespace)

    var json_text = """
{
  "pipeline": "example02_json_loader",
  "type": "unary",
  "shortCircuitOnException": true,
  "actions": [
    {"$local": "strip"},
    {"$local": "normalize_whitespace"}
  ]
}
"""

    var pipeline = PipelineJsonLoader().load_str(json_text, registry)
    print(pipeline.run(PythonObject("  Hello   JSON  ")))
