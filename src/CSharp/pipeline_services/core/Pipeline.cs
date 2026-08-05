using System;
using System.Collections.Generic;
using System.Diagnostics;

namespace PipelineServices.Core;

public delegate ContextType OnErrorHandler<ContextType>(ContextType contextValue, PipelineError error);

public sealed class InvalidErrorHandlerException : InvalidOperationException
{
    public InvalidErrorHandlerException(
        string message,
        Exception actionException,
        Exception? handlerException = null)
        : base(message, handlerException ?? actionException)
    {
        ActionException = actionException ?? throw new ArgumentNullException(nameof(actionException));
        HandlerException = handlerException;
    }

    public Exception ActionException { get; }

    public Exception? HandlerException { get; }
}

/// <summary>A reusable single-context Pipeline backed by one immutable plan and one runner.</summary>
public class Pipeline<ContextType>
{
    private readonly object assemblyLock = new();
    private readonly string pipelineName;
    private readonly List<RegisteredAction> preActions = new();
    private readonly List<RegisteredAction> actions = new();
    private readonly List<RegisteredAction> postActions = new();

    private bool shortCircuitOnException;
    private OnErrorHandler<ContextType> errorHandler = DefaultOnError;
    private PipelineObserver observer = NoopPipelineObserver.Instance;
    private PipelinePlan? frozenPlan;

    public Pipeline(string pipelineName)
        : this(pipelineName, true)
    {
    }

    public Pipeline(string pipelineName, bool shortCircuitOnException)
    {
        if (string.IsNullOrWhiteSpace(pipelineName))
        {
            throw new ArgumentException("pipelineName must not be blank", nameof(pipelineName));
        }
        this.pipelineName = pipelineName.Trim();
        this.shortCircuitOnException = shortCircuitOnException;
    }

    public string PipelineName => pipelineName;

    public string Name() => pipelineName;

    public bool ShortCircuitOnException() => PlanOrCurrentShortCircuitPolicy();

    public int Size() => frozenPlan?.Actions.Count ?? actions.Count;

    public bool IsFrozen() => frozenPlan is not null;

    public Pipeline<ContextType> Freeze()
    {
        _ = Plan();
        return this;
    }

    public Pipeline<ContextType> OnError(OnErrorHandler<ContextType>? handler)
    {
        lock (assemblyLock)
        {
            EnsureMutable();
            errorHandler = handler ?? DefaultOnError;
            return this;
        }
    }

    public Pipeline<ContextType> Observer(PipelineObserver? pipelineObserver)
    {
        lock (assemblyLock)
        {
            EnsureMutable();
            observer = pipelineObserver ?? NoopPipelineObserver.Instance;
            return this;
        }
    }

    public Pipeline<ContextType> AddPreAction(Action<ContextType> action)
        => AddPreAction(string.Empty, action);

    public Pipeline<ContextType> AddPreAction(string actionName, Action<ContextType> action)
    {
        Register(preActions, actionName, action);
        return this;
    }

    public Pipeline<ContextType> AddAction(Action<ContextType> action)
        => AddAction(string.Empty, action);

    public Pipeline<ContextType> AddAction(string actionName, Action<ContextType> action)
    {
        Register(actions, actionName, action);
        return this;
    }

    public Pipeline<ContextType> AddPostAction(Action<ContextType> action)
        => AddPostAction(string.Empty, action);

    public Pipeline<ContextType> AddPostAction(string actionName, Action<ContextType> action)
    {
        Register(postActions, actionName, action);
        return this;
    }

    [Obsolete("Use a one-argument Action and PipelineExecution.ShortCircuit().")]
    public Pipeline<ContextType> AddPreAction(StepAction<ContextType> action)
        => AddPreAction(string.Empty, action);

    [Obsolete("Use a one-argument Action and PipelineExecution.ShortCircuit().")]
    public Pipeline<ContextType> AddPreAction(string actionName, StepAction<ContextType> action)
    {
        ArgumentNullException.ThrowIfNull(action);
        Register(
            preActions,
            actionName,
            (context, state) => action.Apply(context, state));
        return this;
    }

