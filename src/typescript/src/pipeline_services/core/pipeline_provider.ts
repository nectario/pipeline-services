import os from "node:os";

import { Pipeline, PipelineResult } from "./pipeline.js";

export enum PipelineProviderMode {
  SHARED = "shared",
  POOLED = "pooled",
  PER_RUN = "perRun",
}

export function default_pool_max(): number {
  const processor_count = os.cpus().length || 1;
  const computed = processor_count * 8;
  return Math.min(256, Math.max(1, computed));
}

type PipelineFactory = () => Pipeline;

class PipelinePool {
  private max_size: number;
  private created_count: number;
  private factory: PipelineFactory;
  private available: Array<Pipeline>;
  private waiters: Array<(pipeline: Pipeline) => void>;

  constructor(max_size: number, factory: PipelineFactory) {
    if (max_size < 1) {
      throw new RangeError("max_size must be >= 1");
    }
    this.max_size = max_size;
    this.created_count = 0;
    this.factory = factory;
    this.available = [];
    this.waiters = [];
  }

  async borrow(): Promise<Pipeline> {
    const available_pipeline = this.available.pop();
    if (available_pipeline != null) {
      return available_pipeline;
    }

    if (this.created_count < this.max_size) {
      this.created_count += 1;
      return this.factory();
    }

    return new Promise<Pipeline>((resolve) => {
      this.waiters.push(resolve);
    });
  }

  release(pipeline: Pipeline): void {
    const waiter = this.waiters.shift();
    if (waiter != null) {
      waiter(pipeline);
      return;
    }
    this.available.push(pipeline);
  }
}

export class PipelineProvider {
  public mode: PipelineProviderMode;

  private shared_pipeline: Pipeline | null;
  private pool: PipelinePool | null;
  private factory: PipelineFactory | null;

  private constructor(mode: PipelineProviderMode, shared_pipeline: Pipeline | null, pool: PipelinePool | null, factory: PipelineFactory | null) {
    this.mode = mode;
    this.shared_pipeline = shared_pipeline;
    this.pool = pool;
    this.factory = factory;
  }

  static shared(pipeline_or_factory: Pipeline | PipelineFactory): PipelineProvider {
    const shared_pipeline = typeof pipeline_or_factory === "function" ? pipeline_or_factory() : pipeline_or_factory;
    return new PipelineProvider(PipelineProviderMode.SHARED, shared_pipeline, null, null);
  }

  static pooled(factory: PipelineFactory, pool_max: number = default_pool_max()): PipelineProvider {
    const pool = new PipelinePool(pool_max, factory);
    return new PipelineProvider(PipelineProviderMode.POOLED, null, pool, null);
  }

  static per_run(factory: PipelineFactory): PipelineProvider {
    return new PipelineProvider(PipelineProviderMode.PER_RUN, null, null, factory);
  }

  async run(input_value: unknown): Promise<PipelineResult> {
    if (this.mode === PipelineProviderMode.SHARED) {
      if (this.shared_pipeline == null) {
        throw new Error("shared_pipeline is not set");
      }
      return this.shared_pipeline.run(input_value);
    }

    if (this.mode === PipelineProviderMode.POOLED) {
      if (this.pool == null) {
        throw new Error("pool is not set");
      }
      const borrowed_pipeline = await this.pool.borrow();
      try {
        return await borrowed_pipeline.run(input_value);
      } finally {
        this.pool.release(borrowed_pipeline);
      }
    }

    if (this.factory == null) {
      throw new Error("factory is not set");
    }
    const pipeline = this.factory();
    return pipeline.run(input_value);
  }
}

