package com.pipeline.core;

@FunctionalInterface
public interface StepAction<C> {
  C apply(C ctx, StepControl<C> control);
}