    [Obsolete("Use a one-argument Action and PipelineExecution.ShortCircuit().")]
    public Pipeline<ContextType> AddAction(StepAction<ContextType> action)
        => AddAction(string.Empty, action);

    [Obsolete("Use a one-argument Action and PipelineExecution.ShortCircuit().")]
    public Pipeline<ContextType> AddAction(string actionName, StepAction<ContextType> action)
    {
        ArgumentNullException.ThrowIfNull(action);
        Register(
            actions,
            actionName,
            (context, state) => action.Apply(context, state));
        return this;
    }

    [Obsolete("Use a one-argument Action and PipelineExecution.ShortCircuit().")]
    public Pipeline<ContextType> AddPostAction(StepAction<ContextType> action)
        => AddPostAction(string.Empty, action);

    [Obsolete("Use a one-argument Action and PipelineExecution.ShortCircuit().")]
    public Pipeline<ContextType> AddPostAction(string actionName, StepAction<ContextType> action)
    {
        ArgumentNullException.ThrowIfNull(action);
        Register(
            postActions,
            actionName,
            (context, state) => action.Apply(context, state));
        return this;
    }

    [Obsolete("Use a one-argument Action and PipelineExecution.ShortCircuit().")]
    public Pipeline<ContextType> AddPreAction(
        Func<ContextType, ActionControl<ContextType>, ContextType> action)
        => AddPreAction(string.Empty, action);

    [Obsolete("Use a one-argument Action and PipelineExecution.ShortCircuit().")]
    public Pipeline<ContextType> AddPreAction(
        string actionName,
        Func<ContextType, ActionControl<ContextType>, ContextType> action)
    {
        ArgumentNullException.ThrowIfNull(action);
        Register(preActions, actionName, (context, state) => action(context, state));
        return this;
    }

    [Obsolete("Use a one-argument Action and PipelineExecution.ShortCircuit().")]
    public Pipeline<ContextType> AddAction(
        Func<ContextType, ActionControl<ContextType>, ContextType> action)
        => AddAction(string.Empty, action);

    [Obsolete("Use a one-argument Action and PipelineExecution.ShortCircuit().")]
    public Pipeline<ContextType> AddAction(
        string actionName,
        Func<ContextType, ActionControl<ContextType>, ContextType> action)
    {
        ArgumentNullException.ThrowIfNull(action);
        Register(actions, actionName, (context, state) => action(context, state));
        return this;
    }

    [Obsolete("Use a one-argument Action and PipelineExecution.ShortCircuit().")]
    public Pipeline<ContextType> AddPostAction(
        Func<ContextType, ActionControl<ContextType>, ContextType> action)
        => AddPostAction(string.Empty, action);

    [Obsolete("Use a one-argument Action and PipelineExecution.ShortCircuit().")]
    public Pipeline<ContextType> AddPostAction(
        string actionName,
        Func<ContextType, ActionControl<ContextType>, ContextType> action)
    {
        ArgumentNullException.ThrowIfNull(action);
        Register(postActions, actionName, (context, state) => action(context, state));
        return this;
    }

    public void ShortCircuit() => PipelineExecution.ShortCircuit();

    public ContextType Run(ContextType input)
        => PipelineRunner.Run(Plan(), input);

    public PipelineResult<ContextType> RunDetailed(ContextType input)
        => PipelineRunner.RunDetailed(Plan(), input);

    [Obsolete("Use Run() for the final context or RunDetailed() for diagnostics.")]
    public PipelineResult<ContextType> Execute(ContextType input)
        => RunDetailed(input);

    private void Register(
        List<RegisteredAction> destination,
        string actionName,
        Action<ContextType> action)
    {
        ArgumentNullException.ThrowIfNull(action);
        Register(destination, actionName, (context, state) => action(context));
    }

    private void Register(
        List<RegisteredAction> destination,
        string actionName,
        ActionInvoker action)
    {
        ArgumentNullException.ThrowIfNull(action);
        lock (assemblyLock)
        {
            EnsureMutable();
            destination.Add(new RegisteredAction(actionName, action));
        }
    }

