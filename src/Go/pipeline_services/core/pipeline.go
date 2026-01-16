package core

import (
	"errors"
	"fmt"
	"time"
)

type StepPhase int

const (
	StepPhasePre StepPhase = iota
	StepPhaseMain
	StepPhasePost
)

func (phase StepPhase) String() string {
	if phase == StepPhasePre {
		return "pre"
	}
	if phase == StepPhasePost {
		return "post"
	}
	return "main"
}

type PipelineError struct {
	PipelineName string
	Phase        StepPhase
	StepIndex    int
	StepName     string
	Exception    error
}

type ActionTiming struct {
	Phase        StepPhase
	Index        int
	ActionName   string
	ElapsedNanos int64
	Success      bool
}

type StepControl[ContextType any] interface {
	ShortCircuit()
	IsShortCircuited() bool

	RecordError(ctx ContextType, exception error) ContextType
	Errors() []PipelineError

	PipelineName() string
	RunStartNanos() int64
	ActionTimings() []ActionTiming
}

type StepAction[ContextType any] interface {
	Apply(ctx ContextType, control StepControl[ContextType]) (ContextType, error)
}

type OnErrorFn[ContextType any] func(ctx ContextType, err PipelineError) ContextType

func DefaultOnError[ContextType any](ctx ContextType, err PipelineError) ContextType {
	if err.Exception == nil {
		return ctx
	}
	return ctx
}

type UnaryFunc[ContextType any] func(ctx ContextType) ContextType

func (unaryFunc UnaryFunc[ContextType]) Apply(ctx ContextType, control StepControl[ContextType]) (ContextType, error) {
	if control == nil {
		return unaryFunc(ctx), nil
	}
	return unaryFunc(ctx), nil
}

type UnaryFuncWithError[ContextType any] func(ctx ContextType) (ContextType, error)

func (unaryFunc UnaryFuncWithError[ContextType]) Apply(
	ctx ContextType,
	control StepControl[ContextType],
) (ContextType, error) {
	if control == nil {
		return unaryFunc(ctx)
	}
	return unaryFunc(ctx)
}

type StepFunc[ContextType any] func(ctx ContextType, control StepControl[ContextType]) ContextType

func (stepFunc StepFunc[ContextType]) Apply(ctx ContextType, control StepControl[ContextType]) (ContextType, error) {
	return stepFunc(ctx, control), nil
}

type StepFuncWithError[ContextType any] func(ctx ContextType, control StepControl[ContextType]) (ContextType, error)

func (stepFunc StepFuncWithError[ContextType]) Apply(
	ctx ContextType,
	control StepControl[ContextType],
) (ContextType, error) {
	return stepFunc(ctx, control)
}

type defaultStepControl[ContextType any] struct {
	pipelineName   string
	onError        OnErrorFn[ContextType]
	errors         []PipelineError
	actionTimings  []ActionTiming
	shortCircuited bool

	phase         StepPhase
	index         int
	stepName      string
	runStart      time.Time
	runStartNanos int64
	hasRunStart   bool
}

func newDefaultStepControl[ContextType any](pipelineName string, onError OnErrorFn[ContextType]) *defaultStepControl[ContextType] {
	if onError == nil {
		onError = DefaultOnError[ContextType]
	}
	return &defaultStepControl[ContextType]{
		pipelineName:   pipelineName,
		onError:        onError,
		errors:         []PipelineError{},
		actionTimings:  []ActionTiming{},
		shortCircuited: false,
		phase:          StepPhaseMain,
		index:          0,
		stepName:       "?",
		runStart:       time.Time{},
		runStartNanos:  0,
		hasRunStart:    false,
	}
}

func (control *defaultStepControl[ContextType]) beginRun(startTime time.Time) {
	control.runStart = startTime
	control.runStartNanos = startTime.UnixNano()
	control.hasRunStart = true
}

func (control *defaultStepControl[ContextType]) beginStep(phase StepPhase, index int, stepName string) {
	control.phase = phase
	control.index = index
	control.stepName = stepName
}

func (control *defaultStepControl[ContextType]) recordTiming(elapsedNanos int64, success bool) {
	timing := ActionTiming{
		Phase:        control.phase,
		Index:        control.index,
		ActionName:   control.stepName,
		ElapsedNanos: elapsedNanos,
		Success:      success,
	}
	control.actionTimings = append(control.actionTimings, timing)
}

