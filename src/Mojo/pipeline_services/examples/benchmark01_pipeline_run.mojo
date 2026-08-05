from std.python import PythonObject

from pipeline_services.core.pipeline import Pipeline, _now_ns
from pipeline_services.examples.text_steps import append_marker, strip, to_lower


def main() raises:
    var pipeline = Pipeline("benchmark01_pipeline_run", True)
    pipeline.add_action(strip)
    pipeline.add_action(to_lower)
    pipeline.add_action(append_marker)

    var input_value = PythonObject("  Hello Benchmark  ")
    var warmup_iterations = 1000
    var iterations = 10_000

    var warmup_index = 0
    while warmup_index < warmup_iterations:
        _ = pipeline.run(input_value)
        warmup_index += 1

    var total_pipeline_nanos: Int64 = 0
    var wall_start_ns = _now_ns()
    var iteration_index = 0
    while iteration_index < iterations:
        var result = pipeline.run_detailed(input_value)
        total_pipeline_nanos += result.total_nanos
        iteration_index += 1

    var wall_nanos = _now_ns() - wall_start_ns
    print("iterations=", iterations)
    print("wallMs=", Float64(wall_nanos) / 1_000_000.0)
    print(
        "avgPipelineUs=",
        Float64(total_pipeline_nanos) / Float64(iterations) / 1_000.0,
    )