    private PipelinePlan Plan()
    {
        PipelinePlan? currentPlan = frozenPlan;
        if (currentPlan is not null)
        {
            return currentPlan;
        }

        lock (assemblyLock)
        {
            currentPlan = frozenPlan;
            if (currentPlan is null)
            {
                currentPlan = new PipelinePlan(
                    pipelineName,
                    shortCircuitOnException,
                    errorHandler,
                    observer,
                    preActions.ToArray(),
                    actions.ToArray(),
                    postActions.ToArray());
                frozenPlan = currentPlan;
            }
            return currentPlan;
        }
    }

    private bool PlanOrCurrentShortCircuitPolicy()
        => frozenPlan?.ShortCircuitOnException ?? shortCircuitOnException;

    private void EnsureMutable()
    {
        if (frozenPlan is not null)
        {
            throw new InvalidOperationException($"Pipeline '{pipelineName}' is frozen");
        }
    }

    private static ContextType DefaultOnError(ContextType context, PipelineError error)
    {
        _ = error;
        return context;
    }

    private delegate ContextType ActionInvoker(ContextType context, ExecutionState state);

    private sealed class RegisteredAction
    {
        internal RegisteredAction(string? name, ActionInvoker invoke)
        {
            Name = string.IsNullOrWhiteSpace(name) ? string.Empty : name.Trim();
            Invoke = invoke ?? throw new ArgumentNullException(nameof(invoke));
        }

        internal string Name { get; }

        internal ActionInvoker Invoke { get; }
    }

    private sealed record PipelinePlan(
        string PipelineName,
        bool ShortCircuitOnException,
        OnErrorHandler<ContextType> ErrorHandler,
        PipelineObserver Observer,
        IReadOnlyList<RegisteredAction> PreActions,
        IReadOnlyList<RegisteredAction> Actions,
        IReadOnlyList<RegisteredAction> PostActions);

    private sealed class ExecutionState : IPipelineExecutionState, ActionControl<ContextType>
    {
        private readonly bool collectTimings;
        private readonly List<PipelineError> errors = new();
        private readonly List<ActionTiming> actionTimings = new();

        internal ExecutionState(
            PipelinePlan plan,
            ContextType context,
            bool collectTimings,
            long runStartTimestamp,
            long runStartNanos)
        {
            Plan = plan;
            Context = context;
            this.collectTimings = collectTimings;
            RunStartTimestamp = runStartTimestamp;
            RunStartNanosValue = runStartNanos;
            Phase = StepPhase.Main;
            ActionName = "?";
        }

        internal PipelinePlan Plan { get; }
        internal ContextType Context { get; set; }
        internal bool ShortCircuited { get; private set; }
        internal StepPhase Phase { get; private set; }
        internal int ActionIndex { get; private set; }
        internal string ActionName { get; private set; }
        internal long RunStartTimestamp { get; }
        internal long RunStartNanosValue { get; }

        public bool ActionExecuting { get; private set; }
        public int ActionThreadId { get; private set; }

        internal void BeginAction(StepPhase phase, int actionIndex, string actionName)
        {
            Phase = phase;
            ActionIndex = actionIndex;
            ActionName = actionName;
        }

        internal void BeginActionExecution()
        {
            if (ActionExecuting)
            {
                throw new InvalidOperationException("A Pipeline Action is already executing");
            }
            ActionThreadId = Environment.CurrentManagedThreadId;
            ActionExecuting = true;
        }

        internal void EndActionExecution()
        {
            if (!ActionExecuting)
            {
                throw new InvalidOperationException("No Pipeline Action is executing");
            }
            ActionExecuting = false;
            ActionThreadId = 0;
        }

        public void RequestShortCircuit() => ShortCircuited = true;

        public void ShortCircuit() => RequestShortCircuit();

        public bool IsShortCircuited() => ShortCircuited;

        public ContextType RecordError(ContextType contextValue, Exception exception)
            => HandleActionFailure(contextValue, exception);

