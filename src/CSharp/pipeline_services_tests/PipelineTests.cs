using System;

using PipelineServices.Core;

using Xunit;

namespace PipelineServices.Tests;

public sealed class PipelineTests
{
    [Fact]
    public void ShortCircuitOnExceptionTrueStopsAndCaptures()
    {
        Pipeline<string> pipeline = new Pipeline<string>("t1", shortCircuitOnException: true);
        pipeline.AddAction(AppendA);
        pipeline.AddAction(ThrowBoom);
        pipeline.AddAction(AppendB);

        PipelineResult<string> result = pipeline.Execute("X");
        Assert.Equal("XA", result.Context);
        Assert.True(result.ShortCircuited);
        Assert.Equal(1, result.Errors.Count);
        Assert.Equal("boom", result.Errors[0].Exception.Message);
    }

    [Fact]
    public void ShortCircuitOnExceptionFalseContinuesAndCaptures()
    {
        Pipeline<string> pipeline = new Pipeline<string>("t2", shortCircuitOnException: false);
        pipeline.AddAction(AppendA);
        pipeline.AddAction(ThrowBoom);
        pipeline.AddAction(AppendB);

        PipelineResult<string> result = pipeline.Execute("X");
        Assert.Equal("XAB", result.Context);
        Assert.False(result.ShortCircuited);
        Assert.Equal(1, result.Errors.Count);
    }

    [Fact]
    public void ExplicitShortCircuitStopsMainActions()
    {
        Pipeline<string> pipeline = new Pipeline<string>("t3", shortCircuitOnException: true);
        pipeline.AddAction(ShortCircuitAndEnd);
        pipeline.AddAction(AppendB);

        PipelineResult<string> result = pipeline.Execute("X");
        Assert.Equal("END", result.Context);
        Assert.True(result.ShortCircuited);
        Assert.Empty(result.Errors);
    }

    [Fact]
    public void PreAlwaysRunsFullyAndSkipsMainIfShortCircuited()
    {
        Pipeline<string> pipeline = new Pipeline<string>("t4", shortCircuitOnException: true);
        pipeline.AddPreAction(PreShortCircuit);
        pipeline.AddPreAction(PreAppendP2);
        pipeline.AddAction(AppendM);
        pipeline.AddPostAction(PostAppendX);

        PipelineResult<string> result = pipeline.Execute("START");
        Assert.Equal("P1P2X", result.Context);
        Assert.True(result.ShortCircuited);
        Assert.Empty(result.Errors);
    }

    [Fact]
    public void PostAlwaysRunsFullyEvenWhenShortCircuitedOnException()
    {
        Pipeline<string> pipeline = new Pipeline<string>("t5", shortCircuitOnException: true);
        pipeline.AddAction(AppendA);
        pipeline.AddPostAction(PostThrowBoom);
        pipeline.AddPostAction(PostAppendX);

        PipelineResult<string> result = pipeline.Execute("X");
        Assert.Equal("XAX", result.Context);
        Assert.True(result.ShortCircuited);
        Assert.Equal(1, result.Errors.Count);
    }

    [Fact]
    public void OnErrorCanUpdateContext()
    {
        Pipeline<string> pipeline = new Pipeline<string>("t6", shortCircuitOnException: false);
        pipeline.OnError(AppendErrorMarker);
        pipeline.AddAction(ThrowBoom);
        pipeline.AddAction(AppendB);

        PipelineResult<string> result = pipeline.Execute("X");
        Assert.Equal("X[error]B", result.Context);
        Assert.False(result.ShortCircuited);
        Assert.Equal(1, result.Errors.Count);
    }

    private static string AppendA(string value)
    {
        return value + "A";
    }

    private static string AppendB(string value)
    {
        return value + "B";
    }

    private static string AppendM(string value)
    {
        return value + "M";
    }

    private static string PostAppendX(string value)
    {
        return value + "X";
    }

    private static string ThrowBoom(string value, StepControl<string> control)
    {
        throw new InvalidOperationException("boom");
    }

    private static string PostThrowBoom(string value, StepControl<string> control)
    {
        throw new InvalidOperationException("boom");
    }

    private static string ShortCircuitAndEnd(string value, StepControl<string> control)
    {
        control.ShortCircuit();
        return "END";
    }

    private static string PreShortCircuit(string value, StepControl<string> control)
    {
        control.ShortCircuit();
        return "P1";
    }

    private static string PreAppendP2(string value)
    {
        return value + "P2";
    }

    private static string AppendErrorMarker(string value, PipelineError error)
    {
        return value + "[error]";
    }
}

