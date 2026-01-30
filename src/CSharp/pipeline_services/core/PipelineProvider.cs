using System;
using System.Collections.Concurrent;
using System.Threading;

namespace PipelineServices.Core;

public sealed class PipelineProvider<ContextType>
{
    public enum Mode
    {
        Shared,
        Pooled,
        PerRun
    }

    private readonly Mode mode;
    private readonly Pipeline<ContextType>? sharedPipeline;
    private readonly ActionPool<Pipeline<ContextType>>? pipelinePool;
    private readonly Func<Pipeline<ContextType>>? pipelineFactory;

    private PipelineProvider(
        Mode mode,
        Pipeline<ContextType>? sharedPipeline,
        ActionPool<Pipeline<ContextType>>? pipelinePool,
        Func<Pipeline<ContextType>>? pipelineFactory)
    {
        this.mode = mode;
        this.sharedPipeline = sharedPipeline;
        this.pipelinePool = pipelinePool;
        this.pipelineFactory = pipelineFactory;
    }

    public static PipelineProvider<ContextType> Shared(Pipeline<ContextType> pipeline)
    {
        if (pipeline == null)
        {
            throw new ArgumentNullException(nameof(pipeline));
        }
        return new PipelineProvider<ContextType>(Mode.Shared, pipeline, null, null);
    }

    public static PipelineProvider<ContextType> Shared(Func<Pipeline<ContextType>> factory)
    {
        if (factory == null)
        {
            throw new ArgumentNullException(nameof(factory));
        }
        Pipeline<ContextType> pipeline = factory() ?? throw new InvalidOperationException("factory returned null");
        return Shared(pipeline);
    }

    public static PipelineProvider<ContextType> Pooled(Func<Pipeline<ContextType>> factory)
    {
        return Pooled(factory, DefaultPoolMax());
    }

    public static PipelineProvider<ContextType> Pooled(Func<Pipeline<ContextType>> factory, int poolMax)
    {
        if (factory == null)
        {
            throw new ArgumentNullException(nameof(factory));
        }
        ActionPool<Pipeline<ContextType>> pool = new ActionPool<Pipeline<ContextType>>(poolMax, factory);
        return new PipelineProvider<ContextType>(Mode.Pooled, null, pool, null);
    }

    public static PipelineProvider<ContextType> PerRun(Func<Pipeline<ContextType>> factory)
    {
        if (factory == null)
        {
            throw new ArgumentNullException(nameof(factory));
        }
        return new PipelineProvider<ContextType>(Mode.PerRun, null, null, factory);
    }

    public Mode ProviderMode()
    {
        return mode;
    }

    public PipelineResult<ContextType> Run(ContextType input)
    {
        if (mode == Mode.Shared)
        {
            if (sharedPipeline == null)
            {
                throw new InvalidOperationException("sharedPipeline is not set");
            }
            return sharedPipeline.Run(input);
        }

        if (mode == Mode.Pooled)
        {
            if (pipelinePool == null)
            {
                throw new InvalidOperationException("pipelinePool is not set");
            }

            Pipeline<ContextType> borrowedPipeline = pipelinePool.Borrow();
            try
            {
                return borrowedPipeline.Run(input);
            }
            finally
            {
                pipelinePool.Release(borrowedPipeline);
            }
        }

        if (pipelineFactory == null)
        {
            throw new InvalidOperationException("pipelineFactory is not set");
        }

        Pipeline<ContextType> pipeline = pipelineFactory() ?? throw new InvalidOperationException("pipelineFactory returned null");
        return pipeline.Run(input);
    }

    private static int DefaultPoolMax()
    {
        int processorCount = Environment.ProcessorCount;
        int computed = processorCount * 8;
        if (computed < 1)
        {
            computed = 1;
        }
        if (computed > 256)
        {
            computed = 256;
        }
        return computed;
    }

    private sealed class ActionPool<ItemType>
    {
        private readonly int maxSize;
        private readonly Func<ItemType> factory;
        private readonly BlockingCollection<ItemType> available;
        private int createdCount;

        public ActionPool(int maxSize, Func<ItemType> factory)
        {
            if (maxSize < 1)
            {
                throw new ArgumentOutOfRangeException(nameof(maxSize), "maxSize must be >= 1");
            }
            this.maxSize = maxSize;
            this.factory = factory ?? throw new ArgumentNullException(nameof(factory));
            available = new BlockingCollection<ItemType>(new ConcurrentQueue<ItemType>(), maxSize);
            createdCount = 0;
        }

        public ItemType Borrow()
        {
            if (available.TryTake(out ItemType fromQueue))
            {
                return fromQueue;
            }

            int currentCreated = Volatile.Read(ref createdCount);
            while (currentCreated < maxSize)
            {
                int observed = currentCreated;
                int updated = Interlocked.CompareExchange(ref createdCount, observed + 1, observed);
                if (updated == observed)
                {
                    try
                    {
                        ItemType instance = factory();
                        if (instance == null)
                        {
                            throw new InvalidOperationException("factory returned null");
                        }
                        return instance;
                    }
                    catch
                    {
                        Interlocked.Decrement(ref createdCount);
                        throw;
                    }
                }

                currentCreated = Volatile.Read(ref createdCount);
            }

            return available.Take();
        }

        public void Release(ItemType instance)
        {
            if (instance == null)
            {
                return;
            }
            available.TryAdd(instance);
        }
    }
}
