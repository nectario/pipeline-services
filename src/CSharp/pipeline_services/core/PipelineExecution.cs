using System;
using System.Threading;

namespace PipelineServices.Core;

internal interface IPipelineExecutionState
{
    bool ActionExecuting { get; }
    int ActionThreadId { get; }
    void RequestShortCircuit();
}

/// <summary>Execution-scoped control available to Actions declared anywhere.</summary>
public static class PipelineExecution
{
    private sealed class ExecutionFrame
    {
        internal ExecutionFrame(IPipelineExecutionState state, ExecutionFrame? parent)
        {
            State = state;
            Parent = parent;
        }

        internal IPipelineExecutionState State { get; }
        internal ExecutionFrame? Parent { get; }
    }

    private static readonly AsyncLocal<ExecutionFrame?> CurrentFrame = new();

    public static void ShortCircuit()
    {
        ExecutionFrame frame = CurrentFrame.Value
            ?? throw new InvalidOperationException(
                "ShortCircuit() can only be called during an active Pipeline run");

        IPipelineExecutionState state = frame.State;
        if (!state.ActionExecuting || state.ActionThreadId != Environment.CurrentManagedThreadId)
        {
            throw new InvalidOperationException(
                "ShortCircuit() can only be called while the current Pipeline Action body is executing");
        }
        state.RequestShortCircuit();
    }

    internal static IDisposable Open(IPipelineExecutionState state)
    {
        ArgumentNullException.ThrowIfNull(state);
        ExecutionFrame frame = new(state, CurrentFrame.Value);
        CurrentFrame.Value = frame;
        return new ExecutionScope(frame);
    }

    private sealed class ExecutionScope : IDisposable
    {
        private readonly ExecutionFrame expectedFrame;
        private bool disposed;

        internal ExecutionScope(ExecutionFrame expectedFrame)
        {
            this.expectedFrame = expectedFrame;
        }

        public void Dispose()
        {
            if (disposed)
            {
                return;
            }
            disposed = true;

            if (!ReferenceEquals(CurrentFrame.Value, expectedFrame))
            {
                CurrentFrame.Value = null;
                throw new InvalidOperationException(
                    "Pipeline execution scopes were closed out of order");
            }
            CurrentFrame.Value = expectedFrame.Parent;
        }
    }
}
