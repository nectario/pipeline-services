from python import Python
from python import PythonObject
from collections.dict import Dict
from collections.list import List

from pipeline_services.core.pipeline import Pipeline
from pipeline_services.examples.text_steps import strip, to_lower, append_marker

fn now_ns(perf_counter_ns_fn: PythonObject) raises -> Int64:
    var value = perf_counter_ns_fn()
    return Int64(value)

fn main() raises:
    var time_module = Python.import_module("time")
    var perf_counter_ns_fn = time_module.perf_counter_ns

    var pipeline = Pipeline("benchmark01_pipeline_run", True)
    pipeline.add_action(strip)
    pipeline.add_action(to_lower)
    pipeline.add_action(append_marker)

    var input_value = PythonObject("  Hello Benchmark  ")
    var warmup_iterations: Int = 1000
    var iterations: Int = 10_000

    var warmup_index: Int = 0
    while warmup_index < warmup_iterations:
        pipeline.run(input_value)
        warmup_index = warmup_index + 1

    var total_pipeline_nanos: Int64 = 0
    var action_totals: Dict[String, Int64] = Dict[String, Int64]()
    var action_counts: Dict[String, Int] = Dict[String, Int]()
    var action_names: List[String] = List[String]()

    var start_ns = now_ns(perf_counter_ns_fn)
    var iteration_index: Int = 0
    while iteration_index < iterations:
        var result = pipeline.run(input_value)
        total_pipeline_nanos = total_pipeline_nanos + result.total_nanos

        for timing in result.timings:
            if timing.action_name in action_totals:
                action_totals[timing.action_name] = action_totals[timing.action_name] + timing.elapsed_nanos
                action_counts[timing.action_name] = action_counts[timing.action_name] + 1
            else:
                action_totals[timing.action_name] = timing.elapsed_nanos
                action_counts[timing.action_name] = 1
                action_names.append(timing.action_name)

        iteration_index = iteration_index + 1
    var end_ns = now_ns(perf_counter_ns_fn)
    var wall_nanos = end_ns - start_ns

    print("iterations=", iterations)
    print("wallMs=", Float64(wall_nanos) / 1_000_000.0)
    print("avgPipelineUs=", Float64(total_pipeline_nanos) / Float64(iterations) / 1_000.0)

    print("avgActionUs=")
    for action_name in action_names:
        var nanos_total = action_totals[action_name]
        var count_total = action_counts[action_name]
        print("  ", action_name, "=", Float64(nanos_total) / Float64(count_total) / 1_000.0)