func (control *defaultStepControl[ContextType]) ShortCircuit() {
	control.shortCircuited = true
}

func (control *defaultStepControl[ContextType]) IsShortCircuited() bool {
	return control.shortCircuited
}

func (control *defaultStepControl[ContextType]) RecordError(ctx ContextType, exception error) ContextType {
	if exception == nil {
		exception = errors.New("unknown error")
	}
	pipelineError := PipelineError{
		PipelineName: control.pipelineName,
		Phase:        control.phase,
		StepIndex:    control.index,
		StepName:     control.stepName,
		Exception:    exception,
	}
	control.errors = append(control.errors, pipelineError)
	return control.onError(ctx, pipelineError)
}

func (control *defaultStepControl[ContextType]) Errors() []PipelineError {
	errorsCopy := make([]PipelineError, 0, len(control.errors))
	for index := 0; index < len(control.errors); index++ {
		errorsCopy = append(errorsCopy, control.errors[index])
	}
	return errorsCopy
}

func (control *defaultStepControl[ContextType]) PipelineName() string {
	return control.pipelineName
}

func (control *defaultStepControl[ContextType]) RunStartNanos() int64 {
	return control.runStartNanos
}

func (control *defaultStepControl[ContextType]) ActionTimings() []ActionTiming {
	timingsCopy := make([]ActionTiming, 0, len(control.actionTimings))
	for index := 0; index < len(control.actionTimings); index++ {
		timingsCopy = append(timingsCopy, control.actionTimings[index])
	}
	return timingsCopy
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

type Pipeline[ContextType any] struct {
	name                    string
	shortCircuitOnException bool
	onError                 OnErrorFn[ContextType]

	preActions  []registeredAction[ContextType]
	actions     []registeredAction[ContextType]
	postActions []registeredAction[ContextType]
}

type registeredAction[ContextType any] struct {
	name   string
	action StepAction[ContextType]
}

func NewPipeline[ContextType any](name string, shortCircuitOnException bool) *Pipeline[ContextType] {
	return &Pipeline[ContextType]{
		name:                    name,
		shortCircuitOnException: shortCircuitOnException,
		onError:                 DefaultOnError[ContextType],
		preActions:              []registeredAction[ContextType]{},
		actions:                 []registeredAction[ContextType]{},
		postActions:             []registeredAction[ContextType]{},
	}
}

func (pipeline *Pipeline[ContextType]) Name() string {
	return pipeline.name
}

func (pipeline *Pipeline[ContextType]) ShortCircuitOnException() bool {
	return pipeline.shortCircuitOnException
}

func (pipeline *Pipeline[ContextType]) Size() int {
	return len(pipeline.actions)
}

func (pipeline *Pipeline[ContextType]) OnError(handler OnErrorFn[ContextType]) *Pipeline[ContextType] {
	if handler == nil {
		pipeline.onError = DefaultOnError[ContextType]
		return pipeline
	}
	pipeline.onError = handler
	return pipeline
}

func (pipeline *Pipeline[ContextType]) AddPreAction(action any) *Pipeline[ContextType] {
	return pipeline.AddPreActionNamed("", action)
}

func (pipeline *Pipeline[ContextType]) AddPreActionNamed(actionName string, action any) *Pipeline[ContextType] {
	normalizedAction, normalizeError := normalizeAction[ContextType](action)
	if normalizeError != nil {
		panic(normalizeError)
	}
	pipeline.preActions = append(pipeline.preActions, registeredAction[ContextType]{name: actionName, action: normalizedAction})
	return pipeline
}

func (pipeline *Pipeline[ContextType]) AddAction(action any) *Pipeline[ContextType] {
	return pipeline.AddActionNamed("", action)
}

func (pipeline *Pipeline[ContextType]) AddActionNamed(actionName string, action any) *Pipeline[ContextType] {
	normalizedAction, normalizeError := normalizeAction[ContextType](action)
	if normalizeError != nil {
		panic(normalizeError)
	}
	pipeline.actions = append(pipeline.actions, registeredAction[ContextType]{name: actionName, action: normalizedAction})
	return pipeline
}

func (pipeline *Pipeline[ContextType]) AddPostAction(action any) *Pipeline[ContextType] {
	return pipeline.AddPostActionNamed("", action)
}

func (pipeline *Pipeline[ContextType]) AddPostActionNamed(actionName string, action any) *Pipeline[ContextType] {
	normalizedAction, normalizeError := normalizeAction[ContextType](action)
	if normalizeError != nil {
		panic(normalizeError)
	}
	pipeline.postActions = append(pipeline.postActions, registeredAction[ContextType]{name: actionName, action: normalizedAction})
	return pipeline
}

func (pipeline *Pipeline[ContextType]) Run(input ContextType) ContextType {
	return pipeline.Execute(input).Context
}

func (pipeline *Pipeline[ContextType]) Execute(input ContextType) PipelineResult[ContextType] {
	contextValue := input
	runStartTimepoint := time.Now()

	control := newDefaultStepControl[ContextType](pipeline.name, pipeline.onError)
	control.beginRun(runStartTimepoint)

	contextValue = pipeline.runPhase(control, StepPhasePre, contextValue, pipeline.preActions, false)
	if !control.IsShortCircuited() {
		contextValue = pipeline.runPhase(control, StepPhaseMain, contextValue, pipeline.actions, true)
	}
	contextValue = pipeline.runPhase(control, StepPhasePost, contextValue, pipeline.postActions, false)

	totalNanos := time.Since(runStartTimepoint).Nanoseconds()
	return PipelineResult[ContextType]{
		Context:        contextValue,
		ShortCircuited: control.IsShortCircuited(),
		Errors:         control.Errors(),
		ActionTimings:  control.ActionTimings(),
		TotalNanos:     totalNanos,
	}
}

func (pipeline *Pipeline[ContextType]) runPhase(
	control *defaultStepControl[ContextType],
	phase StepPhase,
	startContext ContextType,
	actions []registeredAction[ContextType],
	stopOnShortCircuit bool,
) ContextType {
	contextValue := startContext
	for index := 0; index < len(actions); index++ {
		registeredActionValue := actions[index]
		stepName := formatStepName(phase, index, registeredActionValue.name)
		control.beginStep(phase, index, stepName)

		stepStartTimepoint := time.Now()
		actionSucceeded := true

		contextBeforeStep := contextValue
		nextContext, actionError := registeredActionValue.action.Apply(contextValue, control)
		if actionError != nil {
			actionSucceeded = false
			contextValue = control.RecordError(contextBeforeStep, actionError)
			if pipeline.shortCircuitOnException {
				control.ShortCircuit()
			}
		} else {
			contextValue = nextContext
		}

		elapsedNanos := time.Since(stepStartTimepoint).Nanoseconds()
		control.recordTiming(elapsedNanos, actionSucceeded)

		if stopOnShortCircuit && control.IsShortCircuited() {
			break
		}
	}
	return contextValue
}

func formatStepName(phase StepPhase, index int, label string) string {
	prefix := "s"
	if phase == StepPhasePre {
		prefix = "pre"
	}
	if phase == StepPhasePost {
		prefix = "post"
	}

	if label == "" {
		return fmt.Sprintf("%s%d", prefix, index)
	}
	return fmt.Sprintf("%s%d:%s", prefix, index, label)
}

func normalizeAction[ContextType any](action any) (StepAction[ContextType], error) {
	if action == nil {
		return nil, errors.New("action must not be nil")
	}

	if stepAction, ok := action.(StepAction[ContextType]); ok {
		return stepAction, nil
	}

	if unaryFunc, ok := action.(func(ContextType) ContextType); ok {
		return UnaryFunc[ContextType](unaryFunc), nil
	}

	if unaryFuncWithError, ok := action.(func(ContextType) (ContextType, error)); ok {
		return UnaryFuncWithError[ContextType](unaryFuncWithError), nil
	}

	if stepFunc, ok := action.(func(ContextType, StepControl[ContextType]) ContextType); ok {
		return StepFunc[ContextType](stepFunc), nil
	}

	if stepFuncWithError, ok := action.(func(ContextType, StepControl[ContextType]) (ContextType, error)); ok {
		return StepFuncWithError[ContextType](stepFuncWithError), nil
	}

	return nil, fmt.Errorf("unsupported action type: %T", action)
}
