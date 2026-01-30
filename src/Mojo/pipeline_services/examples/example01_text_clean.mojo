from python import PythonObject

from pipeline_services.core.pipeline import Pipeline, StepControl
from pipeline_services.examples.text_steps import strip, normalize_whitespace

fn truncate_at_280(text_value: PythonObject, mut control: StepControl) raises -> PythonObject:
    var text_string: String = String(text_value)
    if len(text_string) <= 280:
        return PythonObject(text_string)
    control.short_circuit()
    var truncated_slice = text_string[0:280]
    return PythonObject(String(truncated_slice))

fn main() raises:
    var pipeline = Pipeline("example01_text_clean", True)
    pipeline.add_action(strip)
    pipeline.add_action(normalize_whitespace)
    pipeline.add_action_named("truncate", truncate_at_280)

    var result = pipeline.run(PythonObject("  Hello   World  "))
    print("output=", result.context)
    print("shortCircuited=", result.short_circuited)
    print("errors=", len(result.errors))