        internal ContextType HandleActionFailure(
            ContextType currentContext,
            Exception actionException)
        {
            PipelineError pipelineError = new(
                Plan.PipelineName,
                Phase,
                ActionIndex,
                ActionName,
                actionException);
            errors.Add(pipelineError);

            bool wasExecuting = ActionExecuting;
            int previousThreadId = ActionThreadId;
            ActionExecuting = false;
            ActionThreadId = 0;
            try
            {
                ContextType updatedContext;
                try
                {
                    updatedContext = Plan.ErrorHandler(currentContext, pipelineError);
                }
                catch (Exception handlerException)
                {
                    throw new InvalidErrorHandlerException(
                        "OnError handler raised while recovering from an Action failure",
                        actionException,
                        handlerException);
                }

                if (updatedContext is null)
                {
                    throw new InvalidErrorHandlerException(
                        "OnError handler returned null",
                        actionException);
                }
                Context = updatedContext;
                return updatedContext;
            }
            finally
            {
                ActionExecuting = wasExecuting;
                ActionThreadId = previousThreadId;
            }
        }

        internal void RecordTiming(long elapsedNanos, bool success)
        {
            if (!collectTimings)
            {
                return;
            }
            actionTimings.Add(new ActionTiming(
                Phase,
                ActionIndex,
                ActionName,
                elapsedNanos,
                success));
        }

        public IReadOnlyList<PipelineError> Errors() => errors.AsReadOnly();

        public string PipelineName() => Plan.PipelineName;

        public long RunStartNanos() => RunStartNanosValue;

        public IReadOnlyList<ActionTiming> ActionTimings() => actionTimings.AsReadOnly();
    }

    private static class PipelineRunner
    {
        internal static ContextType Run(PipelinePlan plan, ContextType input)
            => Execute(plan, input, collectTimings: false).Context;

        internal static PipelineResult<ContextType> RunDetailed(
            PipelinePlan plan,
            ContextType input)
        {
            ExecutionState state = Execute(plan, input, collectTimings: true);
            long totalNanos = NanoTime.GetElapsedNanos(
                state.RunStartTimestamp,
                Stopwatch.GetTimestamp());
            return new PipelineResult<ContextType>(
                state.Context,
                state.ShortCircuited,
                state.Errors(),
                state.ActionTimings(),
                totalNanos);
        }

        private static ExecutionState Execute(
            PipelinePlan plan,
            ContextType input,
            bool collectTimings)
        {
            if (input is null)
            {
                throw new ArgumentNullException(nameof(input));
            }

            long runStartTimestamp = Stopwatch.GetTimestamp();
            ExecutionState state = new(
                plan,
                input,
                collectTimings,
                runStartTimestamp,
                NanoTime.GetNowNanos());
            Notify(() => plan.Observer.OnPipelineStarted(plan.PipelineName));

            Exception? pendingFailure = null;
            using IDisposable executionScope = PipelineExecution.Open(state);
            try
            {
                try
                {
                    pendingFailure = ExecuteActions(
                        state,
                        StepPhase.Pre,
                        plan.PreActions,
                        stopOnShortCircuit: false,
                        pendingFailure);

                    if (pendingFailure is null && !state.ShortCircuited)
                    {
                        pendingFailure = ExecuteActions(
                            state,
                            StepPhase.Main,
                            plan.Actions,
                            stopOnShortCircuit: true,
                            pendingFailure);
                    }
                }
                finally
                {
                    pendingFailure = ExecuteActions(
                        state,
                        StepPhase.Post,
                        plan.PostActions,
                        stopOnShortCircuit: false,
                        pendingFailure);
                }
            }
            finally
            {
                long elapsedNanos = NanoTime.GetElapsedNanos(
                    runStartTimestamp,
                    Stopwatch.GetTimestamp());
                Notify(() => plan.Observer.OnPipelineCompleted(
                    plan.PipelineName,
                    state.ShortCircuited,
                    state.Errors().Count,
                    elapsedNanos));
            }

            if (pendingFailure is not null)
            {
                throw pendingFailure;
            }
            return state;
        }

