package com.pipeline.core;

import java.util.Objects;

/** Routes an event to a PipelineProvider without owning provider lifecycle or execution semantics. */
@FunctionalInterface
public interface PipelineRouter<E, C> {
  PipelineProvider<C> getPipelineProvider(E event);

  default Pipeline<C> getPipeline(E event) {
    return Objects.requireNonNull(
        getPipelineProvider(event),
        "getPipelineProvider(event)").getPipeline();
  }

  default C run(E event, C context) {
    return getPipeline(event).run(context);
  }

  default PipelineResult<C> runDetailed(E event, C context) {
    return getPipeline(event).runDetailed(context);
  }
}
