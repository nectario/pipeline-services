from python import Python, PythonObject
from collections.list import List

from ..core.pipeline import Pipeline, Pipe, StepFunction, TypedStepFunction
from ..core.jumps import jump_now, jump_after

struct PipelineJsonLoader:
    var beans: PythonObject
    var instance_object: PythonObject

    fn __init__(out self, beans: PythonObject = None, instance_object: PythonObject = None):
        self.beans = beans
        self.instance_object = instance_object

    fn load_str(self, json_string: String) raises -> PythonObject:
        var python_runtime = Python
        var json_module = python_runtime.import_module("json")
        var spec = json_module.loads(json_string)
        return self._build_from_spec(spec)

    fn load_file(self, file_path: String) raises -> PythonObject:
        var python_runtime = Python
        var json_module = python_runtime.import_module("json")
        var builtins_module = python_runtime.import_module("builtins")
        var file_obj = builtins_module.open(file_path, "r", encoding="utf-8")
        var text = file_obj.read(); file_obj.close()
        return self.load_str(text)

    fn _build_from_spec(self, root_spec: PythonObject) raises -> PythonObject:
        var pipeline_name: String = String(root_spec.get("pipeline", "pipeline"))
        var pipeline_type: String = String(root_spec.get("type", "unary"))
        var short_flag: Bool = Bool(root_spec.get("shortCircuit", True))

        if pipeline_type == "unary":
            var p = Pipeline(pipeline_name, short_flag)
            for section_name in ["pre", "steps", "post"]:
                var nodes = root_spec.get(section_name, None)
                if nodes is not None:
                    for node in nodes:
                        var fn_ptr, label = self._compile_step(node, False)
                        var section_label: String = "main" if section_name == "steps" else section_name
                        p.step(fn_ptr, label, "", section_label)
            return p
        elif pipeline_type == "typed":
            var pipe = Pipe(pipeline_name, short_flag)
            for section_name in ["pre", "steps", "post"]:
                var nodes = root_spec.get(section_name, None)
                if nodes is not None:
                    for node in nodes:
                        var fn_ptr, _ = self._compile_step(node, True)
                        pipe.step(fn_ptr)
            return pipe
        else:
            raise "Unsupported pipeline type: " + pipeline_type

    fn _compile_step(self, node: PythonObject, typed: Bool) raises -> (StepFunction, String):
        var label: String = String(node.get("label", ""))

        # jumpWhen
        var predicate_fn: StepFunction
        var jump_label: String = ""
        var jump_delay_ms: Int = 0
        var has_predicate: Bool = False
        var jump_when = node.get("jumpWhen", None)
        if jump_when is not None:
            jump_label = String(jump_when.get("label", ""))
            jump_delay_ms = Int(jump_when.get("delayMillis", 0))
            var predicate_spec = jump_when.get("predicate", None)
            if predicate_spec is not None:
                var inner_predicate, _ = self._compile_step(predicate_spec, True)
                predicate_fn = inner_predicate
                has_predicate = True

        var step_callable: StepFunction

        if "$local" in node:
            var fqcn = String(node["$local"])
            step_callable = self._resolve_local(fqcn)
        elif "$method" in node:
            step_callable = self._resolve_method(node["$method"])
        elif "$prompt" in node:
            step_callable = self._resolve_prompt(node["$prompt"])
        elif "$remote" in node:
            step_callable = self._resolve_remote(node["$remote"])
        else:
            raise "Unsupported step node"

        if jump_when is not None and has_predicate and jump_label != "":
            fn wrapped(input_value: PythonObject) raises -> PythonObject:
                var pred_value = predicate_fn(input_value)
                var truthy = Bool(pred_value)
                if truthy:
                    if jump_delay_ms > 0: jump_after(jump_label, jump_delay_ms)
                    else: jump_now(jump_label)
                return step_callable(input_value)
            return (wrapped, label)
        else:
            return (step_callable, label)

    fn _resolve_local(self, fqcn: String) raises -> StepFunction:
        var python_runtime = Python
        var importlib_module = python_runtime.import_module("importlib")
        var module_name = fqcn.rsplit(".", 1)[0]
        var class_name = fqcn.rsplit(".", 1)[1]
        var module_obj = importlib_module.import_module(module_name)
        var cls = getattr(module_obj, class_name)
        var inst = cls()
        if hasattr(inst, "apply"):
            fn call_apply(input_value: PythonObject) raises -> PythonObject:
                return inst.apply(input_value)
            return call_apply
        else:
            raise "Class " + fqcn + " lacks an 'apply' method"

    fn _resolve_method(self, spec: PythonObject) raises -> StepFunction:
        var ref = String(spec.get("ref", ""))
        var target = String(spec.get("target", ""))

        if "#" in ref:
            var parts = ref.split("#")
            var mod_cls = parts[0]
            var method_name = parts[1]
            var module_name = mod_cls.rsplit(".", 1)[0]
            var class_name = mod_cls.rsplit(".", 1)[1]

            var python_runtime = Python
            var importlib_module = python_runtime.import_module("importlib")
            var module_obj = importlib_module.import_module(module_name)
            var cls = getattr(module_obj, class_name)

            var obj: PythonObject
            if target == "@this":
                obj = self.instance_object
                if obj is None: raise "target=@this but instance_object is None"
            elif len(target) > 0 and target[0] == "@":
                var bean_id = target[1:]
                obj = None
                if self.beans is not None and bean_id in self.beans:
                    obj = self.beans[bean_id]
                if obj is None: raise "Unknown bean id: " + bean_id
            else:
                obj = cls

            var method_obj = getattr(obj, method_name)
            fn call_method(input_value: PythonObject) raises -> PythonObject:
                return method_obj(input_value)
            return call_method
        else:
            var module_name = ref.rsplit(":", 1)[0]
            var func_name = ref.rsplit(":", 1)[1]
            var python_runtime = Python
            var importlib_module = python_runtime.import_module("importlib")
            var module_obj = importlib_module.import_module(module_name)
            var func_obj = getattr(module_obj, func_name)
            fn call_func(input_value: PythonObject) raises -> PythonObject:
                return func_obj(input_value)
            return call_func

    fn _resolve_prompt(self, prompt_spec: PythonObject) -> StepFunction:
        if self.beans is not None and "llm_adapter" in self.beans:
            var adapter = self.beans["llm_adapter"]
            fn call_prompt(input_value: PythonObject) raises -> PythonObject:
                return adapter(input_value, prompt_spec)
            return call_prompt
        else:
            fn not_configured(input_value: PythonObject) raises -> PythonObject:
                raise "No 'llm_adapter' bean provided for $prompt step"
            return not_configured

    fn _resolve_remote(self, remote_spec: PythonObject) -> StepFunction:
        from ..remote.http_step import http_step, RemoteSpec
        var endpoint = String(remote_spec.get("endpoint"))
        var timeout_millis = Int(remote_spec.get("timeoutMillis", 1000))
        var retries = Int(remote_spec.get("retries", 0))
        var method = String(remote_spec.get("method", "POST"))
        var headers = remote_spec.get("headers", Python.dict())

        var rs = RemoteSpec(endpoint, timeout_millis, retries, headers, method)

        var to_json_id = remote_spec.get("toJsonBean", None)
        var from_json_id = remote_spec.get("fromJsonBean", None)
        if to_json_id is not None and self.beans is not None and to_json_id in self.beans:
            rs.to_json = self.beans[to_json_id]
        if from_json_id is not None and self.beans is not None and from_json_id in self.beans:
            rs.from_json = self.beans[from_json_id]

        return http_step(rs)
