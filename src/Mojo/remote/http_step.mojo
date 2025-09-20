from python import Python, PythonObject

struct RemoteSpec:
    var endpoint: String
    var timeout_millis: Int
    var retries: Int
    var headers: PythonObject
    var method: String
    var to_json: PythonObject
    var from_json: PythonObject

    fn __init__(out self, endpoint: String, timeout_millis: Int, retries: Int, headers: PythonObject, method: String):
        self.endpoint = endpoint
        self.timeout_millis = timeout_millis
        self.retries = retries
        self.headers = headers
        self.method = method
        self.to_json = None
        self.from_json = None

fn http_step(spec: RemoteSpec) -> fn(input_value: PythonObject) raises -> PythonObject:
    var python_runtime = Python
    var request_module = python_runtime.import_module("urllib.request")
    var json_module = python_runtime.import_module("json")
    var parse_module = python_runtime.import_module("urllib.parse")
    var time_module = python_runtime.import_module("time")

    if spec.to_json is None: spec.to_json = json_module.dumps
    if spec.from_json is None: spec.from_json = json_module.loads

    fn call_remote(input_value: PythonObject) raises -> PythonObject:
        var data_str = spec.to_json(input_value)
        var last_error = None
        var attempt_index: Int = 0
        while attempt_index < (spec.retries + 1):
            try:
                if spec.method == "GET":
                    var query: String = ""
                    try:
                        var obj = json_module.loads(data_str)
                        if isinstance(obj, dict):
                            query = parse_module.urlencode(obj, doseq=True)
                        else:
                            query = String(obj)
                    except caught_error:
                        query = String(data_str) if data_str else ""
                    var url = spec.endpoint
                    if query != "":
                        url = url + ("&" if ("?" in url) else "?") + query
                    var req = request_module.Request(url, method="GET", headers=spec.headers)
                    var resp = request_module.urlopen(req, timeout=Float64(spec.timeout_millis)/1000.0)
                    var body = resp.read().decode("utf-8")
                    return spec.from_json(body)
                else:
                    var req = request_module.Request(spec.endpoint, data=data_str.encode("utf-8"), method="POST", headers=Python.dict(**{"Content-Type":"application/json", **spec.headers}))
                    var resp = request_module.urlopen(req, timeout=Float64(spec.timeout_millis)/1000.0)
                    var body = resp.read().decode("utf-8")
                    return spec.from_json(body)
            except caught_error:
                last_error = caught_error
                if attempt_index < spec.retries:
                    time_module.sleep(0.05 * (attempt_index + 1))
                else:
                    raise String(caught_error)
            attempt_index = attempt_index + 1
        if last_error is not None:
            raise String(last_error)
        return input_value
    return call_remote
