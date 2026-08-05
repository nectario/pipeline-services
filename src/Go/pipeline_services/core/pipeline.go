package core

import (
	"errors"
	"fmt"
	"sync"
	"sync/atomic"
	"time"
)

type StepPhase int

const (
	StepPhasePre StepPhase = iota
	StepPhaseMain
	StepPhasePost
)

func (phase StepPhase) String() string {
	switch phase {
	case StepPhasePre:
		return "preActions"
	case StepPhasePost:
		return "postActions"
	default:
		return "actions"
	}
}

type PipelineError struct {
	PipelineName string
	Phase        StepPhase
	ActionIndex  int
	ActionName   string
	Exception    error

	// Preview compatibility aliases.
	StepIndex int
	StepName  string
}

type ActionTiming struct {
	Phase        StepPhase
	ActionIndex  int
	ActionName   string
	ElapsedNanos int64
	Success      bool

	// Preview compatibility alias.
	Index int
}

type PipelineResult[ContextType any] struct {
	Context        ContextType
	ShortCircuited bool
	Errors         []PipelineError
	ActionTimings  []ActionTiming
	TotalNanos     int64
}

func (result PipelineResult[ContextType]) HasErrors() bool {
	return len(result.Errors) > 0
}

type Action[ContextType any] func(ContextType) (ContextType, error)
type UnaryFunc[ContextType any] func(ContextType) ContextType
type UnaryFuncWithError[ContextType any] func(ContextType) (ContextType, error)
type OnErrorFn[ContextType any] func(ContextType, PipelineError) ContextType

// PipelineExecution is Go's explicit execution-scoped control handle.
// Go intentionally uses an explicit handle because the language does not
// provide supported goroutine-local storage for a safe ambient ShortCircuit().
type PipelineExecution interface {
	ShortCircuit()
	IsShortCircuited() bool
}

type ControlledFunc[ContextType any] func(ContextType, PipelineExecution) ContextType
type ControlledFuncWithError[ContextType any] func(
	ContextType,
	PipelineExecution,
) (ContextType, error)

type ActionControl[ContextType any] interface {
	PipelineExecution
	RecordError(ContextType, error) ContextType
	Errors() []PipelineError
	PipelineName() string
	RunStartNanos() int64
	ActionTimings() []ActionTiming
}

type StepControl[ContextType any] interface {
	ActionControl[ContextType]
}

type StepAction[ContextType any] interface {
	Apply(ContextType, ActionControl[ContextType]) (ContextType, error)
}

type StepFunc[ContextType any] func(ContextType, ActionControl[ContextType]) ContextType

func (action StepFunc[ContextType]) Apply(
	context ContextType,
	control ActionControl[ContextType],
) (ContextType, error) {
	return action(context, control), nil
}

type StepFuncWithError[ContextType any] func(
	ContextType,
	ActionControl[ContextType],
) (ContextType, error)

func (action StepFuncWithError[ContextType]) Apply(
	context ContextType,
	control ActionControl[ContextType],
) (ContextType, error) {
	return action(context, control)
}

func DefaultOnError[ContextType any](context ContextType, pipelineError PipelineError) ContextType {
	_ = pipelineError
	return context
}

type PipelineObserver interface {
	OnPipelineStarted(string)
	OnActionStarted(string, StepPhase, int, string)
	OnActionCompleted(string, StepPhase, int, string, int64)
	OnActionFailed(string, StepPhase, int, string, error, int64)
	OnShortCircuited(string, StepPhase, int, string)
	OnPipelineCompleted(string, bool, int, int64)
}

type NoopPipelineObserver struct{}

func (NoopPipelineObserver) OnPipelineStarted(string)                                    {}
func (NoopPipelineObserver) OnActionStarted(string, StepPhase, int, string)              {}
func (NoopPipelineObserver) OnActionCompleted(string, StepPhase, int, string, int64)     {}
func (NoopPipelineObserver) OnActionFailed(string, StepPhase, int, string, error, int64) {}
func (NoopPipelineObserver) OnShortCircuited(string, StepPhase, int, string)             {}
func (NoopPipelineObserver) OnPipelineCompleted(string, bool, int, int64)                {}

type executionSignal struct {
	shortCircuited  atomic.Bool
	actionExecuting atomic.Bool
}

// ShortCircuit is retained only as a migration guard. Go cannot implement a
// safe ambient execution scope without unsupported goroutine-ID tricks.
// Actions that need to stop the current run must accept PipelineExecution and
// call execution.ShortCircuit().
func ShortCircuit() {
	panic("Go requires an explicit PipelineExecution parameter; call execution.ShortCircuit() from the active Action")
}

