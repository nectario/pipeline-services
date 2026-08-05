using System;

namespace PipelineServices.Core;

public sealed class ActionTiming
{
    public ActionTiming(
        StepPhase phase,
        int actionIndex,
        string actionName,
        long elapsedNanos,
        bool success)
    {
        Phase = phase;
        if (actionIndex < 0)
        {
            throw new ArgumentOutOfRangeException(
                nameof(actionIndex),
                "actionIndex must be >= 0");
        }
        ActionIndex = actionIndex;
        ActionName = actionName ?? throw new ArgumentNullException(nameof(actionName));
        ElapsedNanos = elapsedNanos;
        Success = success;
    }

    public StepPhase Phase { get; }

    public int ActionIndex { get; }

    public string ActionName { get; }

    public long ElapsedNanos { get; }

    public bool Success { get; }

    [Obsolete("Use ActionIndex.")]
    public int Index => ActionIndex;
}
