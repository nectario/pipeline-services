import { Pipeline, StepControl } from "../../index.js";
import { normalize_whitespace, strip } from "./text_steps.js";

async function truncate_at_280(text_value: unknown, control: StepControl): Promise<string> {
  const text_string = String(text_value);
  if (text_string.length <= 280) {
    return text_string;
  }
  control.short_circuit();
  return text_string.slice(0, 280);
}

async function main(): Promise<void> {
  const pipeline = new Pipeline("example01_text_clean", true);
  pipeline.add_action(strip);
  pipeline.add_action(normalize_whitespace);
  pipeline.add_action_named("truncate", truncate_at_280);

  const result = await pipeline.run("  Hello   World  ");
  // eslint-disable-next-line no-console
  console.log("output=", result.context);
  // eslint-disable-next-line no-console
  console.log("shortCircuited=", result.short_circuited);
  // eslint-disable-next-line no-console
  console.log("errors=", result.errors.length);
}

void main();
