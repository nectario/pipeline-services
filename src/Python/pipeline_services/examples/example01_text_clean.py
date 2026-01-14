from __future__ import annotations

from pipeline_services.core.pipeline import Pipeline, StepControl
from pipeline_services.examples.text_steps import normalize_whitespace, strip


def truncate_at_280(text_value: str, control: StepControl) -> str:
    text_string = str(text_value)
    if len(text_string) <= 280:
        return text_string
    control.short_circuit()
    return text_string[0:280]


def main() -> None:
    pipeline = Pipeline("example01_text_clean", True)
    pipeline.add_action(strip)
    pipeline.add_action(normalize_whitespace)
    pipeline.add_action_named("truncate", truncate_at_280)

    result = pipeline.execute("  Hello   World  ")
    print("output=", result.context)
    print("shortCircuited=", result.short_circuited)
    print("errors=", len(result.errors))


if __name__ == "__main__":
    main()

