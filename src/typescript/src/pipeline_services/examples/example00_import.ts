import { Pipeline } from "../../index.js";

async function main(): Promise<void> {
  const pipeline = new Pipeline("example00_import", true);
  const result = await pipeline.run("ok");
  // eslint-disable-next-line no-console
  console.log(result.context);
}

void main();
