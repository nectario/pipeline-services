package core

import (
	"fmt"
)

type PipelineRegistry[ContextType any] struct {
	unaryActions map[string]func(ContextType) ContextType
	stepActions  map[string]StepAction[ContextType]
}

func NewPipelineRegistry[ContextType any]() *PipelineRegistry[ContextType] {
	return &PipelineRegistry[ContextType]{
		unaryActions: map[string]func(ContextType) ContextType{},
		stepActions:  map[string]StepAction[ContextType]{},
	}
}

func (registry *PipelineRegistry[ContextType]) RegisterUnary(name string, action func(ContextType) ContextType) {
	registry.unaryActions[name] = action
}

func (registry *PipelineRegistry[ContextType]) RegisterAction(name string, action StepAction[ContextType]) {
	registry.stepActions[name] = action
}

func (registry *PipelineRegistry[ContextType]) HasUnary(name string) bool {
	action := registry.unaryActions[name]
	return action != nil
}

func (registry *PipelineRegistry[ContextType]) HasAction(name string) bool {
	action := registry.stepActions[name]
	return action != nil
}

func (registry *PipelineRegistry[ContextType]) GetUnary(name string) (func(ContextType) ContextType, error) {
	action, found := registry.unaryActions[name]
	if !found {
		return nil, fmt.Errorf("unknown unary action: %s", name)
	}
	return action, nil
}

func (registry *PipelineRegistry[ContextType]) GetAction(name string) (StepAction[ContextType], error) {
	action, found := registry.stepActions[name]
	if !found {
		return nil, fmt.Errorf("unknown step action: %s", name)
	}
	return action, nil
}
