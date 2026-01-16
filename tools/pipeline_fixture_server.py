from __future__ import annotations

import argparse
import json
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from typing import Any, Tuple


def decode_text(body_bytes: bytes) -> str:
    try:
        return body_bytes.decode("utf-8")
    except UnicodeDecodeError:
        return body_bytes.decode("utf-8", errors="replace")


def parse_json_or_text(body_text: str) -> Tuple[bool, Any]:
    try:
        value = json.loads(body_text)
        return True, value
    except json.JSONDecodeError:
        return False, body_text


def render_echo_response(parsed_is_json: bool, parsed_value: Any, original_text: str) -> str:
    if not parsed_is_json:
        return original_text

    if isinstance(parsed_value, str):
        return parsed_value

    if parsed_value is None:
        return "null"

    if isinstance(parsed_value, (int, float, bool)):
        return str(parsed_value)

    return json.dumps(parsed_value, sort_keys=True)


class FixtureHandler(BaseHTTPRequestHandler):
    def log_message(self, format_string: str, *args: Any) -> None:  # noqa: A002
        return

    def do_GET(self) -> None:  # noqa: N802
        if self.path.startswith("/health"):
            self.send_response(200)
            self.send_header("Content-Type", "text/plain; charset=utf-8")
            self.end_headers()
            self.wfile.write(b"ok")
            return

        self.send_response(404)
        self.send_header("Content-Type", "text/plain; charset=utf-8")
        self.end_headers()
        self.wfile.write(b"not found")

    def do_POST(self) -> None:  # noqa: N802
        if not self.path.startswith("/echo"):
            self.send_response(404)
            self.send_header("Content-Type", "text/plain; charset=utf-8")
            self.end_headers()
            self.wfile.write(b"not found")
            return

        content_length_header = self.headers.get("Content-Length", "0")
        try:
            content_length_value = int(content_length_header)
        except ValueError:
            content_length_value = 0

        body_bytes = self.rfile.read(content_length_value) if content_length_value > 0 else b""
        body_text = decode_text(body_bytes)

        parsed_is_json, parsed_value = parse_json_or_text(body_text)
        response_text = render_echo_response(parsed_is_json, parsed_value, body_text)
        response_bytes = response_text.encode("utf-8")

        self.send_response(200)
        self.send_header("Content-Type", "text/plain; charset=utf-8")
        self.send_header("Content-Length", str(len(response_bytes)))
        self.end_headers()
        self.wfile.write(response_bytes)


def main() -> None:
    parser = argparse.ArgumentParser(description="Pipeline Services fixture server")
    parser.add_argument("--host", default="127.0.0.1")
    parser.add_argument("--port", type=int, default=8765)
    args = parser.parse_args()

    server = ThreadingHTTPServer((args.host, args.port), FixtureHandler)
    try:
        server.serve_forever()
    finally:
        server.server_close()


if __name__ == "__main__":
    main()

