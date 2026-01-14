from __future__ import annotations

import json
import time
import urllib.request
from dataclasses import dataclass
from typing import Any, Dict, Optional


@dataclass
class RemoteSpec:
    endpoint: str
    timeout_millis: int = 1000
    retries: int = 0
    method: str = "POST"
    headers: Optional[Dict[str, str]] = None


@dataclass
class RemoteDefaults:
    base_url: str = ""
    timeout_millis: int = 1000
    retries: int = 0
    method: str = "POST"
    headers: Optional[Dict[str, str]] = None

    def resolve_endpoint(self, endpoint_or_path: str) -> str:
        if endpoint_or_path.startswith("http://") or endpoint_or_path.startswith("https://"):
            return endpoint_or_path
        if self.base_url == "":
            return endpoint_or_path
        if self.base_url.endswith("/") and endpoint_or_path.startswith("/"):
            return self.base_url + endpoint_or_path[1:]
        if (not self.base_url.endswith("/")) and (not endpoint_or_path.startswith("/")):
            return self.base_url + "/" + endpoint_or_path
        return self.base_url + endpoint_or_path

    def to_spec(self, endpoint_or_path: str) -> RemoteSpec:
        resolved_endpoint = self.resolve_endpoint(endpoint_or_path)
        spec = RemoteSpec(resolved_endpoint)
        spec.timeout_millis = self.timeout_millis
        spec.retries = self.retries
        spec.method = self.method
        spec.headers = self.headers
        return spec


def http_step(spec: RemoteSpec, input_value: Any) -> str:
    headers_value = spec.headers
    if headers_value is None:
        headers_value = {}

    json_body = json.dumps(input_value)
    last_error_message = ""

    attempt_index = 0
    while attempt_index < (spec.retries + 1):
        try:
            timeout_seconds = float(spec.timeout_millis) / 1000.0
            method_value = str(spec.method)

            if spec.method == "GET":
                request_object = urllib.request.Request(
                    spec.endpoint,
                    method=method_value,
                    headers=headers_value,
                )
                with urllib.request.urlopen(request_object, timeout=timeout_seconds) as response_object:
                    response_body = response_object.read().decode("utf-8")
                return str(response_body)

            content_headers: Dict[str, str] = {"Content-Type": "application/json"}
            content_headers.update(headers_value)
            request_object = urllib.request.Request(
                spec.endpoint,
                data=json_body.encode("utf-8"),
                method=method_value,
                headers=content_headers,
            )
            with urllib.request.urlopen(request_object, timeout=timeout_seconds) as response_object:
                response_body = response_object.read().decode("utf-8")
            return str(response_body)
        except Exception as caught_error:
            last_error_message = str(caught_error)
            if attempt_index < spec.retries:
                time.sleep(0.05 * float(attempt_index + 1))
            attempt_index += 1

    raise RuntimeError(last_error_message)

