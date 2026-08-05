import { PipelineJsonLoader, PipelineRegistry } from "../../index.js";
import { normalize_whitespace, strip } from "./text_steps.js";

async function main(): Promise<void> {
  const registry = new PipelineRegistry();
  registry.register_unary("strip", strip);
  registry.register_unary("normalize_whitespace", normalize_whitespace);

  const jsonText = `
{
  "pipeline": "example02_json_loader",
  "type": "unary",
  "shortCircuitOnException": true,
  "actions": [
    {"$local": "strip"},
    {"$local": "normalize_whitespace"}
  ]
}
`;

  const pipeline = new PipelineJsonLoader().load_str(jsonText, registry);
  const output = await pipeline.run("  Hello   JSON  ");
  // eslint-disable-next-line no-console
  console.log(String(output));
}

void main();
