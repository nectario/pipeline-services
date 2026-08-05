from std.python import PythonObject

from pipeline_services.config.json_loader import PipelineJsonLoader
from pipeline_services.core.registry import PipelineRegistry


def main() raises:
    var json_text = """
{
  "pipeline": "example04_json_loader_remote_get",
  "type": "unary",
  "actions": [
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

    var registry = PipelineRegistry()
    var pipeline = PipelineJsonLoader().load_str(json_text, registry)
    print(pipeline.run(PythonObject("ignored")))
