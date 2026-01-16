using System;
using System.Collections.Generic;

namespace PipelineServices.Core;

public sealed class PipelineRegistry<ContextType>
{
    private readonly Dictionary<string, Func<ContextType, ContextType>> unaryActions;
    private readonly Dictionary<string, StepAction<ContextType>> actions;

    public PipelineRegistry()
    {
        unaryActions = new Dictionary<string, Func<ContextType, ContextType>>(StringComparer.Ordinal);
        actions = new Dictionary<string, StepAction<ContextType>>(StringComparer.Ordinal);
    }

    public void RegisterUnary(string name, Func<ContextType, ContextType> action)
    {
        if (string.IsNullOrWhiteSpace(name))
        {
            throw new ArgumentException("name must not be blank", nameof(name));
        }
        unaryActions[name] = action ?? throw new ArgumentNullException(nameof(action));
    }

    public void RegisterAction(string name, StepAction<ContextType> action)
    {
        if (string.IsNullOrWhiteSpace(name))
        {
            throw new ArgumentException("name must not be blank", nameof(name));
        }
        actions[name] = action ?? throw new ArgumentNullException(nameof(action));
    }

    public bool HasUnary(string name)
    {
        return name != null && unaryActions.ContainsKey(name);
    }

    public bool HasAction(string name)
    {
        return name != null && actions.ContainsKey(name);
    }

    public Func<ContextType, ContextType> GetUnary(string name)
    {
        if (name == null)
        {
            throw new ArgumentNullException(nameof(name));
        }
        if (!unaryActions.TryGetValue(name, out Func<ContextType, ContextType>? action))
        {
            throw new InvalidOperationException("Unknown unary action: " + name);
        }
        return action;
    }

    public StepAction<ContextType> GetAction(string name)
    {
        if (name == null)
        {
            throw new ArgumentNullException(nameof(name));
        }
        if (!actions.TryGetValue(name, out StepAction<ContextType>? action))
        {
            throw new InvalidOperationException("Unknown step action: " + name);
        }
        return action;
    }
}

