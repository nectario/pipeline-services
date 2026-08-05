from std.python import PythonObject

from pipeline_services.core.pipeline import Pipeline, short_circuit
from pipeline_services.examples.text_steps import normalize_whitespace, strip


def truncate_at_280(text_value: PythonObject) raises -> PythonObject:
    var text_string = String(text_value)
    if text_string.count_codepoints() <= 280:
        return PythonObject(text_string)
    short_circuit()
    return PythonObject(String(text_string[codepoint=0:280]))


def main() raises:
    var pipeline = Pipeline("example01_text_clean", True)
    pipeline.add_action(strip)
    pipeline.add_action(normalize_whitespace)
    pipeline.add_action_named("truncate", truncate_at_280)

    var result = pipeline.run_detailed(PythonObject("  Hello   World  "))
    print("output=", result.context)
    print("shortCircuited=", result.short_circuited)
    print("errors=", len(result.errors))
