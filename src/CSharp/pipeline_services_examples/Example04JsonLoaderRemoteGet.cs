using System;

using PipelineServices.Config;
using PipelineServices.Core;

namespace PipelineServices.Examples;

public static class Example04JsonLoaderRemoteGet
{
    public static void Run()
    {
        string jsonText = @"
{
  ""pipeline"": ""example04_json_loader_remote_get"",
  ""type"": ""unary"",
  ""steps"": [
    {
      ""name"": ""remote_get_fixture"",
      ""$remote"": {
        ""endpoint"": ""http://127.0.0.1:8765/remote_hello.txt"",
        ""method"": ""GET"",
        ""timeoutMillis"": 1000,
        ""retries"": 0
      }
    }
  ]
}
";

        PipelineRegistry<string> registry = new PipelineRegistry<string>();
        PipelineJsonLoader loader = new PipelineJsonLoader();
        Pipeline<string> pipeline = loader.LoadString(jsonText, registry);

        string outputValue = pipeline.Run("ignored");
        Console.WriteLine(outputValue);
    }
}

