using System;

using PipelineServices.Config;
using PipelineServices.Core;

namespace PipelineServices.Examples;

public static class Example02JsonLoader
{
    public static void Run()
    {
        PipelineRegistry<string> registry = new PipelineRegistry<string>();
        registry.RegisterUnary("strip", TextActions.Strip);
        registry.RegisterUnary("normalize_whitespace", TextActions.NormalizeWhitespace);

        string jsonText = @"
{
  ""pipeline"": ""example02_json_loader"",
  ""type"": ""unary"",
  ""shortCircuitOnException"": true,
  ""steps"": [
    {""$local"": ""strip""},
    {""$local"": ""normalize_whitespace""}
  ]
}
";

        PipelineJsonLoader loader = new PipelineJsonLoader();
        Pipeline<string> pipeline = loader.LoadString(jsonText, registry);
        string outputValue = pipeline.Run("  Hello   JSON  ");
        Console.WriteLine(outputValue);
    }
}

