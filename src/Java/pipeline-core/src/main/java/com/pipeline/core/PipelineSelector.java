package com.pipeline.core;

/** Internal selection policy for a fixed collection of reusable Pipeline instances. */
interface PipelineSelector<C> {
  Pipeline<C> selectPipeline();

  int instanceCount();
}
