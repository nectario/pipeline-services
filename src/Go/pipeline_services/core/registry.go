package core

import "fmt"

type PipelineRegistry[ContextType any] struct {
	actions      map[string]Action[ContextType]
	unaryActions map[string]func(ContextType) ContextType
}

func NewPipelineRegistry[ContextType any]() *PipelineRegistry[ContextType] {
	return &PipelineRegistry[ContextType]{
		actions:      map[string]Action[ContextType]{},
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
	registry.actions[name] = normalizeAction[ContextType](action)
}

func (registry *PipelineRegistry[ContextType]) HasUnary(name string) bool {
	return registry.unaryActions[name] != nil
}

func (registry *PipelineRegistry[ContextType]) HasAction(name string) bool {
	return registry.actions[name] != nil
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
) (Action[ContextType], error) {
	action, found := registry.actions[name]
	if !found {
		return nil, fmt.Errorf("unknown Action: %s", name)
	}
	return action, nil
}
