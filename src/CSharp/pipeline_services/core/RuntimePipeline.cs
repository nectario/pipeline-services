using System;
using System.Collections.Generic;

namespace PipelineServices.Core;

[Obsolete("Construct a Pipeline directly. RuntimePipeline is retained only as a compatibility helper.")]
public sealed class RuntimePipeline<ContextType>
{
    private readonly string name;
    private readonly bool shortCircuitOnException;
    private readonly List<RecordedAction> preActions = new();
    private readonly List<RecordedAction> actions = new();
    private readonly List<RecordedAction> postActions = new();

    private ContextType current;
    private bool ended;

    public RuntimePipeline(
        string name,
        bool shortCircuitOnException,
        ContextType initial)
    {
        this.name = name ?? throw new ArgumentNullException(nameof(name));
        this.shortCircuitOnException = shortCircuitOnException;
        current = initial;
    }

    public PipelineResult<ContextType>? LastResult { get; private set; }

    public ContextType AddPreAction(Func<ContextType, ContextType> action)
        => AddAndExecute(
            StepPhase.Pre,
            preActions,
            new UnaryRecordedAction(action));

    public ContextType AddAction(Func<ContextType, ContextType> action)
        => AddAndExecute(
            StepPhase.Main,
            actions,
            new UnaryRecordedAction(action));

    public ContextType AddPostAction(Func<ContextType, ContextType> action)
        => AddAndExecute(
            StepPhase.Post,
            postActions,
            new UnaryRecordedAction(action));

    public ContextType AddPreAction(StepAction<ContextType> action)
        => AddAndExecute(
            StepPhase.Pre,
            preActions,
            new StepRecordedAction(action));

    public ContextType AddAction(StepAction<ContextType> action)
        => AddAndExecute(
            StepPhase.Main,
            actions,
            new StepRecordedAction(action));

    public ContextType AddPostAction(StepAction<ContextType> action)
        => AddAndExecute(
            StepPhase.Post,
            postActions,
            new StepRecordedAction(action));

    public ContextType AddPreAction(
        Func<ContextType, ActionControl<ContextType>, ContextType> action)
        => AddAndExecute(
            StepPhase.Pre,
            preActions,
            new ControlRecordedAction(action));

    public ContextType AddAction(
        Func<ContextType, ActionControl<ContextType>, ContextType> action)
        => AddAndExecute(
            StepPhase.Main,
            actions,
            new ControlRecordedAction(action));

    public ContextType AddPostAction(
        Func<ContextType, ActionControl<ContextType>, ContextType> action)
        => AddAndExecute(
            StepPhase.Post,
            postActions,
            new ControlRecordedAction(action));

    public ContextType Value() => current;

    public void Reset(ContextType initial)
    {
        current = initial;
        ended = false;
        LastResult = null;
    }

    public void ClearRecorded()
    {
        preActions.Clear();
        actions.Clear();
        postActions.Clear();
    }

    public int RecordedPreActionCount() => preActions.Count;

    public int RecordedActionCount() => actions.Count;

    public int RecordedPostActionCount() => postActions.Count;

    public Pipeline<ContextType> ToImmutable()
    {
        Pipeline<ContextType> pipeline = new(name, shortCircuitOnException);
        AddAll(pipeline, StepPhase.Pre, preActions);
        AddAll(pipeline, StepPhase.Main, actions);
        AddAll(pipeline, StepPhase.Post, postActions);
        return pipeline.Freeze();
    }

    public Pipeline<ContextType> Freeze() => ToImmutable();

    private ContextType AddAndExecute(
        StepPhase phase,
        List<RecordedAction> destination,
        RecordedAction action)
    {
        ArgumentNullException.ThrowIfNull(action);
        if (ended)
        {
            return current;
        }

        destination.Add(action);
        Pipeline<ContextType> temporary = new(
            name + ":runtime",
            shortCircuitOnException);
        action.AddTo(temporary, phase);

        PipelineResult<ContextType> result = temporary.RunDetailed(current);
        current = result.Context;
        ended = result.ShortCircuited;
        LastResult = result;
        return current;
    }

    private static void AddAll(
        Pipeline<ContextType> pipeline,
        StepPhase phase,
        IEnumerable<RecordedAction> recordedActions)
    {
        foreach (RecordedAction action in recordedActions)
        {
            action.AddTo(pipeline, phase);
        }
    }

    private abstract class RecordedAction
    {
        internal abstract void AddTo(
            Pipeline<ContextType> pipeline,
            StepPhase phase);
    }

    private sealed class UnaryRecordedAction : RecordedAction
    {
        private readonly Func<ContextType, ContextType> action;

        internal UnaryRecordedAction(Func<ContextType, ContextType> action)
        {
            this.action = action ?? throw new ArgumentNullException(nameof(action));
        }

        internal override void AddTo(
            Pipeline<ContextType> pipeline,
            StepPhase phase)
        {
            Action<ContextType> canonical = context => action(context);
            switch (phase)
            {
                case StepPhase.Pre:
                    pipeline.AddPreAction(canonical);
                    break;
                case StepPhase.Post:
                    pipeline.AddPostAction(canonical);
                    break;
                default:
                    pipeline.AddAction(canonical);
                    break;
            }
        }
    }

    private sealed class StepRecordedAction : RecordedAction
    {
        private readonly StepAction<ContextType> action;

        internal StepRecordedAction(StepAction<ContextType> action)
        {
            this.action = action ?? throw new ArgumentNullException(nameof(action));
        }

        [Obsolete]
        internal override void AddTo(
            Pipeline<ContextType> pipeline,
            StepPhase phase)
        {
            switch (phase)
            {
                case StepPhase.Pre:
                    pipeline.AddPreAction(action);
                    break;
                case StepPhase.Post:
                    pipeline.AddPostAction(action);
                    break;
                default:
                    pipeline.AddAction(action);
                    break;
            }
        }
    }

    private sealed class ControlRecordedAction : RecordedAction
    {
        private readonly Func<ContextType, ActionControl<ContextType>, ContextType> action;

        internal ControlRecordedAction(
            Func<ContextType, ActionControl<ContextType>, ContextType> action)
        {
            this.action = action ?? throw new ArgumentNullException(nameof(action));
        }

        [Obsolete]
        internal override void AddTo(
            Pipeline<ContextType> pipeline,
            StepPhase phase)
        {
            switch (phase)
            {
                case StepPhase.Pre:
                    pipeline.AddPreAction(action);
                    break;
                case StepPhase.Post:
                    pipeline.AddPostAction(action);
                    break;
                default:
                    pipeline.AddAction(action);
                    break;
            }
        }
    }
}
