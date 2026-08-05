import { PipelineProvider } from "./pipeline_provider.js";

export class PipelineRouter<EventType, ContextType = unknown> {
  constructor(
    private readonly route: (
      event: EventType,
    ) => PipelineProvider<ContextType>,
  ) {
    if (typeof route !== "function") {
      throw new TypeError("route must be callable");
    }
  }

  getPipelineProvider(
    event: EventType,
  ): PipelineProvider<ContextType> {
    const provider = this.route(event);
    if (!(provider instanceof PipelineProvider)) {
      throw new TypeError("route returned an invalid PipelineProvider");
    }
    return provider;
  }

  get_pipeline_provider(
    event: EventType,
  ): PipelineProvider<ContextType> {
    return this.getPipelineProvider(event);
  }
}
