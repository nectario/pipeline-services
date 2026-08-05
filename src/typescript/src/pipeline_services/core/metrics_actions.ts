import { ActionControl, now_ns } from "./pipeline.js";

/** Preview compatibility metrics Action. New adapters should implement PipelineObserver. */
export async function print_metrics<ContextType>(
  context: ContextType,
  control: ActionControl<ContextType>,
): Promise<ContextType> {
  try {
    const metricsMap: Record<string, unknown> = {};
    metricsMap["pipeline"] = control.pipelineName;
    metricsMap["shortCircuited"] = control.isShortCircuited();
    metricsMap["errorCount"] = control.errors.length;

    const nowNanos = now_ns();
    const startNanos = control.runStartNanos;
    const pipelineNanos =
      startNanos > 0n && nowNanos > startNanos
        ? nowNanos - startNanos
        : 0n;
    metricsMap["pipelineLatencyMs"] =
      Number(pipelineNanos) / 1_000_000.0;

    const actionLatencyMs: Record<string, number> = {};
    for (const timing of control.actionTimings) {
      actionLatencyMs[timing.actionName] =
        Number(timing.elapsedNanos) / 1_000_000.0;
    }
    metricsMap["actionLatencyMs"] = actionLatencyMs;

    // eslint-disable-next-line no-console
    console.log(metricsMap);
  } catch (caughtError) {
    // eslint-disable-next-line no-console
    console.log("metricsError=", String(caughtError));
  }
  return context;
}
