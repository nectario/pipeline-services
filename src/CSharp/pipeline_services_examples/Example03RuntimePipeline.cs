using System;

using PipelineServices.Core;

namespace PipelineServices.Examples;

public static class Example03RuntimePipeline
{
    public static void Run()
    {
        RuntimePipeline<string> runtimePipeline =
            new RuntimePipeline<string>("example03_runtime_pipeline", shortCircuitOnException: false, initial: "  Hello   Runtime  ");

        string afterStrip = runtimePipeline.AddAction(TextActions.Strip);
        Console.WriteLine("afterStrip=" + afterStrip);

        string afterNormalize = runtimePipeline.AddAction(TextActions.NormalizeWhitespace);
        Console.WriteLine("afterNormalize=" + afterNormalize);

        Console.WriteLine("runtimeValue=" + runtimePipeline.Value());

        Pipeline<string> frozen = runtimePipeline.Freeze();
        PipelineResult<string> result = frozen.Execute("  Hello   Frozen  ");
        Console.WriteLine("frozenValue=" + result.Context);
    }
}

