from __future__ import annotations

from pipeline_services.core.runtime_pipeline import RuntimePipeline
from pipeline_services.examples.text_steps import normalize_whitespace, strip


def main() -> None:
    runtime_pipeline = RuntimePipeline("example03_runtime_pipeline", False, "  Hello   Runtime  ")
    runtime_pipeline.add_action(strip)
    runtime_pipeline.add_action(normalize_whitespace)
    print("runtimeValue=", runtime_pipeline.value())

    frozen_pipeline = runtime_pipeline.freeze()
    result = frozen_pipeline.run("  Hello   Frozen  ")
    print("frozenValue=", result.context)


if __name__ == "__main__":
    main()
