use std::collections::HashMap;
use std::sync::Arc;

use crate::core::pipeline::{ActionControl, StepAction, UnaryOperator};

pub struct PipelineRegistry<ContextType> {
  unary_actions: HashMap<String, UnaryOperator<ContextType>>,
  step_actions: HashMap<String, StepAction<ContextType>>,
}

impl<ContextType> PipelineRegistry<ContextType> {
  pub fn new() -> Self {
    Self {
      unary_actions: HashMap::new(),
      step_actions: HashMap::new(),
    }
  }

  pub fn register_unary<ActionFn>(&mut self, name: impl Into<String>, action: ActionFn)
  where
    ActionFn: Fn(ContextType) -> ContextType + Send + Sync + 'static,
  {
    self.unary_actions.insert(name.into(), Arc::new(action));
  }

  pub fn register_action<ActionFn>(&mut self, name: impl Into<String>, action: ActionFn)
  where
    ActionFn: Fn(ContextType, &mut ActionControl<ContextType>) -> ContextType + Send + Sync + 'static,
  {
    self.step_actions.insert(name.into(), Arc::new(action));
  }

  pub fn has_unary(&self, name: &str) -> bool {
    self.unary_actions.contains_key(name)
  }

  pub fn has_action(&self, name: &str) -> bool {
    self.step_actions.contains_key(name)
  }

  pub fn get_unary(&self, name: &str) -> Result<UnaryOperator<ContextType>, String> {
    match self.unary_actions.get(name) {
      Some(action) => Ok(action.clone()),
      None => Err(format!("Unknown unary action: {name}")),
    }
  }

  pub fn get_action(&self, name: &str) -> Result<StepAction<ContextType>, String> {
    match self.step_actions.get(name) {
      Some(action) => Ok(action.clone()),
      None => Err(format!("Unknown step action: {name}")),
    }
  }
}

impl<ContextType> Default for PipelineRegistry<ContextType> {
  fn default() -> Self {
    Self::new()
  }
}
