using System;
using System.Collections.Generic;

namespace PipelineServices.Core;

public sealed class PipelineResult<ContextType>
{
    public PipelineResult(
        ContextType contextValue,
        bool shortCircuited,
        IReadOnlyList<PipelineError> errors,
        IReadOnlyList<ActionTiming> actionTimings,
        long totalNanos)
    {
        Context = contextValue;
        ShortCircuited = shortCircuited;
        Errors = errors ?? throw new ArgumentNullException(nameof(errors));
        ActionTimings = actionTimings ?? throw new ArgumentNullException(nameof(actionTimings));
        TotalNanos = totalNanos;
    }

    public ContextType Context { get; }

    public bool ShortCircuited { get; }

    public IReadOnlyList<PipelineError> Errors { get; }

    public IReadOnlyList<ActionTiming> ActionTimings { get; }

    public long TotalNanos { get; }

    public bool HasErrors()
    {
        return Errors.Count > 0;
    }
}

