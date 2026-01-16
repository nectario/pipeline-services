import { Pipeline, RemoteSpec } from "../../index.js";
import { append_marker, normalize_whitespace, strip, to_lower } from "./text_steps.js";

async function main(): Promise<void> {
  const fixture_endpoint = "http://127.0.0.1:8765/echo";

  const remote_spec = new RemoteSpec(fixture_endpoint);
  remote_spec.method = "POST";
  remote_spec.timeout_millis = 1000;
  remote_spec.retries = 0;

  const pipeline = new Pipeline("example06_mixed_local_remote", true);
  pipeline.add_action(strip);
  pipeline.add_action(normalize_whitespace);
  pipeline.add_action_named("remote_echo", remote_spec);
  pipeline.add_action(to_lower);
  pipeline.add_action(append_marker);

  const output_value = await pipeline.run("  Hello   Remote  ");
  // eslint-disable-next-line no-console
  console.log("output=" + String(output_value));
}

void main();

