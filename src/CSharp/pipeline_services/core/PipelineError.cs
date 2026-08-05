using System;

namespace PipelineServices.Core;

public sealed class PipelineError
{
    public PipelineError(
        string pipelineName,
        StepPhase phase,
        int actionIndex,
        string actionName,
        Exception exception)
    {
        PipelineName = pipelineName ?? throw new ArgumentNullException(nameof(pipelineName));
        Phase = phase;
        if (actionIndex < 0)
        {
            throw new ArgumentOutOfRangeException(
                nameof(actionIndex),
                "actionIndex must be >= 0");
        }
        ActionIndex = actionIndex;
        ActionName = actionName ?? throw new ArgumentNullException(nameof(actionName));
        Exception = exception ?? throw new ArgumentNullException(nameof(exception));
    }

    public string PipelineName { get; }

    public StepPhase Phase { get; }

    public int ActionIndex { get; }

    public string ActionName { get; }

    public Exception Exception { get; }

    [Obsolete("Use ActionIndex.")]
    public int StepIndex => ActionIndex;

    [Obsolete("Use ActionName.")]
    public string StepName => ActionName;
}
