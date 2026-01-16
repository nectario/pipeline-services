using System;
using System.Collections.Generic;

namespace PipelineServices.Core;

public interface StepControl<ContextType>
{
    void ShortCircuit();
    bool IsShortCircuited();

    ContextType RecordError(ContextType contextValue, Exception exception);

    IReadOnlyList<PipelineError> Errors();

    string PipelineName();

    long RunStartNanos();

    IReadOnlyList<ActionTiming> ActionTimings();
}

