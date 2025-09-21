export interface RunScope {
  onStepStart(index: number, label: string): void;
  onStepEnd(index: number, label: string, elapsedNanos: number, success: boolean): void;
  onStepError(index: number, label: string, error: unknown): void;
  onJump(fromLabel: string, toLabel: string, delayMillis: number): void;
  onPipelineEnd(success: boolean, elapsedNanos: number, error?: unknown): void;
}

export interface Metrics {
  onPipelineStart(pipelineName: string, runId: string, startLabel?: string | null): RunScope;
}

export class NoopMetrics implements Metrics {
  onPipelineStart(pipelineName: string, runId: string, startLabel?: string | null): RunScope {
    const scope: RunScope = {
      onStepStart() {},
      onStepEnd() {},
      onStepError() {},
      onJump() {},
      onPipelineEnd() {}
    };
    return scope;
  }
}

export type LogLevel = "debug" | "info" | "log" | "warn" | "error";

export class LoggingMetrics implements Metrics {
  private logger: Console;
  private level: LogLevel;

  constructor(logger: Console = console, level: LogLevel = "info") {
    this.logger = logger;
    this.level = level;
  }

  onPipelineStart(pipelineName: string, runId: string, startLabel?: string | null): RunScope {
    const log = this.logger;
    const level = this.level;
    if (typeof (log as any)[level] === "function") {
      (log as any)[level](`pipeline.start name=${pipelineName} runId=${runId} startLabel=${startLabel}`);
    }

    const scope: RunScope = {
      onStepStart: (index, label) => {
        (log as any)[level](`step.start name=${pipelineName} runId=${runId} index=${index} label=${label}`);
      },
      onStepEnd: (index, label, elapsedNanos, success) => {
        const elapsedMillis = (elapsedNanos / 1_000_000).toFixed(3);
        (log as any)[level](`step.end   name=${pipelineName} runId=${runId} index=${index} label=${label} durMs=${elapsedMillis} success=${success}`);
      },
      onStepError: (index, label, error) => {
        log.warn(`step.error name=${pipelineName} runId=${runId} index=${index} label=${label}`, error);
      },
      onJump: (fromLabel, toLabel, delayMillis) => {
        (log as any)[level](`step.jump  name=${pipelineName} runId=${runId} from=${fromLabel} to=${toLabel} delayMs=${delayMillis}`);
      },
      onPipelineEnd: (success, elapsedNanos, error) => {
        const elapsedMillis = (elapsedNanos / 1_000_000).toFixed(3);
        if (error == null) {
          (log as any)[level](`pipeline.end name=${pipelineName} runId=${runId} durMs=${elapsedMillis} success=${success}`);
        } else {
          log.warn(`pipeline.end name=${pipelineName} runId=${runId} durMs=${elapsedMillis} success=${success}`, error);
        }
      }
    };
    return scope;
  }
}

export function nowNanos(): number {
  if (typeof performance !== "undefined" && typeof performance.now === "function") {
    return Math.floor(performance.now() * 1_000_000);
  }
  const dateMillis = Date.now();
  return dateMillis * 1_000_000;
}