type defaultActionControl[ContextType any] struct {
	pipelineName   string
	onError        OnErrorFn[ContextType]
	errors         []PipelineError
	actionTimings  []ActionTiming
	phase          StepPhase
	actionIndex    int
	actionName     string
	runStart       time.Time
	signal         *executionSignal
	collectTimings bool
}

func newDefaultActionControl[ContextType any](
	pipelineName string,
	onError OnErrorFn[ContextType],
	signal *executionSignal,
	collectTimings bool,
) *defaultActionControl[ContextType] {
	if onError == nil {
		onError = DefaultOnError[ContextType]
	}
	return &defaultActionControl[ContextType]{
		pipelineName:   pipelineName,
		onError:        onError,
		errors:         make([]PipelineError, 0),
		actionTimings:  make([]ActionTiming, 0),
		phase:          StepPhaseMain,
		actionName:     "?",
		runStart:       time.Now(),
		signal:         signal,
		collectTimings: collectTimings,
	}
}

func (control *defaultActionControl[ContextType]) beginAction(
	phase StepPhase,
	actionIndex int,
	actionName string,
) {
	control.phase = phase
	control.actionIndex = actionIndex
	control.actionName = actionName
}

func (control *defaultActionControl[ContextType]) requestShortCircuit() {
	control.signal.shortCircuited.Store(true)
}

func (control *defaultActionControl[ContextType]) ShortCircuit() {
	if !control.signal.actionExecuting.Load() {
		panic("PipelineExecution.ShortCircuit() can only be called while the current Pipeline Action is executing")
	}
	control.requestShortCircuit()
}

func (control *defaultActionControl[ContextType]) IsShortCircuited() bool {
	return control.signal.shortCircuited.Load()
}

func (control *defaultActionControl[ContextType]) RecordError(
	context ContextType,
	exception error,
) ContextType {
	if exception == nil {
		exception = errors.New("unknown error")
	}
	pipelineError := makePipelineError(
		control.pipelineName,
		control.phase,
		control.actionIndex,
		control.actionName,
		exception,
	)
	control.errors = append(control.errors, pipelineError)
	wasExecuting := control.signal.actionExecuting.Swap(false)
	defer control.signal.actionExecuting.Store(wasExecuting)
	return control.onError(context, pipelineError)
}

func (control *defaultActionControl[ContextType]) Errors() []PipelineError {
	return append([]PipelineError(nil), control.errors...)
}

func (control *defaultActionControl[ContextType]) PipelineName() string {
	return control.pipelineName
}

func (control *defaultActionControl[ContextType]) RunStartNanos() int64 {
	return control.runStart.UnixNano()
}

func (control *defaultActionControl[ContextType]) ActionTimings() []ActionTiming {
	return append([]ActionTiming(nil), control.actionTimings...)
}

func (control *defaultActionControl[ContextType]) recordTiming(
	elapsedNanos int64,
	success bool,
) {
	if !control.collectTimings {
		return
	}
	control.actionTimings = append(control.actionTimings, ActionTiming{
		Phase:        control.phase,
		ActionIndex:  control.actionIndex,
		Index:        control.actionIndex,
		ActionName:   control.actionName,
		ElapsedNanos: elapsedNanos,
		Success:      success,
	})
}

type actionInvoker[ContextType any] func(
	ContextType,
	*defaultActionControl[ContextType],
) (ContextType, error)

type registeredAction[ContextType any] struct {
	name   string
	invoke actionInvoker[ContextType]
}

type pipelinePlan[ContextType any] struct {
	name                    string
	shortCircuitOnException bool
	onError                 OnErrorFn[ContextType]
	observer                PipelineObserver
	preActions              []registeredAction[ContextType]
	actions                 []registeredAction[ContextType]
	postActions             []registeredAction[ContextType]
}

type Pipeline[ContextType any] struct {
	name                    string
	shortCircuitOnException bool
	onError                 OnErrorFn[ContextType]
	observer                PipelineObserver
	preActions              []registeredAction[ContextType]
	actions                 []registeredAction[ContextType]
	postActions             []registeredAction[ContextType]
	planMutex               sync.Mutex
	frozenPlan              *pipelinePlan[ContextType]
}

func NewPipeline[ContextType any](name string, shortCircuitOnException bool) *Pipeline[ContextType] {
	if name == "" {
		panic("Pipeline name must not be blank")
	}
	return &Pipeline[ContextType]{
		name:                    name,
		shortCircuitOnException: shortCircuitOnException,
		onError:                 DefaultOnError[ContextType],
		observer:                NoopPipelineObserver{},
		preActions:              make([]registeredAction[ContextType], 0),
		actions:                 make([]registeredAction[ContextType], 0),
		postActions:             make([]registeredAction[ContextType], 0),
	}
}

