import { Pipeline, StepControl, print_metrics } from "../../index.js";
import { normalize_whitespace, strip } from "./text_steps.js";

async function truncate_at_16(text_value: unknown, control: StepControl): Promise<string> {
  const text_string = String(text_value);
  if (text_string.length <= 16) {
    return text_string;
  }
  control.short_circuit();
  return text_string.slice(0, 16);
}

async function main(): Promise<void> {
  const pipeline = new Pipeline("example05_metrics_post_action", true);
  pipeline.add_action(strip);
  pipeline.add_action(normalize_whitespace);
  pipeline.add_action_named("truncate", truncate_at_16);
  pipeline.add_post_action_named("metrics", print_metrics);

  const result = await pipeline.execute("  Hello   Metrics  ");
  // eslint-disable-next-line no-console
  console.log("output=", result.context);
  // eslint-disable-next-line no-console
  console.log("totalNanos=", result.total_nanos);
  // eslint-disable-next-line no-console
  console.log("timingsCount=", result.timings.length);
}

void main();

