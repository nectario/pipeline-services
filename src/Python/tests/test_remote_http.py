import http.server
import pathlib
import socketserver
import threading
import unittest

from pipeline_services.config.json_loader import PipelineJsonLoader
from pipeline_services.core.registry import PipelineRegistry
from pipeline_services.remote.http_step import RemoteSpec, http_step


class FixtureHandler(http.server.SimpleHTTPRequestHandler):
    base_directory = ""

    def __init__(self, *args, **kwargs):
        super().__init__(*args, directory=self.base_directory, **kwargs)

    def log_message(self, format, *args):
        return


class RemoteHttpTests(unittest.TestCase):
    def test_http_step_get(self) -> None:
        fixtures_dir = pathlib.Path(__file__).resolve().parents[1] / "pipeline_services" / "examples" / "fixtures"
        FixtureHandler.base_directory = str(fixtures_dir)

        http_server = socketserver.TCPServer(("127.0.0.1", 0), FixtureHandler)
        port = http_server.server_address[1]
        server_thread = threading.Thread(target=http_server.serve_forever, daemon=True)
        server_thread.start()
        try:
            spec = RemoteSpec(f"http://127.0.0.1:{port}/remote_hello.txt", method="GET")
            response_body = http_step(spec, "ignored")
            self.assertIn("Hello from remote fixture", response_body)
        finally:
            http_server.shutdown()
            http_server.server_close()

    def test_json_loader_remote_get(self) -> None:
        fixtures_dir = pathlib.Path(__file__).resolve().parents[1] / "pipeline_services" / "examples" / "fixtures"
        FixtureHandler.base_directory = str(fixtures_dir)

        http_server = socketserver.TCPServer(("127.0.0.1", 0), FixtureHandler)
        port = http_server.server_address[1]
        server_thread = threading.Thread(target=http_server.serve_forever, daemon=True)
        server_thread.start()
        try:
            json_text = f"""
{{
  "pipeline": "t",
  "type": "unary",
  "steps": [
    {{
      "name": "remote_get_fixture",
      "$remote": {{
        "endpoint": "http://127.0.0.1:{port}/remote_hello.txt",
        "method": "GET"
      }}
    }}
  ]
}}
"""
            registry = PipelineRegistry()
            loader = PipelineJsonLoader()
            pipeline = loader.load_str(json_text, registry)
            output_value = pipeline.run("ignored")
            self.assertIn("Hello from remote fixture", output_value)
        finally:
            http_server.shutdown()
            http_server.server_close()


if __name__ == "__main__":
    unittest.main()

