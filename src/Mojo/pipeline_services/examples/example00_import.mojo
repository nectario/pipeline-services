from python import PythonObject

from pipeline_services import Pipeline

fn main() raises:
    var pipeline = Pipeline("example00_import", True)
    var output_value = pipeline.run(PythonObject("ok"))
    print(output_value)