func (pipeline *Pipeline[ContextType]) Name() string {
	return pipeline.name
}

func (pipeline *Pipeline[ContextType]) ShortCircuitOnException() bool {
	pipeline.planMutex.Lock()
	defer pipeline.planMutex.Unlock()
	if pipeline.frozenPlan != nil {
		return pipeline.frozenPlan.shortCircuitOnException
	}
	return pipeline.shortCircuitOnException
}

func (pipeline *Pipeline[ContextType]) Size() int {
	return len(pipeline.plan().actions)
}

func (pipeline *Pipeline[ContextType]) IsFrozen() bool {
	pipeline.planMutex.Lock()
	defer pipeline.planMutex.Unlock()
	return pipeline.frozenPlan != nil
}

func (pipeline *Pipeline[ContextType]) Freeze() *Pipeline[ContextType] {
	_ = pipeline.plan()
	return pipeline
}

func (pipeline *Pipeline[ContextType]) OnError(handler OnErrorFn[ContextType]) *Pipeline[ContextType] {
	pipeline.planMutex.Lock()
	defer pipeline.planMutex.Unlock()
	pipeline.ensureMutableLocked()
	if handler == nil {
		handler = DefaultOnError[ContextType]
	}
	pipeline.onError = handler
	return pipeline
}

func (pipeline *Pipeline[ContextType]) Observer(observer PipelineObserver) *Pipeline[ContextType] {
	pipeline.planMutex.Lock()
	defer pipeline.planMutex.Unlock()
	pipeline.ensureMutableLocked()
	if observer == nil {
		observer = NoopPipelineObserver{}
	}
	pipeline.observer = observer
	return pipeline
}

func (pipeline *Pipeline[ContextType]) AddPreAction(action any) *Pipeline[ContextType] {
	return pipeline.AddPreActionNamed("", action)
}

func (pipeline *Pipeline[ContextType]) AddPreActionNamed(name string, action any) *Pipeline[ContextType] {
	normalized := normalizeAction[ContextType](action)
	pipeline.planMutex.Lock()
	defer pipeline.planMutex.Unlock()
	pipeline.ensureMutableLocked()
	pipeline.preActions = append(pipeline.preActions, registeredAction[ContextType]{
		name: name, invoke: normalized,
	})
	return pipeline
}

func (pipeline *Pipeline[ContextType]) AddAction(action any) *Pipeline[ContextType] {
	return pipeline.AddActionNamed("", action)
}

func (pipeline *Pipeline[ContextType]) AddActionNamed(name string, action any) *Pipeline[ContextType] {
	normalized := normalizeAction[ContextType](action)
	pipeline.planMutex.Lock()
	defer pipeline.planMutex.Unlock()
	pipeline.ensureMutableLocked()
	pipeline.actions = append(pipeline.actions, registeredAction[ContextType]{
		name: name, invoke: normalized,
	})
	return pipeline
}

func (pipeline *Pipeline[ContextType]) AddPostAction(action any) *Pipeline[ContextType] {
	return pipeline.AddPostActionNamed("", action)
}

func (pipeline *Pipeline[ContextType]) AddPostActionNamed(name string, action any) *Pipeline[ContextType] {
	normalized := normalizeAction[ContextType](action)
	pipeline.planMutex.Lock()
	defer pipeline.planMutex.Unlock()
	pipeline.ensureMutableLocked()
	pipeline.postActions = append(pipeline.postActions, registeredAction[ContextType]{
		name: name, invoke: normalized,
	})
	return pipeline
}

func (pipeline *Pipeline[ContextType]) Run(input ContextType) ContextType {
	return executePipeline(pipeline.plan(), input, false).context
}

func (pipeline *Pipeline[ContextType]) RunDetailed(input ContextType) PipelineResult[ContextType] {
	state := executePipeline(pipeline.plan(), input, true)
	return PipelineResult[ContextType]{
		Context:        state.context,
		ShortCircuited: state.control.IsShortCircuited(),
		Errors:         state.control.Errors(),
		ActionTimings:  state.control.ActionTimings(),
		TotalNanos:     time.Since(state.control.runStart).Nanoseconds(),
	}
}

func (pipeline *Pipeline[ContextType]) Execute(input ContextType) PipelineResult[ContextType] {
	return pipeline.RunDetailed(input)
}

