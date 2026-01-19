from python import Python
from python import PythonObject

from ..core.registry import PipelineRegistry

fn normalize_name_action(text_value: PythonObject) raises -> PythonObject:
    var output_string: String = String(text_value)
    var python_re_module = Python.import_module("re")
    output_string = String(python_re_module.sub("\\s+", " ", output_string))
    output_string = String(output_string.strip())
    var python_string_module = Python.import_module("string")
    output_string = String(python_string_module.capwords(output_string))
    return PythonObject(output_string)

fn register_generated_actions(mut registry: PipelineRegistry) -> None:
    registry.register_unary("prompt:normalize_name", normalize_name_action)
