from python import PythonObject

from pipeline_services.core.pipeline import Pipeline
from pipeline_services.remote.http_step import RemoteSpec
from pipeline_services.examples.text_steps import strip, normalize_whitespace, to_lower, append_marker

fn main() raises:
    var fixture_endpoint = "http://127.0.0.1:8765/echo"

    var remote_spec = RemoteSpec(fixture_endpoint)
    remote_spec.method = "POST"
    remote_spec.timeout_millis = 1000
    remote_spec.retries = 0

    var pipeline = Pipeline("example06_mixed_local_remote", True)
    pipeline.add_action(strip)
    pipeline.add_action(normalize_whitespace)
    pipeline.add_action_named("remote_echo", remote_spec)
    pipeline.add_action(to_lower)
    pipeline.add_action(append_marker)

    var output_value = pipeline.run(PythonObject("  Hello   Remote  "))
    var output_string: String = String(output_value)
    print("output=" + output_string)

