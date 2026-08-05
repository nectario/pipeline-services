using System;

namespace PipelineServices.Core;

public sealed class PipelineRouter<EventType, ContextType>
{
    private readonly Func<EventType, PipelineProvider<ContextType>> route;

    public PipelineRouter(Func<EventType, PipelineProvider<ContextType>> route)
    {
        this.route = route ?? throw new ArgumentNullException(nameof(route));
    }

    public PipelineProvider<ContextType> GetPipelineProvider(EventType eventValue)
        => route(eventValue)
            ?? throw new InvalidOperationException("route returned null");

    public ContextType Run(EventType eventValue, ContextType context)
        => GetPipelineProvider(eventValue).Run(context);
}
