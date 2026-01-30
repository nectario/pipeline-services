import { existsSync } from "node:fs";
import path from "node:path";

import { PipelineJsonLoader, PipelineRegistry } from "../../index.js";
import { register_generated_actions } from "../generated/index.js";
import { strip } from "./text_steps.js";

function find_pipeline_file(pipeline_file_name: string): string {
  let current_dir = process.cwd();
  while (true) {
    const candidate_path = path.join(current_dir, "pipelines", pipeline_file_name);
    if (existsSync(candidate_path)) {
      return candidate_path;
    }
    const parent_dir = path.dirname(current_dir);
    if (parent_dir === current_dir) {
      break;
    }
    current_dir = parent_dir;
  }
  throw new Error("Could not locate pipelines directory from current working directory");
}

async function main(): Promise<void> {
  const pipeline_file = find_pipeline_file("normalize_name.json");

  const registry = new PipelineRegistry();
  registry.register_unary("strip", strip);
  register_generated_actions(registry);

  const loader = new PipelineJsonLoader();
  const pipeline = await loader.load_file(pipeline_file, registry);
  const result = await pipeline.run("  john   SMITH ");
  // eslint-disable-next-line no-console
  console.log("output=" + String(result.context));
}

void main();
