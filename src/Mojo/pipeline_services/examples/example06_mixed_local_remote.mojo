from std.python import PythonObject

from pipeline_services.core.pipeline import Pipeline
from pipeline_services.remote.http_step import RemoteSpec, remote_action
from pipeline_services.examples.text_steps import append_marker, normalize_whitespace, strip, to_lower


def main() raises:
    var remote_spec = RemoteSpec("http://127.0.0.1:8765/echo")
    remote_spec.method = "POST"
    remote_spec.timeout_millis = 1000
    remote_spec.retries = 0

    var pipeline = Pipeline("example06_mixed_local_remote", True)
    pipeline.add_action(strip)
    pipeline.add_action(normalize_whitespace)
    pipeline.add_action_named("remote_echo", remote_action(remote_spec))
    pipeline.add_action(to_lower)
    pipeline.add_action(append_marker)

    print("output=" + String(pipeline.run(PythonObject("  Hello   Remote  "))))
