from std.python import PythonObject

from pipeline_services import Pipeline


def main() raises:
    var pipeline = Pipeline("example00_import", True)
    print(pipeline.run(PythonObject("ok")))
