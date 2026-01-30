import { RuntimePipeline } from "../../index.js";
import { normalize_whitespace, strip } from "./text_steps.js";

async function main(): Promise<void> {
  const runtime_pipeline = new RuntimePipeline("example03_runtime_pipeline", false, "  Hello   Runtime  ");
  await runtime_pipeline.add_action(strip);
  await runtime_pipeline.add_action(normalize_whitespace);
  // eslint-disable-next-line no-console
  console.log("runtimeValue=", runtime_pipeline.value());

  const frozen_pipeline = runtime_pipeline.freeze();
  const result = await frozen_pipeline.run("  Hello   Frozen  ");
  // eslint-disable-next-line no-console
  console.log("frozenValue=", result.context);
}

void main();
