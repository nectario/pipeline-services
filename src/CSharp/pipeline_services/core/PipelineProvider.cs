using System;
using System.Collections.Generic;
using System.Threading;

namespace PipelineServices.Core;

/// <summary>Creates or selects reusable Pipeline instances without scheduling work.</summary>
public sealed class PipelineProvider<ContextType>
{
    private readonly PipelineProviderMode mode;
    private readonly Func<Pipeline<ContextType>>? pipelineFactory;
    private readonly Pipeline<ContextType>? singletonPipeline;
    private readonly IReadOnlyList<Pipeline<ContextType>> pooledPipelines;
    private long nextSelection = -1L;

    private PipelineProvider(
        PipelineProviderMode mode,
        Func<Pipeline<ContextType>>? pipelineFactory,
        Pipeline<ContextType>? singletonPipeline,
        IReadOnlyList<Pipeline<ContextType>>? pooledPipelines)
    {
        this.mode = mode;
        this.pipelineFactory = pipelineFactory;
        this.singletonPipeline = singletonPipeline;
        this.pooledPipelines = pooledPipelines ?? Array.Empty<Pipeline<ContextType>>();
    }

    public static PipelineProvider<ContextType> NewInstancePerEvent(
        Func<Pipeline<ContextType>> factory)
    {
        ArgumentNullException.ThrowIfNull(factory);
        return new PipelineProvider<ContextType>(
            PipelineProviderMode.NewInstancePerEvent,
            factory,
            null,
            null);
    }

    public static PipelineProvider<ContextType> Singleton(
        Pipeline<ContextType> pipeline)
    {
        ArgumentNullException.ThrowIfNull(pipeline);
        return new PipelineProvider<ContextType>(
            PipelineProviderMode.Singleton,
            null,
            pipeline.Freeze(),
            null);
    }

    public static PipelineProvider<ContextType> Singleton(
        Func<Pipeline<ContextType>> factory)
    {
        ArgumentNullException.ThrowIfNull(factory);
        Pipeline<ContextType> pipeline = factory()
            ?? throw new InvalidOperationException("factory returned null");
        return Singleton(pipeline);
    }

    public static PipelineProvider<ContextType> Pooled(
        Func<Pipeline<ContextType>> factory)
        => Pooled(factory, DefaultInstanceCount());

    public static PipelineProvider<ContextType> Pooled(
        Func<Pipeline<ContextType>> factory,
        int instanceCount)
    {
        ArgumentNullException.ThrowIfNull(factory);
        if (instanceCount < 1)
        {
            throw new ArgumentOutOfRangeException(
                nameof(instanceCount),
                "instanceCount must be >= 1");
        }

        List<Pipeline<ContextType>> pipelines = new(instanceCount);
        for (int index = 0; index < instanceCount; index++)
        {
            Pipeline<ContextType> pipeline = factory()
                ?? throw new InvalidOperationException("factory returned null");
            pipelines.Add(pipeline.Freeze());
        }

        return new PipelineProvider<ContextType>(
            PipelineProviderMode.Pooled,
            null,
            null,
            pipelines.AsReadOnly());
    }

    [Obsolete("Use Singleton().")]
    public static PipelineProvider<ContextType> Shared(
        Pipeline<ContextType> pipeline)
        => Singleton(pipeline);

    [Obsolete("Use Singleton().")]
    public static PipelineProvider<ContextType> Shared(
        Func<Pipeline<ContextType>> factory)
        => Singleton(factory);

    [Obsolete("Use NewInstancePerEvent().")]
    public static PipelineProvider<ContextType> PerRun(
        Func<Pipeline<ContextType>> factory)
        => NewInstancePerEvent(factory);

    public PipelineProviderMode ProviderMode() => mode;

    public PipelineProviderMode Mode => mode;

    public int InstanceCount => mode switch
    {
        PipelineProviderMode.NewInstancePerEvent => 0,
        PipelineProviderMode.Singleton => 1,
        PipelineProviderMode.Pooled => pooledPipelines.Count,
        _ => throw new InvalidOperationException("Unsupported provider mode")
    };

    public Pipeline<ContextType> GetPipeline()
    {
        return mode switch
        {
            PipelineProviderMode.NewInstancePerEvent => CreatePerEventPipeline(),
            PipelineProviderMode.Singleton => singletonPipeline
                ?? throw new InvalidOperationException("singletonPipeline is not set"),
            PipelineProviderMode.Pooled => SelectPooledPipeline(),
            _ => throw new InvalidOperationException("Unsupported provider mode")
        };
    }

    public ContextType Run(ContextType input)
        => GetPipeline().Run(input);

    public PipelineResult<ContextType> RunDetailed(ContextType input)
        => GetPipeline().RunDetailed(input);

    private Pipeline<ContextType> CreatePerEventPipeline()
    {
        Func<Pipeline<ContextType>> factory = pipelineFactory
            ?? throw new InvalidOperationException("pipelineFactory is not set");
        Pipeline<ContextType> pipeline = factory()
            ?? throw new InvalidOperationException("pipelineFactory returned null");
        return pipeline.Freeze();
    }

    private Pipeline<ContextType> SelectPooledPipeline()
    {
        if (pooledPipelines.Count == 0)
        {
            throw new InvalidOperationException("pooledPipelines is empty");
        }
        long selection = Interlocked.Increment(ref nextSelection);
        int index = (int)(selection % pooledPipelines.Count);
        return pooledPipelines[index];
    }

    private static int DefaultInstanceCount()
        => Math.Max(1, Environment.ProcessorCount);
}
