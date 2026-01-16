using System;

using PipelineServices.Core;
using PipelineServices.Core.Actions;

namespace PipelineServices.Examples;

public static class Example05MetricsPostAction
{
    public static void Run()
    {
        Pipeline<string> pipeline = new Pipeline<string>("example05_metrics_post_action", shortCircuitOnException: true);
        pipeline.AddAction(TextActions.Strip);
        pipeline.AddAction(TextActions.NormalizeWhitespace);
        pipeline.AddPostAction(new MetricsOutputAction<string>());

        string outputValue = pipeline.Run("  Hello   Metrics  ");
        Console.WriteLine(outputValue);
    }
}

