from python import PythonObject

from pipeline_services.config.json_loader import PipelineJsonLoader
from pipeline_services.core.registry import PipelineRegistry
from pipeline_services.examples.text_steps import strip, normalize_whitespace

fn main() raises:
    var registry = PipelineRegistry()
    registry.register_unary("strip", strip)
    registry.register_unary("normalize_whitespace", normalize_whitespace)

    var json_text = """
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

    var loader = PipelineJsonLoader()
    var pipeline = loader.load_str(json_text, registry)
    var output_value = pipeline.run(PythonObject("  Hello   JSON  "))
    print(output_value)
