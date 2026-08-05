namespace PipelineServices.Core;

/// <summary>An ordinary Pipeline Services Action: one context in, one context out.</summary>
public delegate ContextType Action<ContextType>(ContextType context);
