from std.python import Python, PythonObject

from pipeline_services.config.json_loader import PipelineJsonLoader
from pipeline_services.core.registry import PipelineRegistry
from pipeline_services.examples.text_steps import strip
from pipeline_services.generated import register_generated_actions


def find_pipeline_file(pipeline_file_name: String) raises -> String:
    var os_module = Python.import_module("os")
    var pathlib_module = Python.import_module("pathlib")
    var current_dir = pathlib_module.Path(os_module.getcwd()).resolve()
    while True:
        var candidate_path = current_dir / "pipelines" / pipeline_file_name
        if Bool(py=candidate_path.exists()):
            return String(candidate_path)
        if current_dir.parent == current_dir:
            break
        current_dir = current_dir.parent
    raise "Could not locate pipelines directory from current working directory"


def main() raises:
    var pipeline_file = find_pipeline_file("normalize_name.json")
    var registry = PipelineRegistry()
    registry.register_action("strip", strip)
    register_generated_actions(registry)

    var pipeline = PipelineJsonLoader().load_file(pipeline_file, registry)
    print("output=" + String(pipeline.run(PythonObject("  john   SMITH "))))
