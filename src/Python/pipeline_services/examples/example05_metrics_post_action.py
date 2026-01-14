from __future__ import annotations

from pipeline_services.core.metrics_actions import print_metrics
from pipeline_services.core.pipeline import Pipeline, StepControl
from pipeline_services.examples.text_steps import normalize_whitespace, strip


def truncate_at_16(text_value: str, control: StepControl) -> str:
    text_string = str(text_value)
    if len(text_string) <= 16:
        return text_string
    control.short_circuit()
    return text_string[0:16]


def main() -> None:
    pipeline = Pipeline("example05_metrics_post_action", True)
    pipeline.add_action(strip)
    pipeline.add_action(normalize_whitespace)
    pipeline.add_action_named("truncate", truncate_at_16)
    pipeline.add_post_action_named("metrics", print_metrics)

    result = pipeline.execute("  Hello   Metrics  ")
    print("output=", result.context)
    print("totalNanos=", result.total_nanos)
    print("timingsCount=", len(result.timings))


if __name__ == "__main__":
    main()

