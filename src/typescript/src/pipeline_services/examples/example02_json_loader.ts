import { PipelineJsonLoader, PipelineRegistry } from "../../index.js";
import { normalize_whitespace, strip } from "./text_steps.js";

async function main(): Promise<void> {
  const registry = new PipelineRegistry();
  registry.register_unary("strip", strip);
  registry.register_unary("normalize_whitespace", normalize_whitespace);

  const json_text = `
{
  "pipeline": "example02_json_loader",
  "type": "unary",
  "shortCircuitOnException": true,
  "steps": [
    {"$local": "strip"},
    {"$local": "normalize_whitespace"}
  ]
}
`;

  const loader = new PipelineJsonLoader();
  const pipeline = loader.load_str(json_text, registry);
  const result = await pipeline.run("  Hello   JSON  ");
  // eslint-disable-next-line no-console
  console.log(result.context);
}

void main();
