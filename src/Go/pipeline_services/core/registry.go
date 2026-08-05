package core

import "fmt"

type PipelineRegistry[ContextType any] struct {
	actions      map[string]any
	unaryActions map[string]func(ContextType) ContextType
}

func NewPipelineRegistry[ContextType any]() *PipelineRegistry[ContextType] {
	return &PipelineRegistry[ContextType]{
		actions:      map[string]any{},
		unaryActions: map[string]func(ContextType) ContextType{},
	}
}

func (registry *PipelineRegistry[ContextType]) RegisterUnary(
	name string,
	action func(ContextType) ContextType,
) {
	if action == nil {
		panic("action must not be nil")
	}
	registry.unaryActions[name] = action
}

func (registry *PipelineRegistry[ContextType]) RegisterAction(name string, action any) {
	if action == nil {
		panic("action must not be nil")
	}
	// Validate eagerly while retaining the original callable shape for the
	// Pipeline's one normalization path.
	_ = normalizeAction[ContextType](action)
	registry.actions[name] = action
}

func (registry *PipelineRegistry[ContextType]) HasUnary(name string) bool {
	_, found := registry.unaryActions[name]
	return found
}

func (registry *PipelineRegistry[ContextType]) HasAction(name string) bool {
	_, found := registry.actions[name]
	return found
}

func (registry *PipelineRegistry[ContextType]) GetUnary(
	name string,
) (func(ContextType) ContextType, error) {
	action, found := registry.unaryActions[name]
	if !found {
		return nil, fmt.Errorf("unknown unary Action: %s", name)
	}
	return action, nil
}

func (registry *PipelineRegistry[ContextType]) GetAction(
	name string,
) (any, error) {
	action, found := registry.actions[name]
	if !found {
		return nil, fmt.Errorf("unknown Action: %s", name)
	}
	return action, nil
}
