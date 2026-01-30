import { Pipeline } from "../../index.js";
import { append_marker, strip, to_lower } from "./text_steps.js";
import { now_ns } from "../core/pipeline.js";

async function main(): Promise<void> {
  const pipeline = new Pipeline("benchmark01_pipeline_run", true);
  pipeline.add_action(strip);
  pipeline.add_action(to_lower);
  pipeline.add_action(append_marker);

  const input_value = "  Hello Benchmark  ";
  const warmup_iterations = 1000;
  const iterations = 10_000;

  let warmup_index = 0;
  while (warmup_index < warmup_iterations) {
    await pipeline.run(input_value);
    warmup_index += 1;
  }

  let total_pipeline_nanos = 0n;
  const action_totals = new Map<string, bigint>();
  const action_counts = new Map<string, number>();
  const action_names: Array<string> = [];

  const start_ns = now_ns();
  let iteration_index = 0;
  while (iteration_index < iterations) {
    const result = await pipeline.run(input_value);
    total_pipeline_nanos += result.total_nanos;

    for (const timing of result.timings) {
      if (action_totals.has(timing.action_name)) {
        action_totals.set(timing.action_name, action_totals.get(timing.action_name)! + timing.elapsed_nanos);
        action_counts.set(timing.action_name, (action_counts.get(timing.action_name) ?? 0) + 1);
      } else {
        action_totals.set(timing.action_name, timing.elapsed_nanos);
        action_counts.set(timing.action_name, 1);
        action_names.push(timing.action_name);
      }
    }

    iteration_index += 1;
  }
  const end_ns = now_ns();
  const wall_nanos = end_ns - start_ns;

  // eslint-disable-next-line no-console
  console.log("iterations=", iterations);
  // eslint-disable-next-line no-console
  console.log("wallMs=", Number(wall_nanos) / 1_000_000.0);
  // eslint-disable-next-line no-console
  console.log("avgPipelineUs=", Number(total_pipeline_nanos) / Number(iterations) / 1_000.0);
  // eslint-disable-next-line no-console
  console.log("avgActionUs=");
  for (const action_name of action_names) {
    const nanos_total = action_totals.get(action_name)!;
    const count_total = action_counts.get(action_name)!;
    // eslint-disable-next-line no-console
    console.log("  ", action_name, "=", Number(nanos_total) / Number(count_total) / 1_000.0);
  }
}

void main();
