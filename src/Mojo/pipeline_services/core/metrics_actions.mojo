from python import Python
from python import PythonObject

from .pipeline import StepControl

fn print_metrics(ctx: PythonObject, mut control: StepControl) -> PythonObject:
    try:
        var builtins_module = Python.import_module("builtins")
        var time_module = Python.import_module("time")
        var metrics_map = builtins_module.dict()

        metrics_map[PythonObject("pipeline")] = PythonObject(String(control.pipeline_name))
        metrics_map[PythonObject("shortCircuited")] = PythonObject(control.is_short_circuited())
        metrics_map[PythonObject("errorCount")] = PythonObject(Int(len(control.errors)))

        var now_nanos = Int64(time_module.perf_counter_ns())
        var start_nanos = control.run_start_ns
        var pipeline_nanos: Int64 = 0
        if start_nanos > 0 and now_nanos > start_nanos:
            pipeline_nanos = now_nanos - start_nanos
        metrics_map[PythonObject("pipelineLatencyMs")] = PythonObject(Float64(pipeline_nanos) / 1_000_000.0)

        var action_latency_ms = builtins_module.dict()
        for timing in control.timings:
            action_latency_ms[PythonObject(String(timing.action_name))] = PythonObject(Float64(timing.elapsed_nanos) / 1_000_000.0)
        metrics_map[PythonObject("actionLatencyMs")] = action_latency_ms

        print(metrics_map)
    except caught_error:
        print("metricsError=", String(caught_error))
    return ctx