func (pipeline *Pipeline[ContextType]) plan() *pipelinePlan[ContextType] {
	pipeline.planMutex.Lock()
	defer pipeline.planMutex.Unlock()
	if pipeline.frozenPlan == nil {
		pipeline.frozenPlan = &pipelinePlan[ContextType]{
			name:                    pipeline.name,
			shortCircuitOnException: pipeline.shortCircuitOnException,
			onError:                 pipeline.onError,
			observer:                pipeline.observer,
			preActions:              append([]registeredAction[ContextType](nil), pipeline.preActions...),
			actions:                 append([]registeredAction[ContextType](nil), pipeline.actions...),
			postActions:             append([]registeredAction[ContextType](nil), pipeline.postActions...),
		}
	}
	return pipeline.frozenPlan
}

func (pipeline *Pipeline[ContextType]) ensureMutableLocked() {
	if pipeline.frozenPlan != nil {
		panic(fmt.Sprintf("Pipeline '%s' is frozen", pipeline.name))
	}
}

type executionState[ContextType any] struct {
	context ContextType
	control *defaultActionControl[ContextType]
}

func executePipeline[ContextType any](
	plan *pipelinePlan[ContextType],
	input ContextType,
	collectTimings bool,
) executionState[ContextType] {
	signal := &executionSignal{}
	control := newDefaultActionControl(plan.name, plan.onError, signal, collectTimings)
	notify(func() { plan.observer.OnPipelineStarted(plan.name) })

	contextValue := input
	var pendingPanic any
	contextValue = executeActions(
		plan, contextValue, plan.preActions, StepPhasePre, false, control, &pendingPanic,
	)
	if pendingPanic == nil && !control.IsShortCircuited() {
		contextValue = executeActions(
			plan, contextValue, plan.actions, StepPhaseMain, true, control, &pendingPanic,
		)
	}
	contextValue = executeActions(
		plan, contextValue, plan.postActions, StepPhasePost, false, control, &pendingPanic,
	)

	notify(func() {
		plan.observer.OnPipelineCompleted(
			plan.name,
			control.IsShortCircuited(),
			len(control.errors),
			time.Since(control.runStart).Nanoseconds(),
		)
	})
	if pendingPanic != nil {
		panic(pendingPanic)
	}
	return executionState[ContextType]{context: contextValue, control: control}
}

func executeActions[ContextType any](
	plan *pipelinePlan[ContextType],
	startContext ContextType,
	actions []registeredAction[ContextType],
	phase StepPhase,
	stopOnShortCircuit bool,
	control *defaultActionControl[ContextType],
	pendingPanic *any,
) ContextType {
	if *pendingPanic != nil && phase != StepPhasePost {
		return startContext
	}
	contextValue := startContext
	for actionIndex, registered := range actions {
		actionName := formatActionName(phase, actionIndex, registered.name)
		control.beginAction(phase, actionIndex, actionName)
		wasShortCircuited := control.IsShortCircuited()
		notify(func() {
			plan.observer.OnActionStarted(plan.name, phase, actionIndex, actionName)
		})

		actionStarted := time.Now()
		contextBeforeAction := contextValue
		control.signal.actionExecuting.Store(true)
		nextContext, actionError, actionPanic := invokeAction(
			registered.invoke, contextValue, control,
		)
		control.signal.actionExecuting.Store(false)
		succeeded := actionError == nil && actionPanic == nil
		if succeeded {
			contextValue = nextContext
		} else {
			if actionError == nil {
				actionError = fmt.Errorf("panic: %v", actionPanic)
			}
			pipelineError := makePipelineError(
				plan.name, phase, actionIndex, actionName, actionError,
			)
			control.errors = append(control.errors, pipelineError)
			updatedContext, handlerPanic := invokeErrorHandler(
				plan.onError, contextBeforeAction, pipelineError,
			)
			if handlerPanic != nil {
				contextValue = contextBeforeAction
				control.requestShortCircuit()
				if *pendingPanic == nil {
					*pendingPanic = handlerPanic
				}
			} else {
				contextValue = updatedContext
			}
			if plan.shortCircuitOnException {
				control.requestShortCircuit()
			}
		}

		elapsedNanos := time.Since(actionStarted).Nanoseconds()
		control.recordTiming(elapsedNanos, succeeded)
		if succeeded {
			notify(func() {
				plan.observer.OnActionCompleted(plan.name, phase, actionIndex, actionName, elapsedNanos)
			})
		} else {
			notify(func() {
				plan.observer.OnActionFailed(plan.name, phase, actionIndex, actionName, actionError, elapsedNanos)
			})
		}
		if !wasShortCircuited && control.IsShortCircuited() {
			notify(func() {
				plan.observer.OnShortCircuited(plan.name, phase, actionIndex, actionName)
			})
		}
		if *pendingPanic != nil && phase != StepPhasePost {
			break
		}
		if stopOnShortCircuit && control.IsShortCircuited() {
			break
		}
	}
	return contextValue
}

