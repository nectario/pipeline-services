package core

type PipelineRouter[EventType any, ContextType any] struct {
	route func(EventType) *PipelineProvider[ContextType]
}

func NewPipelineRouter[EventType any, ContextType any](
	route func(EventType) *PipelineProvider[ContextType],
) *PipelineRouter[EventType, ContextType] {
	if route == nil {
		panic("route must not be nil")
	}
	return &PipelineRouter[EventType, ContextType]{route: route}
}

func (router *PipelineRouter[EventType, ContextType]) GetPipelineProvider(
	event EventType,
) *PipelineProvider[ContextType] {
	provider := router.route(event)
	if provider == nil {
		panic("route returned nil PipelineProvider")
	}
	return provider
}

func (router *PipelineRouter[EventType, ContextType]) Run(
	event EventType,
	context ContextType,
) ContextType {
	return router.GetPipelineProvider(event).Run(context)
}
