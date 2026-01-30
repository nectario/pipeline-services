package core

import (
	"fmt"
	"runtime"
	"sync/atomic"
)

type PipelineProviderMode string

const (
	PipelineProviderModeShared PipelineProviderMode = "shared"
	PipelineProviderModePooled PipelineProviderMode = "pooled"
	PipelineProviderModePerRun PipelineProviderMode = "perRun"
)

func DefaultPoolMax() int {
	processorCount := runtime.NumCPU()
	computed := processorCount * 8
	if computed < 1 {
		computed = 1
	}
	if computed > 256 {
		computed = 256
	}
	return computed
}

type PipelineFactory[ContextType any] func() *Pipeline[ContextType]

type pipelinePool[ContextType any] struct {
	maxSize      int32
	createdCount int32
	available    chan *Pipeline[ContextType]
	factory      PipelineFactory[ContextType]
}

func newPipelinePool[ContextType any](maxSize int, factory PipelineFactory[ContextType]) *pipelinePool[ContextType] {
	if maxSize < 1 {
		panic("maxSize must be >= 1")
	}
	if factory == nil {
		panic("factory must not be nil")
	}

	return &pipelinePool[ContextType]{
		maxSize:      int32(maxSize),
		createdCount: 0,
		available:    make(chan *Pipeline[ContextType], maxSize),
		factory:      factory,
	}
}

func (pool *pipelinePool[ContextType]) borrow() *Pipeline[ContextType] {
	select {
	case availablePipeline := <-pool.available:
		return availablePipeline
	default:
	}

	for {
		createdSoFar := atomic.LoadInt32(&pool.createdCount)
		if createdSoFar < pool.maxSize {
			if atomic.CompareAndSwapInt32(&pool.createdCount, createdSoFar, createdSoFar+1) {
				pipelineInstance := pool.factory()
				if pipelineInstance == nil {
					panic("pipeline pool factory returned nil")
				}
				return pipelineInstance
			}
			continue
		}

		availablePipeline := <-pool.available
		return availablePipeline
	}
}

func (pool *pipelinePool[ContextType]) release(pipelineInstance *Pipeline[ContextType]) {
	if pipelineInstance == nil {
		return
	}

	select {
	case pool.available <- pipelineInstance:
	default:
	}
}

type PipelineProvider[ContextType any] struct {
	mode          PipelineProviderMode
	sharedPipeline *Pipeline[ContextType]
	pool          *pipelinePool[ContextType]
	factory       PipelineFactory[ContextType]
}

func NewSharedPipelineProvider[ContextType any](pipeline *Pipeline[ContextType]) *PipelineProvider[ContextType] {
	if pipeline == nil {
		panic("pipeline must not be nil")
	}
	return &PipelineProvider[ContextType]{
		mode:          PipelineProviderModeShared,
		sharedPipeline: pipeline,
		pool:          nil,
		factory:       nil,
	}
}

func NewSharedPipelineProviderFromFactory[ContextType any](factory PipelineFactory[ContextType]) *PipelineProvider[ContextType] {
	if factory == nil {
		panic("factory must not be nil")
	}
	pipeline := factory()
	if pipeline == nil {
		panic("factory returned nil pipeline")
	}
	return NewSharedPipelineProvider(pipeline)
}

func NewPooledPipelineProvider[ContextType any](factory PipelineFactory[ContextType], poolMax int) *PipelineProvider[ContextType] {
	if poolMax <= 0 {
		poolMax = DefaultPoolMax()
	}
	pool := newPipelinePool[ContextType](poolMax, factory)
	return &PipelineProvider[ContextType]{
		mode:          PipelineProviderModePooled,
		sharedPipeline: nil,
		pool:          pool,
		factory:       nil,
	}
}

func NewPerRunPipelineProvider[ContextType any](factory PipelineFactory[ContextType]) *PipelineProvider[ContextType] {
	if factory == nil {
		panic("factory must not be nil")
	}
	return &PipelineProvider[ContextType]{
		mode:          PipelineProviderModePerRun,
		sharedPipeline: nil,
		pool:          nil,
		factory:       factory,
	}
}

func (provider *PipelineProvider[ContextType]) Mode() PipelineProviderMode {
	return provider.mode
}

func (provider *PipelineProvider[ContextType]) Run(input ContextType) PipelineResult[ContextType] {
	if provider.mode == PipelineProviderModeShared {
		if provider.sharedPipeline == nil {
			panic("sharedPipeline is not set")
		}
		return provider.sharedPipeline.Run(input)
	}

	if provider.mode == PipelineProviderModePooled {
		if provider.pool == nil {
			panic("pool is not set")
		}
		borrowedPipeline := provider.pool.borrow()
		result := borrowedPipeline.Run(input)
		provider.pool.release(borrowedPipeline)
		return result
	}

	if provider.mode != PipelineProviderModePerRun {
		panic(fmt.Sprintf("unsupported provider mode: %q", provider.mode))
	}
	if provider.factory == nil {
		panic("factory is not set")
	}

	pipeline := provider.factory()
	if pipeline == nil {
		panic("factory returned nil pipeline")
	}
	return pipeline.Run(input)
}

