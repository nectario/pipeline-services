package core

import "time"

type RuntimePipeline[ContextType any] struct {
	name                    string
	shortCircuitOnException bool

	ended   bool
	current ContextType

	preActions  []registeredAction[ContextType]
	actions     []registeredAction[ContextType]
	postActions []registeredAction[ContextType]

	preIndex    int
	actionIndex int
	postIndex   int

	control *defaultStepControl[ContextType]
}

func NewRuntimePipeline[ContextType any](name string, shortCircuitOnException bool, initial ContextType) *RuntimePipeline[ContextType] {
	return &RuntimePipeline[ContextType]{
		name:                    name,
		shortCircuitOnException: shortCircuitOnException,
		ended:                   false,
		current:                 initial,
		preActions:              []registeredAction[ContextType]{},
		actions:                 []registeredAction[ContextType]{},
		postActions:             []registeredAction[ContextType]{},
		preIndex:                0,
		actionIndex:             0,
		postIndex:               0,
		control:                 newDefaultStepControl[ContextType](name, DefaultOnError[ContextType]),
	}
}

func (runtimePipeline *RuntimePipeline[ContextType]) Value() ContextType {
	return runtimePipeline.current
}

func (runtimePipeline *RuntimePipeline[ContextType]) Reset(value ContextType) {
	runtimePipeline.current = value
	runtimePipeline.ended = false
	runtimePipeline.control = newDefaultStepControl[ContextType](runtimePipeline.name, DefaultOnError[ContextType])
}

func (runtimePipeline *RuntimePipeline[ContextType]) AddPreAction(action any) (ContextType, error) {
	if runtimePipeline.ended {
		return runtimePipeline.current, nil
	}

	normalizedAction, normalizeError := normalizeAction[ContextType](action)
	if normalizeError != nil {
		return runtimePipeline.current, normalizeError
	}

	registered := registeredAction[ContextType]{name: "", action: normalizedAction}
	runtimePipeline.preActions = append(runtimePipeline.preActions, registered)
	indexValue := runtimePipeline.preIndex
	runtimePipeline.preIndex += 1
	return runtimePipeline.applyAction(registered, StepPhasePre, indexValue)
}

func (runtimePipeline *RuntimePipeline[ContextType]) AddAction(action any) (ContextType, error) {
	if runtimePipeline.ended {
		return runtimePipeline.current, nil
	}

	normalizedAction, normalizeError := normalizeAction[ContextType](action)
	if normalizeError != nil {
		return runtimePipeline.current, normalizeError
	}

	registered := registeredAction[ContextType]{name: "", action: normalizedAction}
	runtimePipeline.actions = append(runtimePipeline.actions, registered)
	indexValue := runtimePipeline.actionIndex
	runtimePipeline.actionIndex += 1
	return runtimePipeline.applyAction(registered, StepPhaseMain, indexValue)
}

func (runtimePipeline *RuntimePipeline[ContextType]) AddPostAction(action any) (ContextType, error) {
	if runtimePipeline.ended {
		return runtimePipeline.current, nil
	}

	normalizedAction, normalizeError := normalizeAction[ContextType](action)
	if normalizeError != nil {
		return runtimePipeline.current, normalizeError
	}

	registered := registeredAction[ContextType]{name: "", action: normalizedAction}
	runtimePipeline.postActions = append(runtimePipeline.postActions, registered)
	indexValue := runtimePipeline.postIndex
	runtimePipeline.postIndex += 1
	return runtimePipeline.applyAction(registered, StepPhasePost, indexValue)
}

func (runtimePipeline *RuntimePipeline[ContextType]) Freeze() *Pipeline[ContextType] {
	immutable := NewPipeline[ContextType](runtimePipeline.name, runtimePipeline.shortCircuitOnException)
	for index := 0; index < len(runtimePipeline.preActions); index++ {
		registered := runtimePipeline.preActions[index]
		immutable.AddPreActionNamed(registered.name, registered.action)
	}
	for index := 0; index < len(runtimePipeline.actions); index++ {
		registered := runtimePipeline.actions[index]
		immutable.AddActionNamed(registered.name, registered.action)
	}
	for index := 0; index < len(runtimePipeline.postActions); index++ {
		registered := runtimePipeline.postActions[index]
		immutable.AddPostActionNamed(registered.name, registered.action)
	}
	return immutable
}

func (runtimePipeline *RuntimePipeline[ContextType]) applyAction(
	registered registeredAction[ContextType],
	phase StepPhase,
	indexValue int,
) (ContextType, error) {
	stepName := formatStepName(phase, indexValue, registered.name)
	runtimePipeline.control.beginStep(phase, indexValue, stepName)

	contextBeforeStep := runtimePipeline.current
	stepStartTimepoint := time.Now()
	next, applyError := registered.action.Apply(runtimePipeline.current, runtimePipeline.control)
	if applyError != nil {
		runtimePipeline.current = runtimePipeline.control.RecordError(contextBeforeStep, applyError)
		if runtimePipeline.shortCircuitOnException {
			runtimePipeline.control.ShortCircuit()
			runtimePipeline.ended = true
		}
	} else {
		runtimePipeline.current = next
	}

	elapsedNanos := time.Since(stepStartTimepoint).Nanoseconds()
	runtimePipeline.control.recordTiming(elapsedNanos, applyError == nil)

	if runtimePipeline.control.IsShortCircuited() {
		runtimePipeline.ended = true
	}

	return runtimePipeline.current, applyError
}
