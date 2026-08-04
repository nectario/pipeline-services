package com.pipeline.core;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;

final class RoundRobinPipelineSelector<C> implements PipelineSelector<C> {
  private final List<Pipeline<C>> pipelines;
  private final AtomicLong nextSelection = new AtomicLong();

  RoundRobinPipelineSelector(List<Pipeline<C>> pipelines) {
    this.pipelines = List.copyOf(Objects.requireNonNull(pipelines, "pipelines"));
    if (this.pipelines.isEmpty()) {
      throw new IllegalArgumentException("pipelines must not be empty");
    }
  }

  @Override
  public Pipeline<C> selectPipeline() {
    long selection = nextSelection.getAndIncrement();
    int pipelineIndex = Math.floorMod(selection, pipelines.size());
    return pipelines.get(pipelineIndex);
  }

  @Override
  public int instanceCount() {
    return pipelines.size();
  }
}
