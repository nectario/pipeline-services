using System;
using System.Collections.Generic;
using System.Text.Json;

using PipelineServices.Core;

namespace PipelineServices.Core.Actions;

public sealed class MetricsOutputAction<ContextType> : StepAction<ContextType>
{
    private readonly string name;
    private readonly Func<long> nanoClock;
    private readonly Action<IDictionary<string, object>> sink;

    public MetricsOutputAction()
        : this("Metrics", NanoTime.GetNowNanos, DefaultSink)
    {
    }

    public MetricsOutputAction(Action<IDictionary<string, object>> sink)
        : this("Metrics", NanoTime.GetNowNanos, sink)
    {
    }

    public MetricsOutputAction(string name, Action<IDictionary<string, object>> sink)
        : this(name, NanoTime.GetNowNanos, sink)
    {
    }

    public MetricsOutputAction(string name, Func<long> nanoClock, Action<IDictionary<string, object>> sink)
    {
        this.name = name ?? throw new ArgumentNullException(nameof(name));
        this.nanoClock = nanoClock ?? throw new ArgumentNullException(nameof(nanoClock));
        this.sink = sink ?? throw new ArgumentNullException(nameof(sink));
    }

    public ContextType Apply(ContextType contextValue, StepControl<ContextType> control)
    {
        if (control == null)
        {
            throw new ArgumentNullException(nameof(control));
        }

        long nowNanos = nanoClock();
        long startNanos = control.RunStartNanos();
        double pipelineLatencyMs = (startNanos == 0L) ? 0.0 : (nowNanos - startNanos) / 1_000_000.0;

        Dictionary<string, object> metricsMap = new Dictionary<string, object>(StringComparer.Ordinal);
        metricsMap["name"] = name;
        metricsMap["pipeline"] = control.PipelineName();
        metricsMap["shortCircuited"] = control.IsShortCircuited();
        metricsMap["errorCount"] = control.Errors().Count;
        metricsMap["pipelineLatencyMs"] = pipelineLatencyMs;

        Dictionary<string, double> actionLatencyMs = new Dictionary<string, double>(StringComparer.Ordinal);
        IReadOnlyList<ActionTiming> actionTimings = control.ActionTimings();
        for (int timingIndex = 0; timingIndex < actionTimings.Count; timingIndex++)
        {
            ActionTiming timing = actionTimings[timingIndex];
            actionLatencyMs[timing.ActionName] = timing.ElapsedNanos / 1_000_000.0;
        }
        metricsMap["actionLatencyMs"] = actionLatencyMs;

        sink(metricsMap);
        return contextValue;
    }

    private static void DefaultSink(IDictionary<string, object> metricsMap)
    {
        string json = JsonSerializer.Serialize(metricsMap);
        Console.WriteLine(json);
    }
}

