from std.python import Python, PythonObject

from ..core.pipeline import Action


struct RemoteSpec(ImplicitlyCopyable):
    var endpoint: String
    var timeout_millis: Int
    var retries: Int
    var method: String
    var headers: PythonObject

    def __init__(out self, endpoint: String):
        self.endpoint = endpoint
        self.timeout_millis = 1000
        self.retries = 0
        self.method = "POST"
        self.headers = PythonObject(None)

    def to_python(self) raises -> PythonObject:
        var builtins = Python.import_module("builtins")
        var value = builtins.dict()
        value["endpoint"] = self.endpoint
        value["timeout_millis"] = self.timeout_millis
        value["retries"] = self.retries
        value["method"] = self.method
        if self.headers is None:
            value["headers"] = builtins.dict()
        else:
            value["headers"] = self.headers
        return value


struct RemoteDefaults(ImplicitlyCopyable):
    var base_url: String
    var timeout_millis: Int
    var retries: Int
    var method: String
    var headers: PythonObject

    def __init__(out self):
        self.base_url = ""
        self.timeout_millis = 1000
        self.retries = 0
        self.method = "POST"
        self.headers = PythonObject(None)

    def resolve_endpoint(self, endpoint_or_path: String) -> String:
        if endpoint_or_path.startswith("http://") or endpoint_or_path.startswith("https://") or endpoint_or_path.startswith("data:"):
            return endpoint_or_path
        if self.base_url == "":
            return endpoint_or_path
        if self.base_url.endswith("/") and endpoint_or_path.startswith("/"):
            return self.base_url + endpoint_or_path[codepoint=1:]
        if not self.base_url.endswith("/") and not endpoint_or_path.startswith("/"):
            return self.base_url + "/" + endpoint_or_path
        return self.base_url + endpoint_or_path

    def to_spec(self, endpoint_or_path: String) -> RemoteSpec:
        var spec = RemoteSpec(self.resolve_endpoint(endpoint_or_path))
        spec.timeout_millis = self.timeout_millis
        spec.retries = self.retries
        spec.method = self.method
        spec.headers = self.headers
        return spec


def _remote_api() raises -> PythonObject:
    var module = Python.evaluate(
        """
import builtins
import json
import time
import types
import urllib.parse
import urllib.request

if not hasattr(builtins, "_pipeline_services_remote_api"):
    def make_action(spec):
        spec = dict(spec)

        def action(value):
            method = str(spec.get("method", "POST")).upper()
            endpoint = str(spec["endpoint"])
            headers = dict(spec.get("headers") or {})
            retries = int(spec.get("retries", 0))
            timeout_seconds = float(spec.get("timeout_millis", 1000)) / 1000.0
            body_text = json.dumps(value)
            last_error = None

            for attempt_index in range(retries + 1):
                try:
                    if method == "GET":
                        request = urllib.request.Request(
                            endpoint,
                            method="GET",
                            headers=headers,
                        )
                    else:
                        request_headers = dict(headers)
                        request_headers.setdefault("Content-Type", "application/json")
                        request = urllib.request.Request(
                            endpoint,
                            data=body_text.encode("utf-8"),
                            method=method,
                            headers=request_headers,
                        )
                    with urllib.request.urlopen(request, timeout=timeout_seconds) as response:
                        return response.read().decode("utf-8")
                except Exception as error:
                    last_error = error
                    if attempt_index < retries:
                        time.sleep(0.05 * (attempt_index + 1))

            raise last_error or RuntimeError("Unknown remote Action failure")

        return action

    builtins._pipeline_services_remote_api = types.SimpleNamespace(
        make_action=make_action,
    )

api = builtins._pipeline_services_remote_api
""",
        file=True,
        name="_pipeline_services_remote_loader",
    )
    return module.api


def remote_action(spec: RemoteSpec) raises -> Action:
    return Action(_remote_api().make_action(spec.to_python()))


def http_step(
    spec: RemoteSpec,
    input_value: PythonObject,
) raises -> PythonObject:
    return remote_action(spec).call(input_value)
