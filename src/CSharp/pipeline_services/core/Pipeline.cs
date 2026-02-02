using System;
using System.Collections.Generic;
using System.Diagnostics;

namespace PipelineServices.Core;

public delegate ContextType OnErrorHandler<ContextType>(ContextType contextValue, PipelineError error);

public sealed class Pipeline<ContextType>
{
    private readonly string name;
    private readonly bool shortCircuitOnException;

    private OnErrorHandler<ContextType> onError;

    private readonly List<RegisteredAction> preActions;
    private readonly List<RegisteredAction> actions;
    private readonly List<RegisteredAction> postActions;

    public Pipeline(string name)
        : this(name, true)
    {
    }

    public Pipeline(string name, bool shortCircuitOnException)
    {
        this.name = name ?? throw new ArgumentNullException(nameof(name));
        this.shortCircuitOnException = shortCircuitOnException;
        onError = DefaultOnError;

        preActions = new List<RegisteredAction>();
        actions = new List<RegisteredAction>();
        postActions = new List<RegisteredAction>();
    }

    public string Name()
    {
        return name;
    }

    public bool ShortCircuitOnException()
    {
        return shortCircuitOnException;
    }

    public int Size()
    {
        return actions.Count;
    }

    public Pipeline<ContextType> OnError(OnErrorHandler<ContextType>? handler)
    {
        onError = handler ?? DefaultOnError;
        return this;
    }

    public Pipeline<ContextType> AddPreAction(StepAction<ContextType> action)
    {
        return AddPreAction("", action);
    }

    public Pipeline<ContextType> AddPreAction(string actionName, StepAction<ContextType> action)
    {
        preActions.Add(new RegisteredAction(actionName, action));
        return this;
    }

    public Pipeline<ContextType> AddAction(StepAction<ContextType> action)
    {
        return AddAction("", action);
    }

    public Pipeline<ContextType> AddAction(string actionName, StepAction<ContextType> action)
    {
        actions.Add(new RegisteredAction(actionName, action));
        return this;
    }

    public Pipeline<ContextType> AddPostAction(StepAction<ContextType> action)
    {
        return AddPostAction("", action);
    }

    public Pipeline<ContextType> AddPostAction(string actionName, StepAction<ContextType> action)
    {
        postActions.Add(new RegisteredAction(actionName, action));
        return this;
    }

    public Pipeline<ContextType> AddPreAction(Func<ContextType, ContextType> unaryAction)
    {
        return AddPreAction("", unaryAction);
    }

    public Pipeline<ContextType> AddPreAction(string actionName, Func<ContextType, ContextType> unaryAction)
    {
        return AddPreAction(actionName, new UnaryActionAdapter(unaryAction));
    }

    public Pipeline<ContextType> AddAction(Func<ContextType, ContextType> unaryAction)
    {
        return AddAction("", unaryAction);
    }

    public Pipeline<ContextType> AddAction(string actionName, Func<ContextType, ContextType> unaryAction)
    {
        return AddAction(actionName, new UnaryActionAdapter(unaryAction));
    }

    public Pipeline<ContextType> AddPostAction(Func<ContextType, ContextType> unaryAction)
    {
        return AddPostAction("", unaryAction);
    }

    public Pipeline<ContextType> AddPostAction(string actionName, Func<ContextType, ContextType> unaryAction)
    {
        return AddPostAction(actionName, new UnaryActionAdapter(unaryAction));
    }

    public Pipeline<ContextType> AddPreAction(Func<ContextType, ActionControl<ContextType>, ContextType> actionFunction)
    {
        return AddPreAction("", actionFunction);
    }

    public Pipeline<ContextType> AddPreAction(
        string actionName,
        Func<ContextType, ActionControl<ContextType>, ContextType> actionFunction)
    {
        return AddPreAction(actionName, new StepActionAdapter(actionFunction));
    }

    public Pipeline<ContextType> AddAction(Func<ContextType, ActionControl<ContextType>, ContextType> actionFunction)
    {
        return AddAction("", actionFunction);
    }

    public Pipeline<ContextType> AddAction(string actionName, Func<ContextType, ActionControl<ContextType>, ContextType> actionFunction)
    {
        return AddAction(actionName, new StepActionAdapter(actionFunction));
    }

    public Pipeline<ContextType> AddPostAction(Func<ContextType, ActionControl<ContextType>, ContextType> actionFunction)
    {
        return AddPostAction("", actionFunction);
    }

    public Pipeline<ContextType> AddPostAction(
        string actionName,
        Func<ContextType, ActionControl<ContextType>, ContextType> actionFunction)
    {
        return AddPostAction(actionName, new StepActionAdapter(actionFunction));
    }

