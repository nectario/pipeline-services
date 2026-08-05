import {
  Pipeline,
  RemoteSpec,
  http_step,
} from "../../index.js";
import {
  append_marker,
  normalize_whitespace,
  strip,
  to_lower,
} from "./text_steps.js";

async function main(): Promise<void> {
  const remoteSpec = new RemoteSpec(
    "http://127.0.0.1:8765/echo",
  );
  remoteSpec.method = "POST";
  remoteSpec.timeout_millis = 1000;
  remoteSpec.retries = 0;

  const pipeline = new Pipeline<string>(
    "example06_mixed_local_remote",
    true,
  )
    .addAction(strip)
    .addAction(normalize_whitespace)
    .addAction(
      (context) => http_step(remoteSpec, context),
      "remote_echo",
    )
    .addAction(to_lower)
    .addAction(append_marker);

  const output = await pipeline.run("  Hello   Remote  ");
  // eslint-disable-next-line no-console
  console.log("output=" + output);
}

void main();
