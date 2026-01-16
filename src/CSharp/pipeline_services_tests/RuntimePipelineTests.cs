using System;

using PipelineServices.Core;

using Xunit;

namespace PipelineServices.Tests;

public sealed class RuntimePipelineTests
{
    [Fact]
    public void ContinuesOnErrorWhenShortCircuitFalse()
    {
        RuntimePipeline<string> runtimePipeline = new RuntimePipeline<string>("t", shortCircuitOnException: false, initial: "hi");
        runtimePipeline.AddAction(ThrowBoom);
        Assert.Equal("hi", runtimePipeline.Value());

        runtimePipeline.AddAction(Upper);
        Assert.Equal("HI", runtimePipeline.Value());
    }

    [Fact]
    public void ShortCircuitsOnErrorWhenShortCircuitTrue()
    {
        RuntimePipeline<string> runtimePipeline = new RuntimePipeline<string>("t", shortCircuitOnException: true, initial: "hello");
        runtimePipeline.AddAction(ThrowBoom);
        Assert.Equal("hello", runtimePipeline.Value());

        runtimePipeline.AddAction(Upper);
        Assert.Equal("hello", runtimePipeline.Value());
    }

    [Fact]
    public void ExplicitShortCircuitStopsEarly()
    {
        RuntimePipeline<string> runtimePipeline = new RuntimePipeline<string>("t", shortCircuitOnException: false, initial: "hello");
        runtimePipeline.AddAction(ShortCircuitAndFinish);
        Assert.Equal("FINISH", runtimePipeline.Value());

        runtimePipeline.AddAction(Upper);
        Assert.Equal("FINISH", runtimePipeline.Value());
    }

    [Fact]
    public void FreezeProducesImmutablePipeline()
    {
        RuntimePipeline<string> runtimePipeline = new RuntimePipeline<string>("t", shortCircuitOnException: true, initial: "hello");
        runtimePipeline.AddAction(Upper);
        Pipeline<string> frozen = runtimePipeline.Freeze();

        PipelineResult<string> result = frozen.Execute("hi");
        Assert.Equal("HI", result.Context);
    }

    private static string Upper(string value)
    {
        return value.ToUpperInvariant();
    }

    private static string ThrowBoom(string value, StepControl<string> control)
    {
        throw new InvalidOperationException("boom");
    }

    private static string ShortCircuitAndFinish(string value, StepControl<string> control)
    {
        control.ShortCircuit();
        return "FINISH";
    }
}

