import { RuntimePipeline } from "../../index.js";
import { normalize_whitespace, strip } from "./text_steps.js";

async function main(): Promise<void> {
  const runtimePipeline = new RuntimePipeline<string>(
    "example03_runtime_pipeline",
    false,
    "  Hello   Runtime  ",
  );
  await runtimePipeline.addAction(strip);
  await runtimePipeline.addAction(normalize_whitespace);
  // eslint-disable-next-line no-console
  console.log("runtimeValue=", runtimePipeline.value());

  const frozenPipeline = runtimePipeline.freeze();
  const output = await frozenPipeline.run("  Hello   Frozen  ");
  // eslint-disable-next-line no-console
  console.log("frozenValue=", output);
}

void main();
