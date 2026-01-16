using System;

namespace PipelineServices.Core;

public sealed class PipelineError
{
    public PipelineError(string pipelineName, StepPhase phase, int stepIndex, string stepName, Exception exception)
    {
        PipelineName = pipelineName ?? throw new ArgumentNullException(nameof(pipelineName));
        Phase = phase;

        if (stepIndex < 0)
        {
            throw new ArgumentOutOfRangeException(nameof(stepIndex), "stepIndex must be >= 0");
        }
        StepIndex = stepIndex;

        StepName = stepName ?? throw new ArgumentNullException(nameof(stepName));
        Exception = exception ?? throw new ArgumentNullException(nameof(exception));
    }

    public string PipelineName { get; }

    public StepPhase Phase { get; }

    public int StepIndex { get; }

    public string StepName { get; }

    public Exception Exception { get; }
}

