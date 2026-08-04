package com.pipeline.core;

/** Pipeline instance lifecycle modes shared by every language port. */
public enum PipelineProviderMode {
  NEW_INSTANCE_PER_EVENT,
  SINGLETON,
  POOLED
}
