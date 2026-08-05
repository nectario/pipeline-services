import os from "node:os";

import { Pipeline, PipelineResult } from "./pipeline.js";

export enum PipelineProviderMode {
  NEW_INSTANCE_PER_EVENT = "newInstancePerEvent",
  SINGLETON = "singleton",
  POOLED = "pooled",
}

export function defaultInstanceCount(): number {
  return Math.max(1, os.cpus().length || 1);
}

/** @deprecated Preview alias for defaultInstanceCount(). */
export function default_pool_max(): number {
  return defaultInstanceCount();
}

type PipelineFactory<ContextType> = () => Pipeline<ContextType>;

export class PipelineProvider<ContextType = unknown> {
  private nextSelection = 0;

  private constructor(
    public readonly mode: PipelineProviderMode,
    private readonly factory: PipelineFactory<ContextType> | null,
    private readonly singletonPipeline: Pipeline<ContextType> | null,
    private readonly pooledPipelines: ReadonlyArray<Pipeline<ContextType>>,
  ) {}

  static newInstancePerEvent<ContextType>(
    factory: PipelineFactory<ContextType>,
  ): PipelineProvider<ContextType> {
    if (typeof factory !== "function") {
      throw new TypeError("factory must be callable");
    }
    return new PipelineProvider(
      PipelineProviderMode.NEW_INSTANCE_PER_EVENT,
      factory,
      null,
      [],
    );
  }

  static singleton<ContextType>(
    pipelineOrFactory:
      | Pipeline<ContextType>
      | PipelineFactory<ContextType>,
  ): PipelineProvider<ContextType> {
    const pipeline =
      typeof pipelineOrFactory === "function"
        ? pipelineOrFactory()
        : pipelineOrFactory;
    if (!(pipeline instanceof Pipeline)) {
      throw new TypeError("pipeline must be a Pipeline");
    }
    return new PipelineProvider(
      PipelineProviderMode.SINGLETON,
      null,
      pipeline.freeze(),
      [],
    );
  }

  static pooled<ContextType>(
    factory: PipelineFactory<ContextType>,
    instanceCount: number = defaultInstanceCount(),
  ): PipelineProvider<ContextType> {
    if (typeof factory !== "function") {
      throw new TypeError("factory must be callable");
    }
    if (!Number.isInteger(instanceCount) || instanceCount < 1) {
      throw new RangeError("instanceCount must be >= 1");
    }

    const pipelines: Array<Pipeline<ContextType>> = [];
    for (let index = 0; index < instanceCount; index += 1) {
      const pipeline = factory();
      if (!(pipeline instanceof Pipeline)) {
        throw new TypeError("factory returned an invalid Pipeline");
      }
      pipelines.push(pipeline.freeze());
    }
    return new PipelineProvider(
      PipelineProviderMode.POOLED,
      null,
      null,
      Object.freeze(pipelines),
    );
  }

  /** @deprecated Use singleton(). */
  static shared<ContextType>(
    pipelineOrFactory:
      | Pipeline<ContextType>
      | PipelineFactory<ContextType>,
  ): PipelineProvider<ContextType> {
    return PipelineProvider.singleton(pipelineOrFactory);
  }

  /** @deprecated Use newInstancePerEvent(). */
  static per_run<ContextType>(
    factory: PipelineFactory<ContextType>,
  ): PipelineProvider<ContextType> {
    return PipelineProvider.newInstancePerEvent(factory);
  }

  get instanceCount(): number {
    switch (this.mode) {
      case PipelineProviderMode.NEW_INSTANCE_PER_EVENT:
        return 0;
      case PipelineProviderMode.SINGLETON:
        return 1;
      case PipelineProviderMode.POOLED:
        return this.pooledPipelines.length;
    }
  }

  get_pipeline(): Pipeline<ContextType> {
    return this.getPipeline();
  }

  getPipeline(): Pipeline<ContextType> {
    switch (this.mode) {
      case PipelineProviderMode.NEW_INSTANCE_PER_EVENT: {
        if (this.factory == null) {
          throw new Error("factory is not set");
        }
        const pipeline = this.factory();
        if (!(pipeline instanceof Pipeline)) {
          throw new TypeError("factory returned an invalid Pipeline");
        }
        return pipeline.freeze();
      }
      case PipelineProviderMode.SINGLETON:
        if (this.singletonPipeline == null) {
          throw new Error("singletonPipeline is not set");
        }
        return this.singletonPipeline;
      case PipelineProviderMode.POOLED: {
        if (this.pooledPipelines.length === 0) {
          throw new Error("pooledPipelines is empty");
        }
        const selected =
          this.pooledPipelines[
            this.nextSelection % this.pooledPipelines.length
          ];
        this.nextSelection += 1;
        return selected;
      }
    }
  }

  async run(context: ContextType): Promise<ContextType> {
    return this.getPipeline().run(context);
  }

  async runDetailed(
    context: ContextType,
  ): Promise<PipelineResult<ContextType>> {
    return this.getPipeline().runDetailed(context);
  }

  async run_detailed(
    context: ContextType,
  ): Promise<PipelineResult<ContextType>> {
    return this.runDetailed(context);
  }
}
