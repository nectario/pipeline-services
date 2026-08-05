import { Pipeline } from "../../index.js";

async function main(): Promise<void> {
  const pipeline = new Pipeline<string>("example00_import", true);
  const output = await pipeline.run("ok");
  // eslint-disable-next-line no-console
  console.log(output);
}

void main();
