package com.pipeline.core;

import java.util.Objects;
import java.util.function.Supplier;

/**
 * Provides pipeline execution with an explicit lifecycle policy:
 * <ul>
 *   <li>{@code shared}: one pipeline instance reused across runs (must be safe for concurrent use)</li>
 *   <li>{@code pooled}: pipeline instances are reused but never shared concurrently</li>
 *   <li>{@code perRun}: a new pipeline instance is created per run</li>
 * </ul>
 */
public final class PipelineProvider<C> {
  public enum Mode {
    SHARED,
    POOLED,
    PER_RUN
  }

  private final Mode mode;
  private final Pipeline<C> sharedPipeline;
  private final ActionPool<Pipeline<C>> pipelinePool;
  private final Supplier<? extends Pipeline<C>> pipelineFactory;

  private PipelineProvider(
      Mode mode,
      Pipeline<C> sharedPipeline,
      ActionPool<Pipeline<C>> pipelinePool,
      Supplier<? extends Pipeline<C>> pipelineFactory
  ) {
    this.mode = Objects.requireNonNull(mode, "mode");
    this.sharedPipeline = sharedPipeline;
    this.pipelinePool = pipelinePool;
    this.pipelineFactory = pipelineFactory;
  }

  public static <C> PipelineProvider<C> shared(Pipeline<C> pipeline) {
    Pipeline<C> nonNullPipeline = Objects.requireNonNull(pipeline, "pipeline");
    return new PipelineProvider<>(Mode.SHARED, nonNullPipeline, null, null);
  }

  public static <C> PipelineProvider<C> shared(Supplier<? extends Pipeline<C>> factory) {
    Objects.requireNonNull(factory, "factory");
    Pipeline<C> pipeline = Objects.requireNonNull(factory.get(), "factory.get()");
    return shared(pipeline);
  }

  public static <C> PipelineProvider<C> pooled(Supplier<? extends Pipeline<C>> factory) {
    return pooled(factory, defaultPoolMax());
  }

  public static <C> PipelineProvider<C> pooled(Supplier<? extends Pipeline<C>> factory, int poolMax) {
    Objects.requireNonNull(factory, "factory");
    ActionPool<Pipeline<C>> pool = new ActionPool<>(poolMax, () -> Objects.requireNonNull(factory.get(), "factory.get()"));
    return new PipelineProvider<>(Mode.POOLED, null, pool, null);
  }

  public static <C> PipelineProvider<C> perRun(Supplier<? extends Pipeline<C>> factory) {
    Objects.requireNonNull(factory, "factory");
    return new PipelineProvider<>(Mode.PER_RUN, null, null, factory);
  }

  public Mode mode() {
    return mode;
  }

  public PipelineResult<C> run(C input) {
    return switch (mode) {
      case SHARED -> Objects.requireNonNull(sharedPipeline, "sharedPipeline").run(input);
      case POOLED -> runPooled(input);
      case PER_RUN -> Objects.requireNonNull(Objects.requireNonNull(pipelineFactory, "pipelineFactory").get(), "pipelineFactory.get()").run(input);
    };
  }

  private PipelineResult<C> runPooled(C input) {
    Pipeline<C> borrowedPipeline = Objects.requireNonNull(pipelinePool, "pipelinePool").borrow();
    try {
      return borrowedPipeline.run(input);
    } finally {
      pipelinePool.release(borrowedPipeline);
    }
  }

  private static int defaultPoolMax() {
    int processors = Runtime.getRuntime().availableProcessors();
    int computed = processors * 8;
    return Math.min(256, Math.max(1, computed));
  }
}

