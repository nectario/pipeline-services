from python import Python
from python import PythonObject

from ..core.pipeline import Pipeline
from ..core.registry import PipelineRegistry
from ..remote.http_step import RemoteDefaults, RemoteSpec

struct PipelineJsonLoader:
    fn __init__(out self):
        pass

    fn load_str(self, json_text: String, mut registry: PipelineRegistry) raises -> Pipeline:
        var json_module = Python.import_module("json")
        var spec = json_module.loads(json_text)
        if spec_contains_prompt_steps(spec):
            raise "Pipeline contains $prompt steps. Run prompt codegen and load the compiled JSON under pipelines/generated/mojo/."
        return self.build_from_spec(spec, registry)

    fn load_file(self, file_path: String, mut registry: PipelineRegistry) raises -> Pipeline:
        var builtins_module = Python.import_module("builtins")
        var file_object = builtins_module.open(file_path, "r", encoding = PythonObject("utf-8"))
        var text_value = file_object.read()
        file_object.close()
        var json_module = Python.import_module("json")
        var spec = json_module.loads(String(text_value))
        var pipeline_name = String(spec.get("pipeline", "pipeline"))
        if spec_contains_prompt_steps(spec):
            var compiled_path = resolve_compiled_pipeline_path(file_path, pipeline_name, "mojo")
            var os_module = Python.import_module("os")
            if not Bool(os_module.path.exists(compiled_path)):
                raise "Pipeline contains $prompt steps but compiled JSON was not found. Run prompt codegen. Expected compiled pipeline at: " + compiled_path
            var compiled_file = builtins_module.open(compiled_path, "r", encoding = PythonObject("utf-8"))
            var compiled_text = compiled_file.read()
            compiled_file.close()
            return self.load_str(String(compiled_text), registry)
        return self.build_from_spec(spec, registry)

    fn build_from_spec(self, spec: PythonObject, mut registry: PipelineRegistry) raises -> Pipeline:
        if spec_contains_prompt_steps(spec):
            raise "Pipeline contains $prompt steps. Run prompt codegen and load the compiled JSON under pipelines/generated/mojo/."

        var pipeline_name = String(spec.get("pipeline", "pipeline"))
        var pipeline_type = String(spec.get("type", "unary"))

        if pipeline_type != "unary":
            raise "Only 'unary' pipelines are supported by this loader"

        var short_circuit_on_exception = True
        var short_circuit_on_exception_value = spec.get("shortCircuitOnException")
        if short_circuit_on_exception_value is not None:
            short_circuit_on_exception = Bool(short_circuit_on_exception_value)
        else:
            var short_circuit_value = spec.get("shortCircuit")
            if short_circuit_value is not None:
                short_circuit_on_exception = Bool(short_circuit_value)

        var pipeline = Pipeline(pipeline_name, short_circuit_on_exception)

        var remote_defaults = RemoteDefaults()
        var remote_defaults_node = spec.get("remoteDefaults")
        if remote_defaults_node is not None:
            remote_defaults = self.parse_remote_defaults(remote_defaults_node, remote_defaults)

        self.add_section(spec, "pre", pipeline, registry, remote_defaults)
        if spec.get("actions") is not None:
            self.add_section(spec, "actions", pipeline, registry, remote_defaults)
        else:
            self.add_section(spec, "steps", pipeline, registry, remote_defaults)
        self.add_section(spec, "post", pipeline, registry, remote_defaults)

        return pipeline^

    fn add_section(self,
                   spec: PythonObject,
                   section_name: String,
                   mut pipeline: Pipeline,
                   mut registry: PipelineRegistry,
                   remote_defaults: RemoteDefaults) raises -> None:
        var nodes = spec.get(section_name)
        if nodes is None:
            return

        for node in nodes:
            self.add_step(node, section_name, pipeline, registry, remote_defaults)

    fn add_step(self,
                node: PythonObject,
                section_name: String,
                mut pipeline: Pipeline,
                mut registry: PipelineRegistry,
                remote_defaults: RemoteDefaults) raises -> None:
        if node.get("$prompt") is not None:
            raise "Runtime does not execute $prompt steps. Run prompt codegen to produce a compiled pipeline JSON with $local references."

        var display_name = ""
        var name_value = node.get("name")
        if name_value is not None:
            display_name = String(name_value)
        else:
            var label_value = node.get("label")
            if label_value is not None:
                display_name = String(label_value)

        var local_ref_value = node.get("$local")
        if local_ref_value is not None:
            var local_ref = String(local_ref_value)
            self.add_local(local_ref, display_name, section_name, pipeline, registry)
            return

        var remote_node = node.get("$remote")
        if remote_node is not None:
            var remote_spec = self.parse_remote_spec(remote_node, remote_defaults)
            self.add_remote(remote_spec, display_name, section_name, pipeline)
            return

        raise "Unsupported action: expected '$local' or '$remote'"

    fn add_local(self,
                 local_ref: String,
                 display_name: String,
                 section_name: String,
                 mut pipeline: Pipeline,
                 mut registry: PipelineRegistry) raises -> None:
        if registry.has_unary(local_ref):
            var unary_action = registry.get_unary(local_ref)
            if section_name == "pre":
                pipeline.add_pre_action_named(display_name, unary_action)
            elif section_name == "post":
                pipeline.add_post_action_named(display_name, unary_action)
            else:
                pipeline.add_action_named(display_name, unary_action)
            return

        if registry.has_action(local_ref):
            var step_action = registry.get_action(local_ref)
            if section_name == "pre":
                pipeline.add_pre_action_named(display_name, step_action)
            elif section_name == "post":
                pipeline.add_post_action_named(display_name, step_action)
            else:
                pipeline.add_action_named(display_name, step_action)
            return

        if local_ref.startswith("prompt:"):
            raise "Prompt-generated action is missing from the registry: " + local_ref + ". Run prompt codegen and register generated actions."

        raise "Unknown $local reference: " + local_ref

    fn parse_remote_spec(self, remote_node: PythonObject, remote_defaults: RemoteDefaults) raises -> RemoteSpec:
        var builtins_module = Python.import_module("builtins")
        if builtins_module.isinstance(remote_node, builtins_module.str):
            return remote_defaults.to_spec(String(remote_node))

        var endpoint_value = remote_node.get("endpoint")
        if endpoint_value is None:
            endpoint_value = remote_node.get("path")
        if endpoint_value is None:
            raise "Missing required $remote field: endpoint|path"

        var remote_spec = remote_defaults.to_spec(String(endpoint_value))

        var timeout_value = remote_node.get("timeoutMillis")
        if timeout_value is None:
            timeout_value = remote_node.get("timeout_millis")
        if timeout_value is not None:
            remote_spec.timeout_millis = Int(timeout_value)

        var retries_value = remote_node.get("retries")
        if retries_value is not None:
            remote_spec.retries = Int(retries_value)

        var method_value = remote_node.get("method")
        if method_value is not None:
            remote_spec.method = String(method_value)

        var headers_value = remote_node.get("headers")
        if headers_value is not None:
            var merged_headers = builtins_module.dict()
            var base_headers = remote_spec.headers
            if base_headers is not None:
                merged_headers.update(base_headers)
            merged_headers.update(headers_value)
            remote_spec.headers = merged_headers

        return remote_spec

    fn parse_remote_defaults(self, node: PythonObject, base: RemoteDefaults) raises -> RemoteDefaults:
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
            defaults.timeout_millis = Int(timeout_value)

        var retries_value = node.get("retries")
        if retries_value is not None:
            defaults.retries = Int(retries_value)

        var method_value = node.get("method")
        if method_value is not None:
            defaults.method = String(method_value)

        var headers_value = node.get("headers")
        if headers_value is not None:
            defaults.headers = headers_value

        return defaults

    fn add_remote(self,
                  spec: RemoteSpec,
                  display_name: String,
                  section_name: String,
                  mut pipeline: Pipeline) -> None:
        if section_name == "pre":
            pipeline.add_pre_action_named(display_name, spec)
        elif section_name == "post":
            pipeline.add_post_action_named(display_name, spec)
        else:
            pipeline.add_action_named(display_name, spec)


fn spec_contains_prompt_steps(spec: PythonObject) raises -> Bool:
    for section_name in ["pre", "actions", "steps", "post"]:
        var nodes_value = spec.get(section_name)
        if nodes_value is None:
            continue
        for node in nodes_value:
            if node is not None and node.get("$prompt") is not None:
                return True
    return False


fn resolve_compiled_pipeline_path(source_file_path: String, pipeline_name: String, language_name: String) raises -> String:
    var pathlib_module = Python.import_module("pathlib")
    var source_path = pathlib_module.Path(source_file_path).resolve()
    var current_dir = source_path.parent
    while True:
        if String(current_dir.name) == "pipelines":
            var compiled_path = current_dir / "generated" / language_name / (pipeline_name + ".json")
            return String(compiled_path)
        if current_dir.parent == current_dir:
            break
        current_dir = current_dir.parent
    raise "Pipeline contains $prompt steps but the pipelines root directory could not be inferred from path: " + String(source_path)
