use std::sync::atomic::{AtomicUsize, Ordering};
use std::sync::Arc;

use crate::core::pipeline::{Pipeline, PipelineResult};

#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub enum PipelineProviderMode {
  NewInstancePerEvent,
  Singleton,
  Pooled,
}

pub fn default_instance_count() -> usize {
  std::thread::available_parallelism()
    .map(|value| value.get())
    .unwrap_or(1)
    .max(1)
}

#[deprecated(note = "Use default_instance_count().")]
pub fn default_pool_max() -> usize {
  default_instance_count()
}

pub struct PipelineProvider<ContextType>
where
  ContextType: Clone + 'static,
{
  mode: PipelineProviderMode,
  pipeline_factory: Option<Arc<dyn Fn() -> Pipeline<ContextType> + Send + Sync + 'static>>,
  singleton_pipeline: Option<Arc<Pipeline<ContextType>>>,
  pooled_pipelines: Vec<Arc<Pipeline<ContextType>>>,
  next_selection: AtomicUsize,
}

impl<ContextType> PipelineProvider<ContextType>
where
  ContextType: Clone + 'static,
{
  pub fn new_instance_per_event<FactoryFn>(factory: FactoryFn) -> Self
  where
    FactoryFn: Fn() -> Pipeline<ContextType> + Send + Sync + 'static,
  {
    Self {
      mode: PipelineProviderMode::NewInstancePerEvent,
      pipeline_factory: Some(Arc::new(factory)),
      singleton_pipeline: None,
      pooled_pipelines: Vec::new(),
      next_selection: AtomicUsize::new(0),
    }
  }

  pub fn singleton(pipeline: Pipeline<ContextType>) -> Self {
    pipeline.freeze();
    Self {
      mode: PipelineProviderMode::Singleton,
      pipeline_factory: None,
      singleton_pipeline: Some(Arc::new(pipeline)),
      pooled_pipelines: Vec::new(),
      next_selection: AtomicUsize::new(0),
    }
  }

  pub fn singleton_factory<FactoryFn>(factory: FactoryFn) -> Self
  where
    FactoryFn: Fn() -> Pipeline<ContextType> + Send + Sync + 'static,
  {
    Self::singleton(factory())
  }

  pub fn pooled<FactoryFn>(factory: FactoryFn, instance_count: usize) -> Self
  where
    FactoryFn: Fn() -> Pipeline<ContextType> + Send + Sync + 'static,
  {
    assert!(instance_count >= 1, "instance_count must be >= 1");
    let mut pipelines = Vec::with_capacity(instance_count);
    for _ in 0..instance_count {
      let pipeline = factory();
      pipeline.freeze();
      pipelines.push(Arc::new(pipeline));
    }

    Self {
      mode: PipelineProviderMode::Pooled,
      pipeline_factory: None,
      singleton_pipeline: None,
      pooled_pipelines: pipelines,
      next_selection: AtomicUsize::new(0),
    }
  }

  pub fn pooled_default<FactoryFn>(factory: FactoryFn) -> Self
  where
    FactoryFn: Fn() -> Pipeline<ContextType> + Send + Sync + 'static,
  {
    Self::pooled(factory, default_instance_count())
  }

  #[deprecated(note = "Use singleton().")]
  pub fn shared(pipeline: Pipeline<ContextType>) -> Self {
    Self::singleton(pipeline)
  }

  #[deprecated(note = "Use new_instance_per_event().")]
  pub fn per_run<FactoryFn>(factory: FactoryFn) -> Self
  where
    FactoryFn: Fn() -> Pipeline<ContextType> + Send + Sync + 'static,
  {
    Self::new_instance_per_event(factory)
  }

  pub fn mode(&self) -> PipelineProviderMode {
    self.mode
  }

  pub fn instance_count(&self) -> usize {
    match self.mode {
      PipelineProviderMode::NewInstancePerEvent => 0,
      PipelineProviderMode::Singleton => 1,
      PipelineProviderMode::Pooled => self.pooled_pipelines.len(),
    }
  }

  pub fn get_pipeline(&self) -> Arc<Pipeline<ContextType>> {
    match self.mode {
      PipelineProviderMode::NewInstancePerEvent => {
        let pipeline = self
          .pipeline_factory
          .as_ref()
          .expect("pipeline factory is not set")();
        pipeline.freeze();
        Arc::new(pipeline)
      }
      PipelineProviderMode::Singleton => self
        .singleton_pipeline
        .as_ref()
        .expect("singleton pipeline is not set")
        .clone(),
      PipelineProviderMode::Pooled => {
        let selection = self.next_selection.fetch_add(1, Ordering::Relaxed);
        self.pooled_pipelines[selection % self.pooled_pipelines.len()].clone()
      }
    }
  }

  pub fn run(&self, input_value: ContextType) -> ContextType {
    self.get_pipeline().run(input_value)
  }

  pub fn run_detailed(&self, input_value: ContextType) -> PipelineResult<ContextType> {
    self.get_pipeline().run_detailed(input_value)
  }
}
