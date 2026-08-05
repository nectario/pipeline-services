import { Pipeline, now_ns } from "../../index.js";
import { append_marker, strip, to_lower } from "./text_steps.js";

async function main(): Promise<void> {
  const pipeline = new Pipeline<string>("benchmark01_pipeline_run", true)
    .addAction(strip)
    .addAction(to_lower)
    .addAction(append_marker);

  const inputValue = "  Hello Benchmark  ";
  const warmupIterations = 1_000;
  const iterations = 10_000;

  for (let index = 0; index < warmupIterations; index += 1) {
    await pipeline.run(inputValue);
  }

  let totalPipelineNanos = 0n;
  const actionTotals = new Map<string, bigint>();
  const actionCounts = new Map<string, number>();
  const actionNames: Array<string> = [];

  const startNanos = now_ns();
  for (let index = 0; index < iterations; index += 1) {
    const result = await pipeline.runDetailed(inputValue);
    totalPipelineNanos += result.totalNanos;

    for (const timing of result.actionTimings) {
      if (actionTotals.has(timing.actionName)) {
        actionTotals.set(
          timing.actionName,
          actionTotals.get(timing.actionName)! + timing.elapsedNanos,
        );
        actionCounts.set(
          timing.actionName,
          (actionCounts.get(timing.actionName) ?? 0) + 1,
        );
      } else {
        actionTotals.set(timing.actionName, timing.elapsedNanos);
        actionCounts.set(timing.actionName, 1);
        actionNames.push(timing.actionName);
      }
    }
  }
  const wallNanos = now_ns() - startNanos;

  // eslint-disable-next-line no-console
  console.log("iterations=", iterations);
  // eslint-disable-next-line no-console
  console.log("wallMs=", Number(wallNanos) / 1_000_000.0);
  // eslint-disable-next-line no-console
  console.log(
    "avgPipelineUs=",
    Number(totalPipelineNanos) / Number(iterations) / 1_000.0,
  );
  // eslint-disable-next-line no-console
  console.log("avgActionUs=");
  for (const actionName of actionNames) {
    const nanosTotal = actionTotals.get(actionName)!;
    const countTotal = actionCounts.get(actionName)!;
    // eslint-disable-next-line no-console
    console.log(
      "  ",
      actionName,
      "=",
      Number(nanosTotal) / Number(countTotal) / 1_000.0,
    );
  }
}

void main();
