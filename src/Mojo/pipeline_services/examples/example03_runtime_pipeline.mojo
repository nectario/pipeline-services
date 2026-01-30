from python import PythonObject

from pipeline_services.core.runtime_pipeline import RuntimePipeline
from pipeline_services.examples.text_steps import strip, normalize_whitespace

fn main() raises:
    var runtime_pipeline = RuntimePipeline("example03_runtime_pipeline", False, PythonObject("  Hello   Runtime  "))
    runtime_pipeline.add_action(strip)
    runtime_pipeline.add_action(normalize_whitespace)
    print("runtimeValue=", runtime_pipeline.value())

    var frozen_pipeline = runtime_pipeline.freeze()
    var result = frozen_pipeline.run(PythonObject("  Hello   Frozen  "))
    print("frozenValue=", result.context)
