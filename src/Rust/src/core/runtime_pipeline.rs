use std::panic::AssertUnwindSafe;
use std::sync::Arc;

use crate::core::pipeline::{
  default_on_error, format_step_name, safe_panic_to_string, OnErrorFn, Pipeline, PipelineError,
  RegisteredAction, RegisteredActionKind, StepControl,
};

pub struct RuntimePipeline<ContextType> {
  pub name: String,
  pub short_circuit_on_exception: bool,
  pub on_error: OnErrorFn<ContextType>,

  ended: bool,
  current: Option<ContextType>,

  pre_actions: Vec<RegisteredAction<ContextType>>,
  actions: Vec<RegisteredAction<ContextType>>,
  post_actions: Vec<RegisteredAction<ContextType>>,

  pre_index: usize,
  action_index: usize,
  post_index: usize,

  control: StepControl<ContextType>,
}

impl<ContextType> RuntimePipeline<ContextType>
where
  ContextType: Clone + 'static,
{
  pub fn new(name: impl Into<String>, short_circuit_on_exception: bool, initial: ContextType) -> Self {
    let name_value = name.into();
    let on_error: OnErrorFn<ContextType> = Arc::new(default_on_error);
    let control = StepControl::new(name_value.clone(), on_error.clone());

    Self {
      name: name_value,
      short_circuit_on_exception,
      on_error,
      ended: false,
      current: Some(initial),
      pre_actions: Vec::new(),
      actions: Vec::new(),
      post_actions: Vec::new(),
      pre_index: 0,
      action_index: 0,
      post_index: 0,
      control,
    }
  }

  pub fn value(&self) -> Option<&ContextType> {
    self.current.as_ref()
  }

  pub fn reset(&mut self, value: ContextType) {
    self.current = Some(value);
    self.ended = false;
    self.control.reset();
  }

  pub fn on_error_handler<ErrorHandler>(&mut self, handler: ErrorHandler)
  where
    ErrorHandler: Fn(ContextType, PipelineError) -> ContextType + Send + Sync + 'static,
  {
    self.on_error = Arc::new(handler);
    self.control.on_error = self.on_error.clone();
  }

  pub fn add_pre_action<ActionFn>(&mut self, action: ActionFn) -> Option<&ContextType>
  where
    ActionFn: Fn(ContextType) -> ContextType + Send + Sync + 'static,
  {
    let registered_action = RegisteredAction {
      name: "".to_string(),
      kind: RegisteredActionKind::Unary(Arc::new(action)),
    };
    self.pre_actions.push(registered_action.clone());
    let index_value = self.pre_index;
    self.pre_index += 1;
    let output_value = self.apply_action(registered_action, "pre", index_value);
    output_value
  }

  pub fn add_action<ActionFn>(&mut self, action: ActionFn) -> Option<&ContextType>
  where
    ActionFn: Fn(ContextType) -> ContextType + Send + Sync + 'static,
  {
    let registered_action = RegisteredAction {
      name: "".to_string(),
      kind: RegisteredActionKind::Unary(Arc::new(action)),
    };
    self.actions.push(registered_action.clone());
    let index_value = self.action_index;
    self.action_index += 1;
    let output_value = self.apply_action(registered_action, "main", index_value);
    output_value
  }

  pub fn add_action_control<ActionFn>(&mut self, action: ActionFn) -> Option<&ContextType>
  where
    ActionFn: Fn(ContextType, &mut StepControl<ContextType>) -> ContextType + Send + Sync + 'static,
  {
    let registered_action = RegisteredAction {
      name: "".to_string(),
      kind: RegisteredActionKind::StepAction(Arc::new(action)),
    };
    self.actions.push(registered_action.clone());
    let index_value = self.action_index;
    self.action_index += 1;
    let output_value = self.apply_action(registered_action, "main", index_value);
    output_value
  }

  pub fn add_post_action<ActionFn>(&mut self, action: ActionFn) -> Option<&ContextType>
  where
    ActionFn: Fn(ContextType) -> ContextType + Send + Sync + 'static,
  {
    let registered_action = RegisteredAction {
      name: "".to_string(),
      kind: RegisteredActionKind::Unary(Arc::new(action)),
    };
    self.post_actions.push(registered_action.clone());
    let index_value = self.post_index;
    self.post_index += 1;
    let output_value = self.apply_action(registered_action, "post", index_value);
    output_value
  }

  pub fn add_post_action_control<ActionFn>(&mut self, action: ActionFn) -> Option<&ContextType>
  where
    ActionFn: Fn(ContextType, &mut StepControl<ContextType>) -> ContextType + Send + Sync + 'static,
  {
    let registered_action = RegisteredAction {
      name: "".to_string(),
      kind: RegisteredActionKind::StepAction(Arc::new(action)),
    };
    self.post_actions.push(registered_action.clone());
    let index_value = self.post_index;
    self.post_index += 1;
    let output_value = self.apply_action(registered_action, "post", index_value);
    output_value
  }

  pub fn freeze(&self) -> Pipeline<ContextType> {
    self.to_immutable()
  }

  pub fn to_immutable(&self) -> Pipeline<ContextType> {
    let mut pipeline = Pipeline::new(self.name.clone(), self.short_circuit_on_exception);
    pipeline.on_error = self.on_error.clone();
    pipeline.pre_actions = self.pre_actions.clone();
    pipeline.actions = self.actions.clone();
    pipeline.post_actions = self.post_actions.clone();
    pipeline
  }

  fn apply_action(
    &mut self,
    registered_action: RegisteredAction<ContextType>,
    phase: &str,
    index: usize,
  ) -> Option<&ContextType> {
    if self.ended {
      return self.current.as_ref();
    }

    let current_value = match self.current.take() {
      Some(value) => value,
      None => return None,
    };

    let step_name = format_step_name(phase, index, &registered_action.name);
    self.control.begin_step(phase.to_string(), index, step_name);

    match registered_action.kind {
      RegisteredActionKind::Unary(unary_action) => {
        let ctx_before_step = current_value.clone();
        let call_result = std::panic::catch_unwind(AssertUnwindSafe(|| (unary_action)(current_value)));
        match call_result {
          Ok(output_value) => {
            self.current = Some(output_value);
          }
          Err(payload) => {
            let message = safe_panic_to_string(payload);
            let updated = self.control.record_error(ctx_before_step, message);
            self.current = Some(updated);
            if self.short_circuit_on_exception {
              self.control.short_circuit();
              self.ended = true;
            }
          }
        }
      }
      RegisteredActionKind::StepAction(step_action) => {
        let ctx_before_step = current_value.clone();
        let call_result =
          std::panic::catch_unwind(AssertUnwindSafe(|| (step_action)(current_value, &mut self.control)));
        match call_result {
          Ok(output_value) => {
            self.current = Some(output_value);
          }
          Err(payload) => {
            let message = safe_panic_to_string(payload);
            let updated = self.control.record_error(ctx_before_step, message);
            self.current = Some(updated);
            if self.short_circuit_on_exception {
              self.control.short_circuit();
              self.ended = true;
            }
          }
        }
      }
    }

    if self.control.is_short_circuited() {
      self.ended = true;
    }

    self.current.as_ref()
  }
}
