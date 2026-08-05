package core

// RuntimePipeline is a deprecated immediate-execution helper backed by the canonical Pipeline runner.
type RuntimePipeline[ContextType any] struct {
	name                    string
	shortCircuitOnException bool
	ended                   bool
	current                 ContextType
	preActions              []registeredAction[ContextType]
	actions                 []registeredAction[ContextType]
	postActions             []registeredAction[ContextType]
	lastResult              *PipelineResult[ContextType]
}

func NewRuntimePipeline[ContextType any](
	name string,
	shortCircuitOnException bool,
	initial ContextType,
) *RuntimePipeline[ContextType] {
	return &RuntimePipeline[ContextType]{
		name:                    name,
		shortCircuitOnException: shortCircuitOnException,
		current:                 initial,
		preActions:              make([]registeredAction[ContextType], 0),
		actions:                 make([]registeredAction[ContextType], 0),
		postActions:             make([]registeredAction[ContextType], 0),
	}
}

func (runtimePipeline *RuntimePipeline[ContextType]) Value() ContextType {
	return runtimePipeline.current
}

func (runtimePipeline *RuntimePipeline[ContextType]) Reset(value ContextType) {
	runtimePipeline.current = value
	runtimePipeline.ended = false
	runtimePipeline.lastResult = nil
}

func (runtimePipeline *RuntimePipeline[ContextType]) ClearRecorded() {
	runtimePipeline.preActions = nil
	runtimePipeline.actions = nil
	runtimePipeline.postActions = nil
}

func (runtimePipeline *RuntimePipeline[ContextType]) AddPreAction(action any) (ContextType, error) {
	return runtimePipeline.addAndExecute(StepPhasePre, action)
}

func (runtimePipeline *RuntimePipeline[ContextType]) AddAction(action any) (ContextType, error) {
	return runtimePipeline.addAndExecute(StepPhaseMain, action)
}

func (runtimePipeline *RuntimePipeline[ContextType]) AddPostAction(action any) (ContextType, error) {
	return runtimePipeline.addAndExecute(StepPhasePost, action)
}

func (runtimePipeline *RuntimePipeline[ContextType]) Freeze() *Pipeline[ContextType] {
	pipeline := NewPipeline[ContextType](runtimePipeline.name, runtimePipeline.shortCircuitOnException)
	for _, registered := range runtimePipeline.preActions {
		pipeline.AddPreActionNamed(registered.name, registered.invoke)
	}
	for _, registered := range runtimePipeline.actions {
		pipeline.AddActionNamed(registered.name, registered.invoke)
	}
	for _, registered := range runtimePipeline.postActions {
		pipeline.AddPostActionNamed(registered.name, registered.invoke)
	}
	return pipeline.Freeze()
}

func (runtimePipeline *RuntimePipeline[ContextType]) LastResult() *PipelineResult[ContextType] {
	return runtimePipeline.lastResult
}

func (runtimePipeline *RuntimePipeline[ContextType]) addAndExecute(
	phase StepPhase,
	action any,
) (ContextType, error) {
	if runtimePipeline.ended {
		return runtimePipeline.current, nil
	}

	registered := registeredAction[ContextType]{
		name:   "",
		invoke: normalizeAction[ContextType](action),
	}
	switch phase {
	case StepPhasePre:
		runtimePipeline.preActions = append(runtimePipeline.preActions, registered)
	case StepPhasePost:
		runtimePipeline.postActions = append(runtimePipeline.postActions, registered)
	default:
		runtimePipeline.actions = append(runtimePipeline.actions, registered)
	}

	temporary := NewPipeline[ContextType](
		runtimePipeline.name+":runtime",
		runtimePipeline.shortCircuitOnException,
	)
	switch phase {
	case StepPhasePre:
		temporary.AddPreAction(registered.invoke)
	case StepPhasePost:
		temporary.AddPostAction(registered.invoke)
	default:
		temporary.AddAction(registered.invoke)
	}

	result := temporary.RunDetailed(runtimePipeline.current)
	runtimePipeline.current = result.Context
	runtimePipeline.ended = result.ShortCircuited
	runtimePipeline.lastResult = &result
	return runtimePipeline.current, nil
}
