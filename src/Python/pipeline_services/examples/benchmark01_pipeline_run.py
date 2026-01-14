from __future__ import annotations

import time
from typing import Dict, List

from pipeline_services.core.pipeline import Pipeline
from pipeline_services.examples.text_steps import append_marker, strip, to_lower


def main() -> None:
    pipeline = Pipeline("benchmark01_pipeline_run", True)
    pipeline.add_action(strip)
    pipeline.add_action(to_lower)
    pipeline.add_action(append_marker)

    input_value = "  Hello Benchmark  "
    warmup_iterations = 1000
    iterations = 10_000

    warmup_index = 0
    while warmup_index < warmup_iterations:
        pipeline.run(input_value)
        warmup_index += 1

    total_pipeline_nanos = 0
    action_totals: Dict[str, int] = {}
    action_counts: Dict[str, int] = {}
    action_names: List[str] = []

    start_ns = time.perf_counter_ns()
    iteration_index = 0
    while iteration_index < iterations:
        result = pipeline.execute(input_value)
        total_pipeline_nanos += result.total_nanos

        for timing in result.timings:
            if timing.action_name in action_totals:
                action_totals[timing.action_name] += timing.elapsed_nanos
                action_counts[timing.action_name] += 1
            else:
                action_totals[timing.action_name] = timing.elapsed_nanos
                action_counts[timing.action_name] = 1
                action_names.append(timing.action_name)

        iteration_index += 1
    end_ns = time.perf_counter_ns()
    wall_nanos = end_ns - start_ns

    print("iterations=", iterations)
    print("wallMs=", float(wall_nanos) / 1_000_000.0)
    print("avgPipelineUs=", float(total_pipeline_nanos) / float(iterations) / 1_000.0)
    print("avgActionUs=")
    for action_name in action_names:
        nanos_total = action_totals[action_name]
        count_total = action_counts[action_name]
        print("  ", action_name, "=", float(nanos_total) / float(count_total) / 1_000.0)


if __name__ == "__main__":
    main()

