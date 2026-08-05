use std::sync::Arc;

use crate::core::pipeline::{
  default_on_error, ActionControl, OnErrorFn, Pipeline, PipelineError, PipelineResult, RegisteredAction,
  RegisteredActionKind,
};

#[deprecated(note = "Construct a Pipeline directly.")]
pub struct RuntimePipeline<ContextType>
where
  ContextType: Clone + 'static,
{
  pub name: String,
  pub short_circuit_on_exception: bool,
  pub on_error: OnErrorFn<ContextType>,
  ended: bool,
  current: Option<ContextType>,
  pre_actions: Vec<RegisteredAction<ContextType>>,
  actions: Vec<RegisteredAction<ContextType>>,
  post_actions: Vec<RegisteredAction<ContextType>>,
  pub last_result: Option<PipelineResult<ContextType>>,
}

#[allow(deprecated)]
impl<ContextType> RuntimePipeline<ContextType>
where
  ContextType: Clone + 'static,
{
  pub fn new(name: impl Into<String>, short_circuit_on_exception: bool, initial: ContextType) -> Self {
    Self {
      name: name.into(),
      short_circuit_on_exception,
      on_error: Arc::new(default_on_error),
      ended: false,
      current: Some(initial),
      pre_actions: Vec::new(),
      actions: Vec::new(),
      post_actions: Vec::new(),
      last_result: None,
    }
  }

  pub fn value(&self) -> Option<&ContextType> {
    self.current.as_ref()
  }

  pub fn reset(&mut self, value: ContextType) {
    self.current = Some(value);
    self.ended = false;
    self.last_result = None;
  }

  pub fn clear_recorded(&mut self) {
    self.pre_actions.clear();
    self.actions.clear();
    self.post_actions.clear();
  }

  pub fn recorded_pre_action_count(&self) -> usize {
    self.pre_actions.len()
  }

  pub fn recorded_action_count(&self) -> usize {
    self.actions.len()
  }

  pub fn recorded_post_action_count(&self) -> usize {
    self.post_actions.len()
  }

  pub fn on_error_handler<ErrorHandler>(&mut self, handler: ErrorHandler)
  where
    ErrorHandler: Fn(ContextType, PipelineError) -> ContextType + Send + Sync + 'static,
  {
    self.on_error = Arc::new(handler);
  }

  pub fn add_pre_action<ActionFn>(&mut self, action: ActionFn) -> Option<&ContextType>
  where
    ActionFn: Fn(ContextType) -> ContextType + Send + Sync + 'static,
  {
    let registered = RegisteredAction {
      name: String::new(),
      kind: RegisteredActionKind::Action(Arc::new(action)),
    };
    self.pre_actions.push(registered.clone());
    self.apply_registered("preActions", registered)
  }

  pub fn add_action<ActionFn>(&mut self, action: ActionFn) -> Option<&ContextType>
  where
    ActionFn: Fn(ContextType) -> ContextType + Send + Sync + 'static,
  {
    let registered = RegisteredAction {
      name: String::new(),
      kind: RegisteredActionKind::Action(Arc::new(action)),
    };
    self.actions.push(registered.clone());
    self.apply_registered("actions", registered)
  }

  #[deprecated(note = "Use a one-argument Action and short_circuit().")]
  pub fn add_action_control<ActionFn>(&mut self, action: ActionFn) -> Option<&ContextType>
  where
    ActionFn: Fn(ContextType, &mut ActionControl<ContextType>) -> ContextType + Send + Sync + 'static,
  {
    let registered = RegisteredAction {
      name: String::new(),
      kind: RegisteredActionKind::StepAction(Arc::new(action)),
    };
    self.actions.push(registered.clone());
    self.apply_registered("actions", registered)
  }

  pub fn add_post_action<ActionFn>(&mut self, action: ActionFn) -> Option<&ContextType>
  where
    ActionFn: Fn(ContextType) -> ContextType + Send + Sync + 'static,
  {
    let registered = RegisteredAction {
      name: String::new(),
      kind: RegisteredActionKind::Action(Arc::new(action)),
    };
    self.post_actions.push(registered.clone());
    self.apply_registered("postActions", registered)
  }

  #[deprecated(note = "Use a one-argument Action and short_circuit().")]
  pub fn add_post_action_control<ActionFn>(&mut self, action: ActionFn) -> Option<&ContextType>
  where
    ActionFn: Fn(ContextType, &mut ActionControl<ContextType>) -> ContextType + Send + Sync + 'static,
  {
    let registered = RegisteredAction {
      name: String::new(),
      kind: RegisteredActionKind::StepAction(Arc::new(action)),
    };
    self.post_actions.push(registered.clone());
    self.apply_registered("postActions", registered)
  }

  pub fn freeze(&self) -> Pipeline<ContextType> {
    self.to_immutable()
  }

  pub fn to_immutable(&self) -> Pipeline<ContextType> {
    let mut pipeline = Pipeline::new(self.name.clone(), self.short_circuit_on_exception);
    let error_handler = self.on_error.clone();
    pipeline.on_error_handler(move |context, error| error_handler(context, error));
    for action in &self.pre_actions {
      pipeline.add_registered_pre_action(action.clone());
    }
    for action in &self.actions {
      pipeline.add_registered_action(action.clone());
    }
    for action in &self.post_actions {
      pipeline.add_registered_post_action(action.clone());
    }
    pipeline.freeze();
    pipeline
  }

  fn apply_registered(&mut self, phase: &str, registered: RegisteredAction<ContextType>) -> Option<&ContextType> {
    if self.ended {
      return self.current.as_ref();
    }
    let input = self.current.take()?;
    let mut pipeline = Pipeline::new(format!("{}:runtime", self.name), self.short_circuit_on_exception);
    let error_handler = self.on_error.clone();
    pipeline.on_error_handler(move |context, error| error_handler(context, error));
    match phase {
      "preActions" => pipeline.add_registered_pre_action(registered),
      "postActions" => pipeline.add_registered_post_action(registered),
      _ => pipeline.add_registered_action(registered),
    }

    let result = pipeline.run_detailed(input);
    self.current = Some(result.context.clone());
    self.ended = result.short_circuited;
    self.last_result = Some(result);
    self.current.as_ref()
  }
}
