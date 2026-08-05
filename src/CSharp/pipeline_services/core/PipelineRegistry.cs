using System;
using System.Collections.Generic;

namespace PipelineServices.Core;

public sealed class PipelineRegistry<ContextType>
{
    private readonly Dictionary<string, Action<ContextType>> actions =
        new(StringComparer.Ordinal);

    public void RegisterUnary(string name, Func<ContextType, ContextType> action)
    {
        ValidateName(name);
        ArgumentNullException.ThrowIfNull(action);
        actions[name] = context => action(context);
    }

    public void RegisterAction(string name, Action<ContextType> action)
    {
        ValidateName(name);
        actions[name] = action ?? throw new ArgumentNullException(nameof(action));
    }

    [Obsolete("Register a one-argument Action instead.")]
    public void RegisterAction(string name, StepAction<ContextType> action)
    {
        ValidateName(name);
        ArgumentNullException.ThrowIfNull(action);
        actions[name] = context =>
        {
            throw new InvalidOperationException(
                "Legacy StepAction registry entries must be added directly through Pipeline compatibility overloads");
        };
        legacyActions[name] = action;
    }

    private readonly Dictionary<string, StepAction<ContextType>> legacyActions =
        new(StringComparer.Ordinal);

    public bool HasUnary(string name) => HasAction(name);

    public bool HasAction(string name)
        => name is not null && (actions.ContainsKey(name) || legacyActions.ContainsKey(name));

    public Action<ContextType> GetUnary(string name) => GetAction(name);

    public Action<ContextType> GetAction(string name)
    {
        ArgumentNullException.ThrowIfNull(name);
        if (actions.TryGetValue(name, out Action<ContextType>? action))
        {
            return action;
        }
        throw new InvalidOperationException("Unknown Action: " + name);
    }

    internal bool TryGetLegacyAction(string name, out StepAction<ContextType>? action)
        => legacyActions.TryGetValue(name, out action);

    private static void ValidateName(string name)
    {
        if (string.IsNullOrWhiteSpace(name))
        {
            throw new ArgumentException("name must not be blank", nameof(name));
        }
    }
}