func invokeAction[ContextType any](
	action actionInvoker[ContextType],
	context ContextType,
	control *defaultActionControl[ContextType],
) (output ContextType, actionError error, panicValue any) {
	defer func() { panicValue = recover() }()
	output, actionError = action(context, control)
	return
}

func invokeErrorHandler[ContextType any](
	handler OnErrorFn[ContextType],
	context ContextType,
	pipelineError PipelineError,
) (output ContextType, panicValue any) {
	defer func() { panicValue = recover() }()
	output = handler(context, pipelineError)
	return
}

func notify(callback func()) {
	defer func() { _ = recover() }()
	callback()
}

func makePipelineError(
	pipelineName string,
	phase StepPhase,
	actionIndex int,
	actionName string,
	exception error,
) PipelineError {
	return PipelineError{
		PipelineName: pipelineName,
		Phase:        phase,
		ActionIndex:  actionIndex,
		ActionName:   actionName,
		Exception:    exception,
		StepIndex:    actionIndex,
		StepName:     actionName,
	}
}

func formatActionName(phase StepPhase, index int, label string) string {
	prefix := "s"
	if phase == StepPhasePre {
		prefix = "pre"
	} else if phase == StepPhasePost {
		prefix = "post"
	}
	if label == "" {
		return fmt.Sprintf("%s%d", prefix, index)
	}
	return fmt.Sprintf("%s%d:%s", prefix, index, label)
}

func normalizeAction[ContextType any](action any) actionInvoker[ContextType] {
	if action == nil {
		panic("Action must not be nil")
	}
	if invoker, ok := action.(actionInvoker[ContextType]); ok {
		return invoker
	}
	if normalized, ok := action.(Action[ContextType]); ok {
		return func(context ContextType, _ *defaultActionControl[ContextType]) (ContextType, error) {
			return normalized(context)
		}
	}
	if unary, ok := action.(func(ContextType) ContextType); ok {
		return func(context ContextType, _ *defaultActionControl[ContextType]) (ContextType, error) {
			return unary(context), nil
		}
	}
	if unary, ok := action.(UnaryFunc[ContextType]); ok {
		return func(context ContextType, _ *defaultActionControl[ContextType]) (ContextType, error) {
			return unary(context), nil
		}
	}
	if unary, ok := action.(func(ContextType) (ContextType, error)); ok {
		return func(context ContextType, _ *defaultActionControl[ContextType]) (ContextType, error) {
			return unary(context)
		}
	}
	if unary, ok := action.(UnaryFuncWithError[ContextType]); ok {
		return func(context ContextType, _ *defaultActionControl[ContextType]) (ContextType, error) {
			return unary(context)
		}
	}
	if controlled, ok := action.(func(ContextType, PipelineExecution) ContextType); ok {
		return func(context ContextType, control *defaultActionControl[ContextType]) (ContextType, error) {
			return controlled(context, control), nil
		}
	}
	if controlled, ok := action.(ControlledFunc[ContextType]); ok {
		return func(context ContextType, control *defaultActionControl[ContextType]) (ContextType, error) {
			return controlled(context, control), nil
		}
	}
	if controlled, ok := action.(func(ContextType, PipelineExecution) (ContextType, error)); ok {
		return func(context ContextType, control *defaultActionControl[ContextType]) (ContextType, error) {
			return controlled(context, control)
		}
	}
	if controlled, ok := action.(ControlledFuncWithError[ContextType]); ok {
		return func(context ContextType, control *defaultActionControl[ContextType]) (ContextType, error) {
			return controlled(context, control)
		}
	}
	if legacy, ok := action.(StepAction[ContextType]); ok {
		return func(context ContextType, control *defaultActionControl[ContextType]) (ContextType, error) {
			return legacy.Apply(context, control)
		}
	}
	if legacy, ok := action.(func(ContextType, ActionControl[ContextType]) ContextType); ok {
		return func(context ContextType, control *defaultActionControl[ContextType]) (ContextType, error) {
			return legacy(context, control), nil
		}
	}
	if legacy, ok := action.(func(ContextType, ActionControl[ContextType]) (ContextType, error)); ok {
		return func(context ContextType, control *defaultActionControl[ContextType]) (ContextType, error) {
			return legacy(context, control)
		}
	}
	panic(fmt.Sprintf("unsupported Action type: %T", action))
}
