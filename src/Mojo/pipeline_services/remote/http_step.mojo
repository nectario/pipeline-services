from python import Python
from python import PythonObject

struct RemoteSpec(ImplicitlyCopyable):
    var endpoint: String
    var timeout_millis: Int
    var retries: Int
    var method: String
    var headers: PythonObject

    fn __init__(out self, endpoint: String):
        self.endpoint = endpoint
        self.timeout_millis = 1000
        self.retries = 0
        self.method = "POST"
        self.headers = PythonObject(None)

struct RemoteDefaults(ImplicitlyCopyable):
    var base_url: String
    var timeout_millis: Int
    var retries: Int
    var method: String
    var headers: PythonObject

    fn __init__(out self):
        self.base_url = ""
        self.timeout_millis = 1000
        self.retries = 0
        self.method = "POST"
        self.headers = PythonObject(None)

    fn resolve_endpoint(self, endpoint_or_path: String) -> String:
        if endpoint_or_path.startswith("http://") or endpoint_or_path.startswith("https://"):
            return endpoint_or_path
        if self.base_url == "":
            return endpoint_or_path
        if self.base_url.endswith("/") and endpoint_or_path.startswith("/"):
            return self.base_url + endpoint_or_path[1:]
        if not self.base_url.endswith("/") and not endpoint_or_path.startswith("/"):
            return self.base_url + "/" + endpoint_or_path
        return self.base_url + endpoint_or_path

    fn to_spec(self, endpoint_or_path: String) -> RemoteSpec:
        var resolved_endpoint = self.resolve_endpoint(endpoint_or_path)
        var spec = RemoteSpec(resolved_endpoint)
        spec.timeout_millis = self.timeout_millis
        spec.retries = self.retries
        spec.method = self.method
        spec.headers = self.headers
        return spec

fn http_step(spec: RemoteSpec, input_value: PythonObject) raises -> PythonObject:
    var request_module = Python.import_module("urllib.request")
    var json_module = Python.import_module("json")
    var time_module = Python.import_module("time")
    var builtins_module = Python.import_module("builtins")

    var headers_value = spec.headers
    if headers_value is None:
        headers_value = builtins_module.dict()

    var json_body = json_module.dumps(input_value)
    var last_error_message = ""

    var attempt_index: Int = 0
    while attempt_index < (spec.retries + 1):
        try:
            var timeout_seconds = Float64(spec.timeout_millis) / 1000.0
            var method_value = PythonObject(String(spec.method))

            if spec.method == "GET":
                var url_value = spec.endpoint
                var request_object = request_module.Request(url_value, method = method_value, headers = headers_value)
                var response_object = request_module.urlopen(request_object, timeout = PythonObject(timeout_seconds))
                var response_body = response_object.read().decode("utf-8")
                return PythonObject(String(response_body))

            var content_headers = builtins_module.dict()
            content_headers[PythonObject("Content-Type")] = PythonObject("application/json")
            content_headers.update(headers_value)
            var request_object = request_module.Request(
                spec.endpoint,
                data = json_body.encode("utf-8"),
                method = method_value,
                headers = content_headers,
            )
            var response_object = request_module.urlopen(request_object, timeout = PythonObject(timeout_seconds))
            var response_body = response_object.read().decode("utf-8")
            return PythonObject(String(response_body))
        except caught_error:
            last_error_message = String(caught_error)
            if attempt_index < spec.retries:
                time_module.sleep(0.05 * Float64(attempt_index + 1))
            attempt_index = attempt_index + 1

    raise last_error_message
