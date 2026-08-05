import {
  Pipeline,
  print_metrics,
  shortCircuit,
} from "../../index.js";
import { normalize_whitespace, strip } from "./text_steps.js";

async function truncateAt16(textValue: string): Promise<string> {
  if (textValue.length <= 16) {
    return textValue;
  }
  shortCircuit();
  return textValue.slice(0, 16);
}

async function main(): Promise<void> {
  const pipeline = new Pipeline<string>(
    "example05_metrics_post_action",
    true,
  )
    .addAction(strip)
    .addAction(normalize_whitespace)
    .addAction(truncateAt16, "truncate")
    .addPostAction(print_metrics, "metrics");

  const result = await pipeline.runDetailed(
    "  Hello   Metrics  ",
  );
  // eslint-disable-next-line no-console
  console.log("output=", result.context);
  // eslint-disable-next-line no-console
  console.log("totalNanos=", result.totalNanos);
  // eslint-disable-next-line no-console
  console.log("timingsCount=", result.actionTimings.length);
}

void main();
