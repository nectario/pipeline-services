using System;

namespace PipelineServices.Core;

public sealed class ActionTiming
{
    public ActionTiming(StepPhase phase, int index, string actionName, long elapsedNanos, bool success)
    {
        Phase = phase;

        if (index < 0)
        {
            throw new ArgumentOutOfRangeException(nameof(index), "index must be >= 0");
        }
        Index = index;

        ActionName = actionName ?? throw new ArgumentNullException(nameof(actionName));
        ElapsedNanos = elapsedNanos;
        Success = success;
    }

    public StepPhase Phase { get; }

    public int Index { get; }

    public string ActionName { get; }

    public long ElapsedNanos { get; }

    public bool Success { get; }
}

