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

        Assert.Equal("Hello JSON", pipeline.Run("  Hello   JSON  "));
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
  ""preActions"": [
    { ""$local"": ""prefix"" }
  ],
  ""actions"": [
    { ""$local"": ""strip"" }
  ],
  ""postActions"": [
    { ""$local"": ""suffix"" }
  ]
}
";

        PipelineJsonLoader loader = new PipelineJsonLoader();
        Pipeline<string> pipeline = loader.LoadString(jsonText, registry);

        Assert.Equal("PRE:  Hi:POST", pipeline.Run("  Hi  "));
    }


    [Fact]
    public void CanonicalSectionsTakePrecedenceOverLegacyAliases()
    {
        PipelineRegistry<string> registry = new PipelineRegistry<string>();
        registry.RegisterUnary("canonical", value => value + "C");
        registry.RegisterUnary("legacy", value => value + "L");

        string jsonText = @"
{
  ""pipeline"": ""t"",
  ""preActions"": [ { ""$local"": ""canonical"" } ],
  ""pre"": [ { ""$local"": ""legacy"" } ],
  ""actions"": [ { ""$local"": ""canonical"" } ],
  ""steps"": [ { ""$local"": ""legacy"" } ],
  ""postActions"": [ { ""$local"": ""canonical"" } ],
  ""post"": [ { ""$local"": ""legacy"" } ]
}
";

        Pipeline<string> pipeline = new PipelineJsonLoader().LoadString(jsonText, registry);

        Assert.Equal("XCCC", pipeline.Run("X"));
    }

    [Theory]
    [InlineData("preActions")]
    [InlineData("actions")]
    [InlineData("postActions")]
    public void DetectsPromptActionsInCanonicalSections(string sectionName)
    {
        PipelineRegistry<string> registry = new PipelineRegistry<string>();
        string jsonText = "{\"pipeline\":\"t\",\"" + sectionName + "\":[{\"$prompt\":\"generate\"}]}";

        InvalidOperationException exception = Assert.Throws<InvalidOperationException>(
            () => new PipelineJsonLoader().LoadString(jsonText, registry));

        Assert.Contains("$prompt", exception.Message, StringComparison.Ordinal);
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
