from python import PythonObject

from pipeline_services.core.pipeline import Pipeline, StepControl
from pipeline_services.core.metrics_actions import print_metrics
from pipeline_services.examples.text_steps import strip, normalize_whitespace

fn truncate_at_16(text_value: PythonObject, mut control: StepControl) raises -> PythonObject:
    var text_string: String = String(text_value)
    if len(text_string) <= 16:
        return PythonObject(text_string)
    control.short_circuit()
    return PythonObject(String(text_string[0:16]))

fn main() raises:
    var pipeline = Pipeline("example05_metrics_post_action", True)
    pipeline.add_action(strip)
    pipeline.add_action(normalize_whitespace)
    pipeline.add_action_named("truncate", truncate_at_16)
    pipeline.add_post_action_named("metrics", print_metrics)

    var result = pipeline.execute(PythonObject("  Hello   Metrics  "))
    print("output=", result.context)
    print("totalNanos=", result.total_nanos)
    print("timingsCount=", len(result.timings))
