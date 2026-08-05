from std.python import PythonObject

from pipeline_services.core.runtime_pipeline import RuntimePipeline
from pipeline_services.examples.text_steps import normalize_whitespace, strip


def main() raises:
    var runtime_pipeline = RuntimePipeline(
        "example03_runtime_pipeline",
        False,
        PythonObject("  Hello   Runtime  "),
    )
    _ = runtime_pipeline.add_action(strip)
    _ = runtime_pipeline.add_action(normalize_whitespace)
    print("runtimeValue=", runtime_pipeline.value())

    var frozen_pipeline = runtime_pipeline.freeze()
    print(
        "frozenValue=",
        frozen_pipeline.run(PythonObject("  Hello   Frozen  ")),
    )
