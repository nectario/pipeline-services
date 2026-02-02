using System;

namespace PipelineServices.Core;

/// <summary>
/// Backwards-compatible alias for <see cref="ActionControl{ContextType}"/>.
/// </summary>
[Obsolete("Renamed to ActionControl<ContextType>.")]
public interface StepControl<ContextType>
    : ActionControl<ContextType>
{
}
