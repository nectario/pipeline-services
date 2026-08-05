package core

import (
	"runtime"
	"sync/atomic"
)

type PipelineProviderMode string

const (
	PipelineProviderModeNewInstancePerEvent PipelineProviderMode = "newInstancePerEvent"
	PipelineProviderModeSingleton           PipelineProviderMode = "singleton"
	PipelineProviderModePooled              PipelineProviderMode = "pooled"

	// Preview compatibility aliases.
	PipelineProviderModeShared = PipelineProviderModeSingleton
	PipelineProviderModePerRun = PipelineProviderModeNewInstancePerEvent
)

func DefaultInstanceCount() int {
	count := runtime.NumCPU()
	if count < 1 {
		return 1
	}
	return count
}

func DefaultPoolMax() int {
	return DefaultInstanceCount()
}

type PipelineFactory[ContextType any] func() *Pipeline[ContextType]

type PipelineProvider[ContextType any] struct {
	mode              PipelineProviderMode
	factory           PipelineFactory[ContextType]
	singletonPipeline *Pipeline[ContextType]
	pooledPipelines   []*Pipeline[ContextType]
	nextSelection     atomic.Uint64
}

func NewInstancePerEventPipelineProvider[ContextType any](
	factory PipelineFactory[ContextType],
) *PipelineProvider[ContextType] {
	if factory == nil {
		panic("factory must not be nil")
	}
	return &PipelineProvider[ContextType]{
		mode: PipelineProviderModeNewInstancePerEvent,
		factory: factory,
		pooledPipelines: make([]*Pipeline[ContextType], 0),
	}
}

func NewSingletonPipelineProvider[ContextType any](
	pipeline *Pipeline[ContextType],
) *PipelineProvider[ContextType] {
	if pipeline == nil {
		panic("pipeline must not be nil")
	}
	pipeline.Freeze()
	return &PipelineProvider[ContextType]{
		mode: PipelineProviderModeSingleton,
		singletonPipeline: pipeline,
		pooledPipelines: make([]*Pipeline[ContextType], 0),
	}
}

func NewSingletonPipelineProviderFromFactory[ContextType any](
	factory PipelineFactory[ContextType],
) *PipelineProvider[ContextType] {
	if factory == nil {
		panic("factory must not be nil")
	}
	return NewSingletonPipelineProvider(factory())
}

func NewPooledPipelineProvider[ContextType any](
	factory PipelineFactory[ContextType],
	instanceCount int,
) *PipelineProvider[ContextType] {
	if factory == nil {
		panic("factory must not be nil")
	}
	if instanceCount <= 0 {
		instanceCount = DefaultInstanceCount()
	}
	pipelines := make([]*Pipeline[ContextType], 0, instanceCount)
	for index := 0; index < instanceCount; index++ {
		pipeline := factory()
		if pipeline == nil {
			panic("factory returned nil Pipeline")
		}
		pipeline.Freeze()
		pipelines = append(pipelines, pipeline)
	}
	return &PipelineProvider[ContextType]{
		mode: PipelineProviderModePooled,
		pooledPipelines: pipelines,
	}
}

// Preview compatibility constructors.
func NewSharedPipelineProvider[ContextType any](
	pipeline *Pipeline[ContextType],
) *PipelineProvider[ContextType] {
	return NewSingletonPipelineProvider(pipeline)
}

func NewSharedPipelineProviderFromFactory[ContextType any](
	factory PipelineFactory[ContextType],
) *PipelineProvider[ContextType] {
	return NewSingletonPipelineProviderFromFactory(factory)
}

func NewPerRunPipelineProvider[ContextType any](
	factory PipelineFactory[ContextType],
) *PipelineProvider[ContextType] {
	return NewInstancePerEventPipelineProvider(factory)
}

func (provider *PipelineProvider[ContextType]) Mode() PipelineProviderMode {
	return provider.mode
}

func (provider *PipelineProvider[ContextType]) InstanceCount() int {
	switch provider.mode {
	case PipelineProviderModeNewInstancePerEvent:
		return 0
	case PipelineProviderModeSingleton:
		return 1
	case PipelineProviderModePooled:
		return len(provider.pooledPipelines)
	default:
		panic("unsupported PipelineProvider mode")
	}
}

func (provider *PipelineProvider[ContextType]) GetPipeline() *Pipeline[ContextType] {
	switch provider.mode {
	case PipelineProviderModeNewInstancePerEvent:
		if provider.factory == nil {
			panic("factory is not set")
		}
		pipeline := provider.factory()
		if pipeline == nil {
			panic("factory returned nil Pipeline")
		}
		return pipeline.Freeze()
	case PipelineProviderModeSingleton:
		if provider.singletonPipeline == nil {
			panic("singleton Pipeline is not set")
		}
		return provider.singletonPipeline
	case PipelineProviderModePooled:
		if len(provider.pooledPipelines) == 0 {
			panic("pooled Pipelines are empty")
		}
		selection := provider.nextSelection.Add(1) - 1
		return provider.pooledPipelines[int(selection%uint64(len(provider.pooledPipelines)))]
	default:
		panic("unsupported PipelineProvider mode")
	}
}

func (provider *PipelineProvider[ContextType]) Run(input ContextType) ContextType {
	return provider.GetPipeline().Run(input)
}

func (provider *PipelineProvider[ContextType]) RunDetailed(
	input ContextType,
) PipelineResult[ContextType] {
	return provider.GetPipeline().RunDetailed(input)
}
