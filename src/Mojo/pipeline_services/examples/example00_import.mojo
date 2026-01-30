from python import PythonObject

from pipeline_services import Pipeline

fn main() raises:
    var pipeline = Pipeline("example00_import", True)
    var result = pipeline.run(PythonObject("ok"))
    print(result.context)
