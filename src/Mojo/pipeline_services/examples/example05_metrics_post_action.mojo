from std.python import PythonObject

from pipeline_services.core.pipeline import Pipeline, short_circuit
from pipeline_services.core.metrics_actions import print_metrics
from pipeline_services.examples.text_steps import normalize_whitespace, strip


def truncate_at_16(text_value: PythonObject) raises -> PythonObject:
    var text_string = String(text_value)
    if text_string.count_codepoints() <= 16:
        return PythonObject(text_string)
    short_circuit()
    return PythonObject(String(text_string[codepoint=0:16]))


def main() raises:
    var pipeline = Pipeline("example05_metrics_post_action", True)
    pipeline.add_action(strip)
    pipeline.add_action(normalize_whitespace)
    pipeline.add_action_named("truncate", truncate_at_16)
    pipeline.add_post_action_named("metrics", print_metrics)

    var result = pipeline.run_detailed(PythonObject("  Hello   Metrics  "))
    print("output=", result.context)
    print("totalNanos=", result.total_nanos)
    print("timingsCount=", len(result.action_timings))
