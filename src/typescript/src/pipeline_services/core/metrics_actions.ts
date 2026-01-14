import { StepControl, now_ns } from "./pipeline.js";

export async function print_metrics(ctx: unknown, control: StepControl): Promise<unknown> {
  try {
    const metrics_map: Record<string, unknown> = {};

    metrics_map["pipeline"] = control.pipeline_name;
    metrics_map["shortCircuited"] = control.is_short_circuited();
    metrics_map["errorCount"] = control.errors.length;

    const now_nanos = now_ns();
    const start_nanos = control.run_start_ns;
    let pipeline_nanos = 0n;
    if (start_nanos > 0n && now_nanos > start_nanos) {
      pipeline_nanos = now_nanos - start_nanos;
    }
    metrics_map["pipelineLatencyMs"] = Number(pipeline_nanos) / 1_000_000.0;

    const action_latency_ms: Record<string, number> = {};
    for (const timing of control.timings) {
      action_latency_ms[timing.action_name] = Number(timing.elapsed_nanos) / 1_000_000.0;
    }
    metrics_map["actionLatencyMs"] = action_latency_ms;

    // eslint-disable-next-line no-console
    console.log(metrics_map);
  } catch (caught_error) {
    // eslint-disable-next-line no-console
    console.log("metricsError=", String(caught_error));
  }
  return ctx;
}

