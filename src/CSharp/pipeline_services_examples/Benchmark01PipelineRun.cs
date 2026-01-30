using System;
using System.Collections.Generic;
using System.Diagnostics;

using PipelineServices.Core;

namespace PipelineServices.Examples;

public static class Benchmark01PipelineRun
{
    public static void Run()
    {
        Pipeline<string> pipeline = new Pipeline<string>("benchmark01_pipeline_run", shortCircuitOnException: true);
        pipeline.AddAction(TextActions.Strip);
        pipeline.AddAction(TextActions.ToLower);
        pipeline.AddAction(TextActions.AppendMarker);

        string inputValue = "  Hello Benchmark  ";
        int warmupIterations = 1000;
        int iterations = 10_000;

        int warmupIndex = 0;
        while (warmupIndex < warmupIterations)
        {
            pipeline.Run(inputValue);
            warmupIndex += 1;
        }

        long totalPipelineNanos = 0L;
        Dictionary<string, long> actionTotals = new Dictionary<string, long>(StringComparer.Ordinal);
        Dictionary<string, long> actionCounts = new Dictionary<string, long>(StringComparer.Ordinal);

        Stopwatch stopwatch = Stopwatch.StartNew();
        int iterationIndex = 0;
        while (iterationIndex < iterations)
        {
            PipelineResult<string> result = pipeline.Run(inputValue);
            totalPipelineNanos += result.TotalNanos;

            IReadOnlyList<ActionTiming> actionTimings = result.ActionTimings;
            for (int timingIndex = 0; timingIndex < actionTimings.Count; timingIndex++)
            {
                ActionTiming timing = actionTimings[timingIndex];
                if (!actionTotals.ContainsKey(timing.ActionName))
                {
                    actionTotals[timing.ActionName] = 0L;
                    actionCounts[timing.ActionName] = 0L;
                }
                actionTotals[timing.ActionName] += timing.ElapsedNanos;
                actionCounts[timing.ActionName] += 1;
            }

            iterationIndex += 1;
        }
        stopwatch.Stop();

        double wallMs = stopwatch.Elapsed.TotalMilliseconds;
        Console.WriteLine("iterations=" + iterations);
        Console.WriteLine("wallMs=" + wallMs.ToString("F3"));
        Console.WriteLine("avgPipelineUs=" + (totalPipelineNanos / (double)iterations / 1_000.0).ToString("F3"));
        Console.WriteLine("avgActionUs=");

        foreach (KeyValuePair<string, long> entry in actionTotals)
        {
            string actionName = entry.Key;
            long nanosTotal = entry.Value;
            long countTotal = actionCounts[actionName];
            double avgUs = nanosTotal / (double)countTotal / 1_000.0;
            Console.WriteLine("  " + actionName + "=" + avgUs.ToString("F3"));
        }
    }
}
