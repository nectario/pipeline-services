from std.python import Python, PythonObject

from ..core.pipeline import Action, Pipeline
from ..core.registry import PipelineRegistry
from ..remote.http_step import RemoteDefaults, RemoteSpec, remote_action


struct PipelineJsonLoader(ImplicitlyCopyable):
    def __init__(out self):
        pass

    def load_str(
        self,
        json_text: String,
        registry: PipelineRegistry,
    ) raises -> Pipeline:
        var json_module = Python.import_module("json")
        var spec = json_module.loads(json_text)
        if spec_contains_prompt_actions(spec):
            raise "Pipeline contains $prompt Actions. Run prompt codegen and load the compiled JSON under pipelines/generated/mojo/."
        return self.build_from_spec(spec, registry)

    def load_file(
        self,
        file_path: String,
        registry: PipelineRegistry,
    ) raises -> Pipeline:
        var builtins = Python.import_module("builtins")
        var file_object = builtins.open(
            file_path,
            "r",
            encoding=PythonObject("utf-8"),
        )
        var json_text = String(file_object.read())
        file_object.close()

        var json_module = Python.import_module("json")
        var spec = json_module.loads(json_text)
        var pipeline_name = String(spec.get("pipeline", "pipeline"))
        if spec_contains_prompt_actions(spec):
            var compiled_path = resolve_compiled_pipeline_path(
                file_path,
                pipeline_name,
                "mojo",
            )
            var os_module = Python.import_module("os")
            if not Bool(py=os_module.path.exists(compiled_path)):
                raise "Pipeline contains $prompt Actions but compiled JSON was not found. Run prompt codegen. Expected compiled pipeline at: " + compiled_path
            var compiled_file = builtins.open(
                compiled_path,
                "r",
                encoding=PythonObject("utf-8"),
            )
            var compiled_text = String(compiled_file.read())
            compiled_file.close()
            return self.load_str(compiled_text, registry)

        return self.build_from_spec(spec, registry)

    def build_from_spec(
        self,
        spec: PythonObject,
        registry: PipelineRegistry,
    ) raises -> Pipeline:
        if spec_contains_prompt_actions(spec):
            raise "Pipeline contains $prompt Actions. Run prompt codegen and load the compiled JSON under pipelines/generated/mojo/."

        var pipeline_name = String(spec.get("pipeline", "pipeline"))
        var pipeline_type = String(spec.get("type", "unary"))
        if pipeline_type != "unary":
            raise "Only 'unary' Pipelines are supported by this loader"

        var short_circuit_on_exception = True
        var short_circuit_value = spec.get("shortCircuitOnException")
        if short_circuit_value is None:
            short_circuit_value = spec.get("shortCircuit")
        if short_circuit_value is not None:
            short_circuit_on_exception = Bool(py=short_circuit_value)

        var pipeline = Pipeline(
            pipeline_name,
            short_circuit_on_exception,
        )
        var remote_defaults = RemoteDefaults()
        var remote_defaults_node = spec.get("remoteDefaults")
        if remote_defaults_node is not None:
            remote_defaults = self.parse_remote_defaults(
                remote_defaults_node,
                remote_defaults,
            )

        self.add_first_present_section(
            spec,
            "preActions",
            "pre",
            "preActions",
            pipeline,
            registry,
            remote_defaults,
        )
        self.add_first_present_section(
            spec,
            "actions",
            "steps",
            "actions",
            pipeline,
            registry,
            remote_defaults,
        )
        self.add_first_present_section(
            spec,
            "postActions",
            "post",
            "postActions",
            pipeline,
            registry,
            remote_defaults,
        )
        return pipeline^

    def add_first_present_section(
        self,
        spec: PythonObject,
        canonical_name: String,
        legacy_name: String,
        phase_name: String,
        mut pipeline: Pipeline,
        registry: PipelineRegistry,
        remote_defaults: RemoteDefaults,
    ) raises:
        var nodes = spec.get(canonical_name)
        if nodes is None:
            nodes = spec.get(legacy_name)
        if nodes is None:
            return
        for node in nodes:
            self.add_action_node(
                node,
                phase_name,
                pipeline,
                registry,
                remote_defaults,
            )

    def add_action_node(
        self,
        node: PythonObject,
        phase_name: String,
        mut pipeline: Pipeline,
        registry: PipelineRegistry,
        remote_defaults: RemoteDefaults,
    ) raises:
        if node.get("$prompt") is not None:
            raise "Runtime does not execute $prompt Actions. Run prompt codegen to produce compiled JSON with $local references."

        var display_name = ""
        var display_name_value = node.get("name")
        if display_name_value is None:
            display_name_value = node.get("label")
        if display_name_value is not None:
            display_name = String(display_name_value)

        var local_reference_value = node.get("$local")
        if local_reference_value is not None:
            var action = registry.get_action(String(local_reference_value))
            self.add_resolved_action(
                action,
                display_name,
                phase_name,
                pipeline,
            )
            return

        var remote_node = node.get("$remote")
        if remote_node is not None:
            var spec = self.parse_remote_spec(remote_node, remote_defaults)
            self.add_resolved_action(
                remote_action(spec),
                display_name,
                phase_name,
                pipeline,
            )
            return

        raise "Unsupported Action: expected '$local' or '$remote'"

    def add_resolved_action(
        self,
        action: Action,
        display_name: String,
        phase_name: String,
        mut pipeline: Pipeline,
    ) raises:
        if phase_name == "preActions":
            pipeline.add_pre_action_named(display_name, action)
        elif phase_name == "postActions":
            pipeline.add_post_action_named(display_name, action)
        else:
            pipeline.add_action_named(display_name, action)

    def parse_remote_spec(
        self,
        remote_node: PythonObject,
        remote_defaults: RemoteDefaults,
    ) raises -> RemoteSpec:
        var builtins = Python.import_module("builtins")
        if Bool(py=builtins.isinstance(remote_node, builtins.str)):
            return remote_defaults.to_spec(String(remote_node))

        var endpoint_value = remote_node.get("endpoint")
        if endpoint_value is None:
            endpoint_value = remote_node.get("path")
        if endpoint_value is None:
            raise "Missing required $remote field: endpoint|path"

        var spec = remote_defaults.to_spec(String(endpoint_value))
        var timeout_value = remote_node.get("timeoutMillis")
        if timeout_value is None:
            timeout_value = remote_node.get("timeout_millis")
        if timeout_value is not None:
            spec.timeout_millis = Int(py=timeout_value)

        var retries_value = remote_node.get("retries")
        if retries_value is not None:
            spec.retries = Int(py=retries_value)

        var method_value = remote_node.get("method")
        if method_value is not None:
            spec.method = String(method_value)

        var headers_value = remote_node.get("headers")
        if headers_value is not None:
            var merged_headers = builtins.dict()
            if spec.headers is not None:
                merged_headers.update(spec.headers)
            merged_headers.update(headers_value)
            spec.headers = merged_headers
        return spec

    def parse_remote_defaults(
        self,
        node: PythonObject,
        base: RemoteDefaults,
    ) raises -> RemoteDefaults:
        var defaults = base
        var base_url_value = node.get("baseUrl")
        if base_url_value is None:
            base_url_value = node.get("endpointBase")
        if base_url_value is not None:
            defaults.base_url = String(base_url_value)

        var timeout_value = node.get("timeoutMillis")
        if timeout_value is None:
            timeout_value = node.get("timeout_millis")
        if timeout_value is not None:
            defaults.timeout_millis = Int(py=timeout_value)

        var retries_value = node.get("retries")
        if retries_value is not None:
            defaults.retries = Int(py=retries_value)

        var method_value = node.get("method")
        if method_value is not None:
            defaults.method = String(method_value)

        var headers_value = node.get("headers")
        if headers_value is not None:
            defaults.headers = headers_value
        return defaults


def spec_contains_prompt_actions(spec: PythonObject) raises -> Bool:
    for section_name in [
        "preActions",
        "pre",
        "actions",
        "steps",
        "postActions",
        "post",
    ]:
        var nodes = spec.get(section_name)
        if nodes is None:
            continue
        for node in nodes:
            if node is not None and node.get("$prompt") is not None:
                return True
    return False


def resolve_compiled_pipeline_path(
    source_file_path: String,
    pipeline_name: String,
    language_name: String,
) raises -> String:
    var pathlib = Python.import_module("pathlib")
    var source_path = pathlib.Path(source_file_path).resolve()
    var current_dir = source_path.parent
    while True:
        if String(current_dir.name) == "pipelines":
            return String(
                current_dir /
                "generated" /
                language_name /
                (pipeline_name + ".json")
            )
        if current_dir.parent == current_dir:
            break
        current_dir = current_dir.parent
    raise "Pipeline contains $prompt Actions but the pipelines root directory could not be inferred from: " + String(source_path)
