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
  private final ActionPoolCache actionPoolCache;

  private PipelineProvider(
      Mode mode,
      Pipeline<C> sharedPipeline,
      ActionPool<Pipeline<C>> pipelinePool,
      Supplier<? extends Pipeline<C>> pipelineFactory,
      ActionPoolCache actionPoolCache
  ) {
    this.mode = Objects.requireNonNull(mode, "mode");
    this.sharedPipeline = sharedPipeline;
    this.pipelinePool = pipelinePool;
    this.pipelineFactory = pipelineFactory;
    this.actionPoolCache = actionPoolCache;
  }

  public static <C> PipelineProvider<C> shared(Pipeline<C> pipeline) {
    Pipeline<C> nonNullPipeline = Objects.requireNonNull(pipeline, "pipeline");
    return new PipelineProvider<>(Mode.SHARED, nonNullPipeline, null, null, null);
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
    return new PipelineProvider<>(Mode.POOLED, null, pool, null, null);
  }

  public static <C> PipelineProvider<C> perRun(Supplier<? extends Pipeline<C>> factory) {
    Objects.requireNonNull(factory, "factory");
    return new PipelineProvider<>(Mode.PER_RUN, null, null, factory, null);
  }

  public Mode mode() {
    return mode;
  }

  public PipelineProvider<C> withPooledLocalActions() {
    return withPooledLocalActions(new ActionPoolCache());
  }

  public PipelineProvider<C> withPooledLocalActions(ActionPoolCache actionPoolCache) {
    Objects.requireNonNull(actionPoolCache, "actionPoolCache");

    if (mode == Mode.SHARED && sharedPipeline != null) {
      sharedPipeline.enablePooledLocalActions(actionPoolCache);
    }

    return new PipelineProvider<>(mode, sharedPipeline, pipelinePool, pipelineFactory, actionPoolCache);
  }

  public PipelineResult<C> run(C input) {
    return switch (mode) {
      case SHARED -> runWithOptionalActionPooling(Objects.requireNonNull(sharedPipeline, "sharedPipeline"), input);
      case POOLED -> runPooled(input);
      case PER_RUN -> {
        Pipeline<C> pipeline = Objects.requireNonNull(
            Objects.requireNonNull(pipelineFactory, "pipelineFactory").get(),
            "pipelineFactory.get()");
        yield runWithOptionalActionPooling(pipeline, input);
      }
    };
  }

  private PipelineResult<C> runPooled(C input) {
    Pipeline<C> borrowedPipeline = Objects.requireNonNull(pipelinePool, "pipelinePool").borrow();
    try {
      return runWithOptionalActionPooling(borrowedPipeline, input);
    } finally {
      pipelinePool.release(borrowedPipeline);
    }
  }

  private PipelineResult<C> runWithOptionalActionPooling(Pipeline<C> pipeline, C input) {
    if (actionPoolCache != null) {
      pipeline.enablePooledLocalActions(actionPoolCache);
    }
    return pipeline.run(input);
  }

  private static int defaultPoolMax() {
    int processors = Runtime.getRuntime().availableProcessors();
    int computed = processors * 8;
    return Math.min(256, Math.max(1, computed));
  }
}
