using PipelineServices.Config;
using PipelineServices.Core;

using Xunit;

namespace PipelineServices.Tests;

public sealed class JsonLoaderTests
{
    [Fact]
    public void LoadsUnaryPipelineFromStepsAlias()
    {
        PipelineRegistry<string> registry = new PipelineRegistry<string>();
        registry.RegisterUnary("strip", Strip);
        registry.RegisterUnary("normalize", NormalizeWhitespace);

        string jsonText = @"
{
  ""pipeline"": ""t"",
  ""type"": ""unary"",
  ""shortCircuitOnException"": true,
  ""steps"": [
    { ""$local"": ""strip"" },
    { ""$local"": ""normalize"" }
  ]
}
";

        PipelineJsonLoader loader = new PipelineJsonLoader();
        Pipeline<string> pipeline = loader.LoadString(jsonText, registry);

        string outputValue = pipeline.Run("  Hello   JSON  ");
        Assert.Equal("Hello JSON", outputValue);
    }

    [Fact]
    public void LoadsPreActionsAndPostActions()
    {
        PipelineRegistry<string> registry = new PipelineRegistry<string>();
        registry.RegisterUnary("strip", Strip);
        registry.RegisterUnary("prefix", Prefix);
        registry.RegisterUnary("suffix", Suffix);

        string jsonText = @"
{
  ""pipeline"": ""t"",
  ""type"": ""unary"",
  ""shortCircuitOnException"": true,
  ""pre"": [
    { ""$local"": ""prefix"" }
  ],
  ""actions"": [
    { ""$local"": ""strip"" }
  ],
  ""post"": [
    { ""$local"": ""suffix"" }
  ]
}
";

        PipelineJsonLoader loader = new PipelineJsonLoader();
        Pipeline<string> pipeline = loader.LoadString(jsonText, registry);

        string outputValue = pipeline.Run("  Hi  ");
        Assert.Equal("PRE:Hi:POST", outputValue);
    }

    private static string Strip(string value)
    {
        return value.Trim();
    }

    private static string Prefix(string value)
    {
        return "PRE:" + value;
    }

    private static string Suffix(string value)
    {
        return value + ":POST";
    }

    private static string NormalizeWhitespace(string value)
    {
        return System.Text.RegularExpressions.Regex.Replace(value.Trim(), "\\s+", " ");
    }
}