        private static Exception? ExecuteActions(
            ExecutionState state,
            StepPhase phase,
            IReadOnlyList<RegisteredAction> registeredActions,
            bool stopOnShortCircuit,
            Exception? initialFailure)
        {
            if (initialFailure is not null && phase != StepPhase.Post)
            {
                return initialFailure;
            }

            Exception? pendingFailure = initialFailure;
            for (int actionIndex = 0; actionIndex < registeredActions.Count; actionIndex++)
            {
                RegisteredAction registeredAction = registeredActions[actionIndex];
                string actionName = FormatActionName(
                    phase,
                    actionIndex,
                    registeredAction.Name);
                state.BeginAction(phase, actionIndex, actionName);
                bool wasShortCircuited = state.ShortCircuited;

                Notify(() => state.Plan.Observer.OnActionStarted(
                    state.Plan.PipelineName,
                    phase,
                    actionIndex,
                    actionName));

                long actionStartTimestamp = Stopwatch.GetTimestamp();
                bool actionSucceeded = true;
                Exception? actionFailure = null;
                ContextType contextBeforeAction = state.Context;

                try
                {
                    ContextType nextContext;
                    state.BeginActionExecution();
                    try
                    {
                        nextContext = registeredAction.Invoke(state.Context, state);
                    }
                    finally
                    {
                        state.EndActionExecution();
                    }

                    if (nextContext is null)
                    {
                        throw new InvalidOperationException(
                            "Action returned null: " + actionName);
                    }
                    state.Context = nextContext;
                }
                catch (InvalidErrorHandlerException invalidHandler)
                {
                    actionSucceeded = false;
                    actionFailure = invalidHandler.ActionException;
                    pendingFailure ??= invalidHandler;
                    state.RequestShortCircuit();
                    state.Context = contextBeforeAction;
                }
                catch (Exception exception)
                {
                    actionSucceeded = false;
                    actionFailure = exception;
                    try
                    {
                        state.Context = state.HandleActionFailure(
                            contextBeforeAction,
                            exception);
                    }
                    catch (InvalidErrorHandlerException invalidHandler)
                    {
                        pendingFailure ??= invalidHandler;
                        state.RequestShortCircuit();
                        state.Context = contextBeforeAction;
                    }

                    if (state.Plan.ShortCircuitOnException)
                    {
                        state.RequestShortCircuit();
                    }
                }

                long elapsedNanos = NanoTime.GetElapsedNanos(
                    actionStartTimestamp,
                    Stopwatch.GetTimestamp());
                state.RecordTiming(elapsedNanos, actionSucceeded);

                if (actionSucceeded)
                {
                    Notify(() => state.Plan.Observer.OnActionCompleted(
                        state.Plan.PipelineName,
                        phase,
                        actionIndex,
                        actionName,
                        elapsedNanos));
                }
                else
                {
                    Exception reportedFailure = actionFailure
                        ?? new InvalidOperationException("Unknown Action failure");
                    Notify(() => state.Plan.Observer.OnActionFailed(
                        state.Plan.PipelineName,
                        phase,
                        actionIndex,
                        actionName,
                        reportedFailure,
                        elapsedNanos));
                }

                if (!wasShortCircuited && state.ShortCircuited)
                {
                    Notify(() => state.Plan.Observer.OnShortCircuited(
                        state.Plan.PipelineName,
                        phase,
                        actionIndex,
                        actionName));
                }

                if (pendingFailure is not null && phase != StepPhase.Post)
                {
                    break;
                }
                if (stopOnShortCircuit && state.ShortCircuited)
                {
                    break;
                }
            }
            return pendingFailure;
        }

        private static string FormatActionName(
            StepPhase phase,
            int actionIndex,
            string registeredName)
        {
            string prefix = phase switch
            {
                StepPhase.Pre => "pre",
                StepPhase.Post => "post",
                _ => "s"
            };
            return string.IsNullOrWhiteSpace(registeredName)
                ? prefix + actionIndex
                : prefix + actionIndex + ":" + registeredName;
        }

        private static void Notify(System.Action callback)
        {
            try
            {
                callback();
            }
            catch
            {
                // Observers cannot change Pipeline semantics.
            }
        }
    }
}