    public PipelineResult<ContextType> Run(ContextType input)
    {
        if (input is null)
        {
            throw new ArgumentNullException(nameof(input));
        }

        long runStartTimestamp = Stopwatch.GetTimestamp();
        ContextType contextValue = input;

        DefaultActionControl control = new DefaultActionControl(name, onError);
        control.BeginRun(NanoTime.GetNowNanos());

        contextValue = RunPhase(control, StepPhase.Pre, contextValue, preActions, stopOnShortCircuit: false);
        if (!control.IsShortCircuited())
        {
            contextValue = RunPhase(control, StepPhase.Main, contextValue, actions, stopOnShortCircuit: true);
        }
        contextValue = RunPhase(control, StepPhase.Post, contextValue, postActions, stopOnShortCircuit: false);

        long totalNanos = NanoTime.GetElapsedNanos(runStartTimestamp, Stopwatch.GetTimestamp());
        return new PipelineResult<ContextType>(
            contextValue,
            control.IsShortCircuited(),
            control.Errors(),
            control.ActionTimings(),
            totalNanos);
    }

    public PipelineResult<ContextType> Execute(ContextType input)
    {
        return Run(input);
    }

    private ContextType RunPhase(
        DefaultActionControl control,
        StepPhase phase,
        ContextType startContext,
        List<RegisteredAction> actionList,
        bool stopOnShortCircuit)
    {
        ContextType contextValue = startContext;
        for (int actionIndex = 0; actionIndex < actionList.Count; actionIndex++)
        {
            RegisteredAction registeredAction = actionList[actionIndex];
            string stepName = FormatStepName(phase, actionIndex, registeredAction.Name);
            control.BeginStep(phase, actionIndex, stepName);

            long actionStartTimestamp = Stopwatch.GetTimestamp();
            bool actionSucceeded = true;

            ContextType contextBeforeAction = contextValue;
            try
            {
                ContextType nextContext = registeredAction.Action.Apply(contextValue, control);
                if (nextContext is null)
                {
                    throw new InvalidOperationException("Action returned null: " + stepName);
                }
                contextValue = nextContext;
            }
            catch (Exception exception)
            {
                actionSucceeded = false;
                contextValue = control.RecordError(contextBeforeAction, exception);
                if (shortCircuitOnException)
                {
                    control.ShortCircuit();
                }
            }
            finally
            {
                long elapsedNanos = NanoTime.GetElapsedNanos(actionStartTimestamp, Stopwatch.GetTimestamp());
                control.RecordTiming(elapsedNanos, actionSucceeded);
            }

            if (stopOnShortCircuit && control.IsShortCircuited())
            {
                break;
            }
        }
        return contextValue;
    }

    private static string FormatStepName(StepPhase phase, int index, string labelOrEmpty)
    {
        string prefix = "s";
        if (phase == StepPhase.Pre)
        {
            prefix = "pre";
        }
        else if (phase == StepPhase.Post)
        {
            prefix = "post";
        }

        if (string.IsNullOrWhiteSpace(labelOrEmpty))
        {
            return prefix + index;
        }
        return prefix + index + ":" + labelOrEmpty;
    }

    private static ContextType DefaultOnError(ContextType contextValue, PipelineError error)
    {
        if (error == null)
        {
            return contextValue;
        }
        return contextValue;
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

    private readonly struct RegisteredAction
    {
        public RegisteredAction(string name, StepAction<ContextType> action)
        {
            Name = name ?? "";
            Action = action ?? throw new ArgumentNullException(nameof(action));
        }

        public string Name { get; }

        public StepAction<ContextType> Action { get; }
    }

    private sealed class DefaultActionControl : ActionControl<ContextType>
    {
        private readonly string pipelineName;
        private readonly OnErrorHandler<ContextType> onErrorHandler;

        private readonly List<PipelineError> errors;
        private readonly List<ActionTiming> actionTimings;

        private bool shortCircuited;
        private StepPhase phase;
        private int index;
        private string stepName;
        private long runStartNanos;

        public DefaultActionControl(string pipelineName, OnErrorHandler<ContextType> onErrorHandler)
        {
            this.pipelineName = pipelineName ?? throw new ArgumentNullException(nameof(pipelineName));
            this.onErrorHandler = onErrorHandler ?? throw new ArgumentNullException(nameof(onErrorHandler));

            errors = new List<PipelineError>();
            actionTimings = new List<ActionTiming>();
            shortCircuited = false;
            phase = StepPhase.Main;
            index = 0;
            stepName = "?";
            runStartNanos = 0L;
        }

        public void BeginRun(long startNanos)
        {
            runStartNanos = startNanos;
        }

        public void BeginStep(StepPhase phase, int index, string stepName)
        {
            this.phase = phase;
            this.index = index;
            this.stepName = stepName ?? "?";
        }

        public void RecordTiming(long elapsedNanos, bool success)
        {
            actionTimings.Add(new ActionTiming(phase, index, stepName, elapsedNanos, success));
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
            PipelineError error = new PipelineError(pipelineName, phase, index, stepName, exception);
            errors.Add(error);

            ContextType nextContext = onErrorHandler(contextValue, error);
            if (nextContext is null)
            {
                throw new InvalidOperationException("onError returned null");
            }
            return nextContext;
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
            return runStartNanos;
        }

        public IReadOnlyList<ActionTiming> ActionTimings()
        {
            return actionTimings.AsReadOnly();
        }
    }
}
