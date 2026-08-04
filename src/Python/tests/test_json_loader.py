import unittest

from pipeline_services.config.json_loader import PipelineJsonLoader
from pipeline_services.core.registry import PipelineRegistry
from pipeline_services.examples.text_steps import normalize_whitespace, strip


class JsonLoaderTests(unittest.TestCase):
    def test_canonical_action_arrays_and_legacy_aliases(self) -> None:
        registry = PipelineRegistry()
        registry.register_unary("strip", strip)
        registry.register_unary("normalize_whitespace", normalize_whitespace)

        json_text = """
{
  "pipeline": "t",
  "type": "unary",
  "preActions": [
    {"$local": "strip"}
  ],
  "actions": [
    {"$local": "normalize_whitespace"}
  ],
  "postActions": []
}
"""
        pipeline = PipelineJsonLoader().load_str(json_text, registry)
        self.assertEqual(pipeline.run("  Hello   JSON  "), "Hello JSON")

        legacy_text = """
{
  "pipeline": "legacy",
  "type": "unary",
  "pre": [{"$local": "strip"}],
  "steps": [{"$local": "normalize_whitespace"}],
  "post": []
}
"""
        legacy = PipelineJsonLoader().load_str(legacy_text, registry)
        self.assertEqual(legacy.run("  Hello   Legacy  "), "Hello Legacy")


if __name__ == "__main__":
    unittest.main()
