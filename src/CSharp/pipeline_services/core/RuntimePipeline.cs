using System;
using System.Collections.Generic;

namespace PipelineServices.Core;

public sealed class RuntimePipeline<ContextType>
{
    private readonly string name;
    private readonly bool shortCircuitOnException;

    private ContextType current;
    private bool ended;

    private readonly List<StepAction<ContextType>> pre;
    private readonly List<StepAction<ContextType>> main;
    private readonly List<StepAction<ContextType>> post;

    private int preIndex;
    private int actionIndex;
    private int postIndex;

    private readonly SessionActionControl control;

    public RuntimePipeline(string name, bool shortCircuitOnException, ContextType initial)
    {
        this.name = name ?? throw new ArgumentNullException(nameof(name));
        this.shortCircuitOnException = shortCircuitOnException;
        current = initial;
        ended = false;

        pre = new List<StepAction<ContextType>>();
        main = new List<StepAction<ContextType>>();
        post = new List<StepAction<ContextType>>();

        preIndex = 0;
        actionIndex = 0;
        postIndex = 0;

        control = new SessionActionControl(name);
    }

    public ContextType AddPreAction(StepAction<ContextType> preAction)
    {
        if (ended)
        {
            return current;
        }
        pre.Add(preAction);
        return Apply(preAction, StepPhase.Pre, preIndex++, "pre");
    }

    public ContextType AddAction(StepAction<ContextType> action)
    {
        if (ended)
        {
            return current;
        }
        main.Add(action);
        return Apply(action, StepPhase.Main, actionIndex++, "s");
    }

    public ContextType AddPostAction(StepAction<ContextType> postAction)
    {
        if (ended)
        {
            return current;
        }
        post.Add(postAction);
        return Apply(postAction, StepPhase.Post, postIndex++, "post");
    }

    public ContextType AddPreAction(Func<ContextType, ContextType> unaryAction)
    {
        return AddPreAction(new UnaryActionAdapter(unaryAction));
    }

    public ContextType AddAction(Func<ContextType, ContextType> unaryAction)
    {
        return AddAction(new UnaryActionAdapter(unaryAction));
    }

    public ContextType AddPostAction(Func<ContextType, ContextType> unaryAction)
    {
        return AddPostAction(new UnaryActionAdapter(unaryAction));
    }

    public ContextType AddPreAction(Func<ContextType, ActionControl<ContextType>, ContextType> actionFunction)
    {
        return AddPreAction(new StepActionAdapter(actionFunction));
    }

    public ContextType AddAction(Func<ContextType, ActionControl<ContextType>, ContextType> actionFunction)
    {
        return AddAction(new StepActionAdapter(actionFunction));
    }

    public ContextType AddPostAction(Func<ContextType, ActionControl<ContextType>, ContextType> actionFunction)
    {
        return AddPostAction(new StepActionAdapter(actionFunction));
    }

    public ContextType Value()
    {
        return current;
    }

    public void Reset(ContextType initial)
    {
        current = initial;
        ended = false;
        control.Reset();
    }

    public void ClearRecorded()
    {
        pre.Clear();
        main.Clear();
        post.Clear();
        preIndex = 0;
        actionIndex = 0;
        postIndex = 0;
    }

    public int RecordedPreActionCount()
    {
        return pre.Count;
    }

    public int RecordedActionCount()
    {
        return main.Count;
    }

    public int RecordedPostActionCount()
    {
        return post.Count;
    }

    public Pipeline<ContextType> ToImmutable()
    {
        Pipeline<ContextType> pipeline = new Pipeline<ContextType>(name, shortCircuitOnException);
        foreach (StepAction<ContextType> preAction in pre)
        {
            pipeline.AddPreAction(preAction);
        }
        foreach (StepAction<ContextType> action in main)
        {
            pipeline.AddAction(action);
        }
        foreach (StepAction<ContextType> postAction in post)
        {
            pipeline.AddPostAction(postAction);
        }
        return pipeline;
    }

    public Pipeline<ContextType> Freeze()
    {
        return ToImmutable();
    }

    private ContextType Apply(StepAction<ContextType> action, StepPhase phase, int index, string prefix)
    {
        if (ended)
        {
            return current;
        }

        string stepName = prefix + index;
        control.BeginStep(phase, index, stepName);

        try
        {
            ContextType output = action.Apply(current, control);
            if (output is null)
            {
                throw new InvalidOperationException("Action returned null: " + stepName);
            }
            current = output;
            if (control.IsShortCircuited())
            {
                ended = true;
            }
            return current;
        }
        catch (Exception exception)
        {
            current = control.RecordError(current, exception);
            if (shortCircuitOnException)
            {
                control.ShortCircuit();
                ended = true;
            }
            return current;
        }
    }

    private sealed class UnaryActionAdapter : StepAction<ContextType>
    {
        private readonly Func<ContextType, ContextType> unaryAction;

        public UnaryActionAdapter(Func<ContextType, ContextType> unaryAction)
        {
            this.unaryAction = unaryAction ?? throw new ArgumentNullException(nameof(unaryAction));
        }

        public ContextType Apply(ContextType contextValue, ActionControl<ContextType> control)
        {
            return unaryAction(contextValue);
        }
    }

    private sealed class StepActionAdapter : StepAction<ContextType>
    {
        private readonly Func<ContextType, ActionControl<ContextType>, ContextType> actionFunction;

        public StepActionAdapter(Func<ContextType, ActionControl<ContextType>, ContextType> actionFunction)
        {
            this.actionFunction = actionFunction ?? throw new ArgumentNullException(nameof(actionFunction));
        }

        public ContextType Apply(ContextType contextValue, ActionControl<ContextType> control)
        {
            return actionFunction(contextValue, control);
        }
    }

    private sealed class SessionActionControl : ActionControl<ContextType>
    {
        private readonly string pipelineName;
        private readonly List<PipelineError> errors;

        private bool shortCircuited;
        private StepPhase phase;
        private int index;
        private string stepName;

        public SessionActionControl(string pipelineName)
        {
            this.pipelineName = pipelineName ?? throw new ArgumentNullException(nameof(pipelineName));
            errors = new List<PipelineError>();
            shortCircuited = false;
            phase = StepPhase.Main;
            index = 0;
            stepName = "?";
        }

        public void BeginStep(StepPhase phase, int index, string stepName)
        {
            this.phase = phase;
            this.index = index;
            this.stepName = stepName ?? "?";
        }

        public void Reset()
        {
            shortCircuited = false;
            errors.Clear();
            phase = StepPhase.Main;
            index = 0;
            stepName = "?";
        }

        public void ShortCircuit()
        {
            shortCircuited = true;
        }

        public bool IsShortCircuited()
        {
            return shortCircuited;
        }

        public ContextType RecordError(ContextType contextValue, Exception exception)
        {
            errors.Add(new PipelineError(pipelineName, phase, index, stepName, exception));
            return contextValue;
        }

        public IReadOnlyList<PipelineError> Errors()
        {
            return errors.AsReadOnly();
        }

        public string PipelineName()
        {
            return pipelineName;
        }

        public long RunStartNanos()
        {
            return 0L;
        }

        public IReadOnlyList<ActionTiming> ActionTimings()
        {
            return Array.Empty<ActionTiming>();
        }
    }
}
