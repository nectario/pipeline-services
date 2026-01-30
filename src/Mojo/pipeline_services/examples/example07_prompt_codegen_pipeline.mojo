from python import Python
from python import PythonObject

from pipeline_services.config.json_loader import PipelineJsonLoader
from pipeline_services.core.registry import PipelineRegistry
from pipeline_services.examples.text_steps import strip
from pipeline_services.generated import register_generated_actions


fn find_pipeline_file(pipeline_file_name: String) raises -> String:
    var os_module = Python.import_module("os")
    var pathlib_module = Python.import_module("pathlib")
    var current_dir = pathlib_module.Path(os_module.getcwd()).resolve()
    while True:
        var candidate_path = current_dir / "pipelines" / pipeline_file_name
        if Bool(candidate_path.exists()):
            return String(candidate_path)
        if current_dir.parent == current_dir:
            break
        current_dir = current_dir.parent
    raise "Could not locate pipelines directory from current working directory"


fn main() raises:
    var pipeline_file = find_pipeline_file("normalize_name.json")

    var registry = PipelineRegistry()
    registry.register_unary("strip", strip)
    register_generated_actions(registry)

    var loader = PipelineJsonLoader()
    var pipeline = loader.load_file(pipeline_file, registry)
    var result = pipeline.run(PythonObject("  john   SMITH "))
    var output_text = "output=" + String(result.context)
    print(output_text)
