from __future__ import annotations

from pipeline_services.config.json_loader import PipelineJsonLoader
from pipeline_services.core.registry import PipelineRegistry


def main() -> None:
    json_text = """
{
  "pipeline": "example04_json_loader_remote_get",
  "type": "unary",
  "steps": [
    {
      "name": "remote_get_fixture",
      "$remote": {
        "endpoint": "http://127.0.0.1:8765/remote_hello.txt",
        "method": "GET",
        "timeoutMillis": 1000,
        "retries": 0
      }
    }
  ]
}
"""

    registry = PipelineRegistry()
    loader = PipelineJsonLoader()
    pipeline = loader.load_str(json_text, registry)
    result = pipeline.run("ignored")
    print(result.context)


if __name__ == "__main__":
    main()
