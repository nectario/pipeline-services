using System;

namespace PipelineServices.Core;

/// <summary>One optional observability seam over the canonical Pipeline runner.</summary>
public interface PipelineObserver
{
    void OnPipelineStarted(string pipelineName) { }

    void OnActionStarted(
        string pipelineName,
        StepPhase phase,
        int actionIndex,
        string actionName) { }

    void OnActionCompleted(
        string pipelineName,
        StepPhase phase,
        int actionIndex,
        string actionName,
        long elapsedNanos) { }

    void OnActionFailed(
        string pipelineName,
        StepPhase phase,
        int actionIndex,
        string actionName,
        Exception exception,
        long elapsedNanos) { }

    void OnShortCircuited(
        string pipelineName,
        StepPhase phase,
        int actionIndex,
        string actionName) { }

    void OnPipelineCompleted(
        string pipelineName,
        bool shortCircuited,
        int errorCount,
        long elapsedNanos) { }
}

internal sealed class NoopPipelineObserver : PipelineObserver
{
    internal static readonly NoopPipelineObserver Instance = new();
    private NoopPipelineObserver() { }
}
