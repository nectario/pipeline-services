from __future__ import annotations

from pipeline_services.core.pipeline import Pipeline
from pipeline_services.examples.text_steps import append_marker, normalize_whitespace, strip, to_lower
from pipeline_services.remote.http_step import RemoteSpec


def main() -> None:
    fixture_endpoint = "http://127.0.0.1:8765/echo"

    remote_spec = RemoteSpec(fixture_endpoint)
    remote_spec.method = "POST"
    remote_spec.timeout_millis = 1000
    remote_spec.retries = 0

    pipeline = Pipeline("example06_mixed_local_remote", short_circuit_on_exception=True)
    pipeline.add_action(strip)
    pipeline.add_action(normalize_whitespace)
    pipeline.add_action_named("remote_echo", remote_spec)
    pipeline.add_action(to_lower)
    pipeline.add_action(append_marker)

    output_value = pipeline.run("  Hello   Remote  ")
    print("output=" + str(output_value))


if __name__ == "__main__":
    main()

