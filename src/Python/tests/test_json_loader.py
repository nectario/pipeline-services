import unittest

from pipeline_services.config.json_loader import PipelineJsonLoader
from pipeline_services.core.registry import PipelineRegistry
from pipeline_services.examples.text_steps import normalize_whitespace, strip


class JsonLoaderTests(unittest.TestCase):
    def test_load_steps_alias_actions(self) -> None:
        registry = PipelineRegistry()
        registry.register_unary("strip", strip)
        registry.register_unary("normalize_whitespace", normalize_whitespace)

        json_text = """
{
  "pipeline": "t",
  "type": "unary",
  "actions": [
    {"$local": "strip"},
    {"$local": "normalize_whitespace"}
  ]
}
"""
        loader = PipelineJsonLoader()
        pipeline = loader.load_str(json_text, registry)
        output_value = pipeline.run("  Hello   JSON  ")
        self.assertEqual(output_value, "Hello JSON")


if __name__ == "__main__":
    unittest.main()

