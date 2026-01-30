use std::cmp;
use std::ops::Deref;
use std::panic::AssertUnwindSafe;
use std::sync::{Arc, Condvar, Mutex};

use crate::core::pipeline::{Pipeline, PipelineResult};

#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub enum PipelineProviderMode {
  Shared,
  Pooled,
  PerRun,
}

pub fn default_pool_max() -> usize {
  let processor_count = std::thread::available_parallelism().map(|value| value.get()).unwrap_or(1);
  let computed = processor_count.saturating_mul(8);
  cmp::min(256, cmp::max(1, computed))
}

struct ActionPoolState<ItemType> {
  created_count: usize,
  available: Vec<ItemType>,
}

pub struct ActionPool<ItemType> {
  max_size: usize,
  factory: Arc<dyn Fn() -> ItemType + Send + Sync + 'static>,
  state: Mutex<ActionPoolState<ItemType>>,
  condition: Condvar,
}

pub struct BorrowedItem<'pool, ItemType> {
  pool: &'pool ActionPool<ItemType>,
  item: Option<ItemType>,
}

impl<'pool, ItemType> Deref for BorrowedItem<'pool, ItemType> {
  type Target = ItemType;

  fn deref(&self) -> &Self::Target {
    self.item.as_ref().expect("borrowed item is missing")
  }
}

impl<'pool, ItemType> Drop for BorrowedItem<'pool, ItemType> {
  fn drop(&mut self) {
    if let Some(item) = self.item.take() {
      self.pool.release(item);
    }
  }
}

impl<ItemType> ActionPool<ItemType> {
  pub fn new(max_size: usize, factory: Arc<dyn Fn() -> ItemType + Send + Sync + 'static>) -> Self {
    if max_size < 1 {
      panic!("max_size must be >= 1");
    }

    Self {
      max_size,
      factory,
      state: Mutex::new(ActionPoolState {
        created_count: 0,
        available: Vec::new(),
      }),
      condition: Condvar::new(),
    }
  }

  pub fn borrow(&self) -> BorrowedItem<'_, ItemType> {
    loop {
      let should_create = {
        let mut guard = self.state.lock().expect("pool mutex poisoned");
        if let Some(item) = guard.available.pop() {
          return BorrowedItem {
            pool: self,
            item: Some(item),
          };
        }

        if guard.created_count < self.max_size {
          guard.created_count += 1;
          true
        } else {
          while guard.available.is_empty() {
            guard = self.condition.wait(guard).expect("pool mutex poisoned");
          }
          let item = guard.available.pop().expect("available item missing");
          return BorrowedItem {
            pool: self,
            item: Some(item),
          };
        }
      };

      if should_create {
        let create_result = std::panic::catch_unwind(AssertUnwindSafe(|| (self.factory)()));
        match create_result {
          Ok(item) => {
            return BorrowedItem {
              pool: self,
              item: Some(item),
            }
          }
          Err(payload) => {
            let mut guard = self.state.lock().expect("pool mutex poisoned");
            guard.created_count = guard.created_count.saturating_sub(1);
            drop(guard);
            std::panic::resume_unwind(payload);
          }
        }
      }
    }
  }

  fn release(&self, item: ItemType) {
    let mut guard = self.state.lock().expect("pool mutex poisoned");
    guard.available.push(item);
    drop(guard);
    self.condition.notify_one();
  }
}

pub struct PipelineProvider<ContextType>
where
  ContextType: Clone + 'static,
{
  mode: PipelineProviderMode,
  shared_pipeline: Option<Pipeline<ContextType>>,
  pipeline_pool: Option<ActionPool<Pipeline<ContextType>>>,
  pipeline_factory: Option<Arc<dyn Fn() -> Pipeline<ContextType> + Send + Sync + 'static>>,
}

impl<ContextType> PipelineProvider<ContextType>
where
  ContextType: Clone + 'static,
{
  pub fn shared(pipeline: Pipeline<ContextType>) -> Self {
    Self {
      mode: PipelineProviderMode::Shared,
      shared_pipeline: Some(pipeline),
      pipeline_pool: None,
      pipeline_factory: None,
    }
  }

  pub fn pooled<FactoryFn>(factory: FactoryFn, pool_max: usize) -> Self
  where
    FactoryFn: Fn() -> Pipeline<ContextType> + Send + Sync + 'static,
  {
    let factory_arc: Arc<dyn Fn() -> Pipeline<ContextType> + Send + Sync + 'static> = Arc::new(factory);
    let pool = ActionPool::new(pool_max, factory_arc);

    Self {
      mode: PipelineProviderMode::Pooled,
      shared_pipeline: None,
      pipeline_pool: Some(pool),
      pipeline_factory: None,
    }
  }

  pub fn per_run<FactoryFn>(factory: FactoryFn) -> Self
  where
    FactoryFn: Fn() -> Pipeline<ContextType> + Send + Sync + 'static,
  {
    Self {
      mode: PipelineProviderMode::PerRun,
      shared_pipeline: None,
      pipeline_pool: None,
      pipeline_factory: Some(Arc::new(factory)),
    }
  }

  pub fn mode(&self) -> PipelineProviderMode {
    self.mode
  }

  pub fn run(&self, input_value: ContextType) -> PipelineResult<ContextType> {
    match self.mode {
      PipelineProviderMode::Shared => self
        .shared_pipeline
        .as_ref()
        .expect("shared pipeline is not set")
        .run(input_value),
      PipelineProviderMode::Pooled => {
        let pool = self.pipeline_pool.as_ref().expect("pipeline pool is not set");
        let borrowed_pipeline = pool.borrow();
        borrowed_pipeline.run(input_value)
      }
      PipelineProviderMode::PerRun => {
        let factory = self.pipeline_factory.as_ref().expect("pipeline factory is not set");
        let pipeline = factory();
        pipeline.run(input_value)
      }
    }
  }
}
