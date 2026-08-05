import { Pipeline, shortCircuit } from "../../index.js";
import { normalize_whitespace, strip } from "./text_steps.js";

async function truncateAt280(textValue: string): Promise<string> {
  if (textValue.length <= 280) {
    return textValue;
  }
  shortCircuit();
  return textValue.slice(0, 280);
}

async function main(): Promise<void> {
  const pipeline = new Pipeline<string>("example01_text_clean", true)
    .addAction(strip)
    .addAction(normalize_whitespace)
    .addAction(truncateAt280, "truncate");

  const result = await pipeline.runDetailed("  Hello   World  ");
  // eslint-disable-next-line no-console
  console.log("output=", result.context);
  // eslint-disable-next-line no-console
  console.log("shortCircuited=", result.shortCircuited);
  // eslint-disable-next-line no-console
  console.log("errors=", result.errors.length);
}

void main();
