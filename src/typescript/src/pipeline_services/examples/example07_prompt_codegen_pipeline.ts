import { existsSync } from "node:fs";
import path from "node:path";

import {
  PipelineJsonLoader,
  PipelineRegistry,
} from "../../index.js";
import { register_generated_actions } from "../generated/index.js";
import { strip } from "./text_steps.js";

function findPipelineFile(pipelineFileName: string): string {
  let currentDir = process.cwd();
  while (true) {
    const candidatePath = path.join(
      currentDir,
      "pipelines",
      pipelineFileName,
    );
    if (existsSync(candidatePath)) {
      return candidatePath;
    }
    const parentDir = path.dirname(currentDir);
    if (parentDir === currentDir) {
      break;
    }
    currentDir = parentDir;
  }
  throw new Error(
    "Could not locate pipelines directory from current working directory",
  );
}

async function main(): Promise<void> {
  const pipelineFile = findPipelineFile("normalize_name.json");

  const registry = new PipelineRegistry();
  registry.register_unary("strip", strip);
  register_generated_actions(registry);

  const pipeline = await new PipelineJsonLoader().load_file(
    pipelineFile,
    registry,
  );
  const output = await pipeline.run("  john   SMITH ");
  // eslint-disable-next-line no-console
  console.log("output=" + String(output));
}

void main();
