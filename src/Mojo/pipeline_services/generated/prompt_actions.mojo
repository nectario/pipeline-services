from std.python import Python, PythonObject

from ..core.registry import PipelineRegistry


def normalize_name_action(text_value: PythonObject) raises -> PythonObject:
    var python_re_module = Python.import_module("re")
    var output_string = String(
        python_re_module.sub("\\s+", " ", String(text_value))
    ).strip()
    var python_string_module = Python.import_module("string")
    return PythonObject(String(python_string_module.capwords(output_string)))


def register_generated_actions(mut registry: PipelineRegistry) raises:
    registry.register_action(
        "prompt:normalize_name",
        normalize_name_action,
    )
