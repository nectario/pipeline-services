using System;

using PipelineServices.Core;

namespace PipelineServices.Examples;

public static class Example01TextClean
{
    public static void Run()
    {
        Pipeline<string> pipeline = new Pipeline<string>("example01_text_clean", shortCircuitOnException: true);
        pipeline.AddAction(TextActions.Strip);
        pipeline.AddAction(TextActions.NormalizeWhitespace);
        pipeline.AddAction("truncate", TextActions.TruncateAt280);

        PipelineResult<string> result = pipeline.Run("  Hello   World  ");
        Console.WriteLine("output=" + result.Context);
        Console.WriteLine("shortCircuited=" + result.ShortCircuited);
        Console.WriteLine("errors=" + result.Errors.Count);
    }
}
