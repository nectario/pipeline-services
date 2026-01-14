from __future__ import annotations

import time
from typing import Any, Dict

from .pipeline import StepControl


def print_metrics(ctx: Any, control: StepControl) -> Any:
    try:
        metrics_map: Dict[str, Any] = {}

        metrics_map["pipeline"] = control.pipeline_name
        metrics_map["shortCircuited"] = control.is_short_circuited()
        metrics_map["errorCount"] = len(control.errors)

        now_nanos = time.perf_counter_ns()
        start_nanos = control.run_start_ns
        pipeline_nanos = 0
        if start_nanos > 0 and now_nanos > start_nanos:
            pipeline_nanos = now_nanos - start_nanos
        metrics_map["pipelineLatencyMs"] = float(pipeline_nanos) / 1_000_000.0

        action_latency_ms: Dict[str, float] = {}
        for timing in control.timings:
            action_latency_ms[timing.action_name] = float(timing.elapsed_nanos) / 1_000_000.0
        metrics_map["actionLatencyMs"] = action_latency_ms

        print(metrics_map)
    except Exception as caught_error:
        print("metricsError=", str(caught_error))
    return ctx

