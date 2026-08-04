package com.pipeline.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

/** Creates or selects reusable Pipeline instances without scheduling work. */
public final class PipelineProvider<C> {
  private final PipelineProviderMode mode;
  private final Supplier<? extends Pipeline<C>> pipelineFactory;
  private final Pipeline<C> singletonPipeline;
  private final PipelineSelector<C> pooledSelector;

  private PipelineProvider(
      PipelineProviderMode mode,
      Supplier<? extends Pipeline<C>> pipelineFactory,
      Pipeline<C> singletonPipeline,
      PipelineSelector<C> pooledSelector) {
    this.mode = Objects.requireNonNull(mode, "mode");
    this.pipelineFactory = pipelineFactory;
    this.singletonPipeline = singletonPipeline;
    this.pooledSelector = pooledSelector;
  }

  public static <C> PipelineProvider<C> newInstancePerEvent(
      Supplier<? extends Pipeline<C>> pipelineFactory) {
    return new PipelineProvider<>(
        PipelineProviderMode.NEW_INSTANCE_PER_EVENT,
        Objects.requireNonNull(pipelineFactory, "pipelineFactory"),
        null,
        null);
  }

  public static <C> PipelineProvider<C> singleton(Pipeline<C> pipeline) {
    Pipeline<C> frozenPipeline = Objects.requireNonNull(pipeline, "pipeline").freeze();
    return new PipelineProvider<>(
        PipelineProviderMode.SINGLETON,
        null,
        frozenPipeline,
        null);
  }

  public static <C> PipelineProvider<C> singleton(
      Supplier<? extends Pipeline<C>> pipelineFactory) {
    Objects.requireNonNull(pipelineFactory, "pipelineFactory");
    return singleton(requirePipeline(pipelineFactory.get(), "pipelineFactory.get()"));
  }

  public static <C> PipelineProvider<C> pooled(
      Supplier<? extends Pipeline<C>> pipelineFactory,
      int instanceCount) {
    Objects.requireNonNull(pipelineFactory, "pipelineFactory");
    if (instanceCount < 1) {
      throw new IllegalArgumentException("instanceCount must be >= 1");
    }

    List<Pipeline<C>> pipelines = new ArrayList<>(instanceCount);
    for (int index = 0; index < instanceCount; index++) {
      Pipeline<C> pipeline = requirePipeline(
          pipelineFactory.get(),
          "pipelineFactory.get()");
      pipelines.add(pipeline.freeze());
    }

    return new PipelineProvider<>(
        PipelineProviderMode.POOLED,
        null,
        null,
        new RoundRobinPipelineSelector<>(pipelines));
  }

  public static <C> PipelineProvider<C> pooled(
      Supplier<? extends Pipeline<C>> pipelineFactory) {
    return pooled(pipelineFactory, defaultInstanceCount());
  }

  /** Preview compatibility alias for singleton mode. */
  @Deprecated
  public static <C> PipelineProvider<C> shared(Pipeline<C> pipeline) {
    return singleton(pipeline);
  }

  /** Preview compatibility alias for singleton mode. */
  @Deprecated
  public static <C> PipelineProvider<C> shared(
      Supplier<? extends Pipeline<C>> pipelineFactory) {
    return singleton(pipelineFactory);
  }

  /** Preview compatibility alias for new-instance-per-event mode. */
  @Deprecated
  public static <C> PipelineProvider<C> perRun(
      Supplier<? extends Pipeline<C>> pipelineFactory) {
    return newInstancePerEvent(pipelineFactory);
  }

  public static <C> Builder<C> builder(
      Supplier<? extends Pipeline<C>> pipelineFactory) {
    return new Builder<>(pipelineFactory);
  }

  public PipelineProviderMode mode() {
    return mode;
  }

  /** Number of retained instances: zero for per-event, one for singleton, pool size for pooled. */
  public int instanceCount() {
    return switch (mode) {
      case NEW_INSTANCE_PER_EVENT -> 0;
      case SINGLETON -> 1;
      case POOLED -> pooledSelector.instanceCount();
    };
  }

  public Pipeline<C> getPipeline() {
    return switch (mode) {
      case NEW_INSTANCE_PER_EVENT ->
          requirePipeline(pipelineFactory.get(), "pipelineFactory.get()").freeze();
      case SINGLETON -> singletonPipeline;
      case POOLED -> pooledSelector.selectPipeline();
    };
  }

  public C run(C context) {
    return getPipeline().run(context);
  }

  public PipelineResult<C> runDetailed(C context) {
    return getPipeline().runDetailed(context);
  }

  private static <C> Pipeline<C> requirePipeline(
      Pipeline<C> pipeline,
      String source) {
    return Objects.requireNonNull(pipeline, source + " returned null");
  }

  private static int defaultInstanceCount() {
    return Math.max(1, Runtime.getRuntime().availableProcessors());
  }

  public static final class Builder<C> {
    private final Supplier<? extends Pipeline<C>> pipelineFactory;
    private PipelineProviderMode mode = PipelineProviderMode.NEW_INSTANCE_PER_EVENT;
    private int instanceCount = defaultInstanceCount();

    private Builder(Supplier<? extends Pipeline<C>> pipelineFactory) {
      this.pipelineFactory = Objects.requireNonNull(pipelineFactory, "pipelineFactory");
    }

    public Builder<C> mode(PipelineProviderMode mode) {
      this.mode = Objects.requireNonNull(mode, "mode");
      return this;
    }

    public Builder<C> instanceCount(int instanceCount) {
      if (instanceCount < 1) {
        throw new IllegalArgumentException("instanceCount must be >= 1");
      }
      this.instanceCount = instanceCount;
      return this;
    }

    public PipelineProvider<C> build() {
      return switch (mode) {
        case NEW_INSTANCE_PER_EVENT ->
            PipelineProvider.newInstancePerEvent(pipelineFactory);
        case SINGLETON ->
            PipelineProvider.singleton(pipelineFactory);
        case POOLED ->
            PipelineProvider.pooled(pipelineFactory, instanceCount);
      };
    }
  }
}
