use std::sync::Arc;

use crate::core::pipeline::Pipeline;
use crate::core::pipeline_provider::PipelineProvider;

pub struct PipelineRouter<EventType, ContextType>
where
  ContextType: Clone + 'static,
{
  route: Arc<dyn Fn(&EventType) -> Arc<PipelineProvider<ContextType>> + Send + Sync + 'static>,
}

impl<EventType, ContextType> PipelineRouter<EventType, ContextType>
where
  ContextType: Clone + 'static,
{
  pub fn new<RouteFn>(route: RouteFn) -> Self
  where
    RouteFn: Fn(&EventType) -> Arc<PipelineProvider<ContextType>> + Send + Sync + 'static,
  {
    Self {
      route: Arc::new(route),
    }
  }

  pub fn get_pipeline_provider(&self, event: &EventType) -> Arc<PipelineProvider<ContextType>> {
    (self.route)(event)
  }

  pub fn run(&self, event: &EventType, context: ContextType) -> ContextType {
    self.get_pipeline_provider(event).run(context)
  }

  pub fn get_pipeline(&self, event: &EventType) -> Arc<Pipeline<ContextType>> {
    self.get_pipeline_provider(event).get_pipeline()
  }
}
