use std::any::Any;
use std::panic::AssertUnwindSafe;
use std::sync::Arc;
use std::time::Instant;

pub type UnaryOperator<ContextType> = Arc<dyn Fn(ContextType) -> ContextType + Send + Sync + 'static>;
pub type StepAction<ContextType> =
  Arc<dyn Fn(ContextType, &mut StepControl<ContextType>) -> ContextType + Send + Sync + 'static>;
pub type OnErrorFn<ContextType> =
  Arc<dyn Fn(ContextType, PipelineError) -> ContextType + Send + Sync + 'static>;

#[derive(Clone, Debug, PartialEq, Eq)]
pub struct PipelineError {
  pub pipeline: String,
  pub phase: String,
  pub index: usize,
  pub action_name: String,
  pub message: String,
}

#[derive(Clone, Debug, PartialEq, Eq)]
pub struct ActionTiming {
  pub phase: String,
  pub index: usize,
  pub action_name: String,
  pub elapsed_nanos: u128,
  pub success: bool,
}

pub fn default_on_error<ContextType>(ctx: ContextType, error: PipelineError) -> ContextType {
  drop(error);
  ctx
}

pub struct StepControl<ContextType> {
  pub pipeline_name: String,
  pub on_error: OnErrorFn<ContextType>,
  pub errors: Vec<PipelineError>,
  pub timings: Vec<ActionTiming>,
  pub short_circuited: bool,

  pub phase: String,
  pub index: usize,
  pub action_name: String,

  pub run_start_instant: Option<Instant>,
}

impl<ContextType> StepControl<ContextType> {
  pub fn new(pipeline_name: impl Into<String>, on_error: OnErrorFn<ContextType>) -> Self {
    Self {
      pipeline_name: pipeline_name.into(),
      on_error,
      errors: Vec::new(),
      timings: Vec::new(),
      short_circuited: false,
      phase: "main".to_string(),
      index: 0,
      action_name: "?".to_string(),
      run_start_instant: None,
    }
  }

  pub fn begin_step(&mut self, phase: impl Into<String>, index: usize, action_name: impl Into<String>) {
    self.phase = phase.into();
    self.index = index;
    self.action_name = action_name.into();
  }

  pub fn begin_run(&mut self) {
    self.run_start_instant = Some(Instant::now());
  }

  pub fn reset(&mut self) {
    self.short_circuited = false;
    self.errors.clear();
    self.timings.clear();
    self.phase = "main".to_string();
    self.index = 0;
    self.action_name = "?".to_string();
    self.run_start_instant = None;
  }

  pub fn short_circuit(&mut self) {
    self.short_circuited = true;
  }

  pub fn is_short_circuited(&self) -> bool {
    self.short_circuited
  }

  pub fn record_error(&mut self, ctx: ContextType, message: impl Into<String>) -> ContextType {
    let pipeline_error = PipelineError {
      pipeline: self.pipeline_name.clone(),
      phase: self.phase.clone(),
      index: self.index,
      action_name: self.action_name.clone(),
      message: message.into(),
    };
    self.errors.push(pipeline_error.clone());
    (self.on_error)(ctx, pipeline_error)
  }

  pub fn record_timing(&mut self, elapsed_nanos: u128, success: bool) {
    let timing = ActionTiming {
      phase: self.phase.clone(),
      index: self.index,
      action_name: self.action_name.clone(),
      elapsed_nanos,
      success,
    };
    self.timings.push(timing);
  }

  pub fn run_elapsed_nanos(&self) -> u128 {
    match self.run_start_instant {
      Some(run_start_instant) => run_start_instant.elapsed().as_nanos(),
      None => 0,
    }
  }
}

#[derive(Clone, Debug)]
pub struct PipelineResult<ContextType> {
  pub context: ContextType,
  pub short_circuited: bool,
  pub errors: Vec<PipelineError>,
  pub timings: Vec<ActionTiming>,
  pub total_nanos: u128,
}

impl<ContextType> PipelineResult<ContextType> {
  pub fn has_errors(&self) -> bool {
    !self.errors.is_empty()
  }
}

#[derive(Clone)]
pub enum RegisteredActionKind<ContextType> {
  Unary(UnaryOperator<ContextType>),
  StepAction(StepAction<ContextType>),
}

#[derive(Clone)]
pub struct RegisteredAction<ContextType> {
  pub name: String,
  pub kind: RegisteredActionKind<ContextType>,
}

pub fn format_action_name(phase: &str, index: usize, name: &str) -> String {
  let mut prefix = "s";
  if phase == "pre" {
    prefix = "pre";
  } else if phase == "post" {
    prefix = "post";
  }

  if name.is_empty() {
    return format!("{prefix}{index}");
  }
  format!("{prefix}{index}:{name}")
}

pub fn format_step_name(phase: &str, index: usize, name: &str) -> String {
  format_action_name(phase, index, name)
}

pub fn safe_panic_to_string(payload: Box<dyn Any + Send>) -> String {
  if let Some(message) = payload.downcast_ref::<&str>() {
    return message.to_string();
  }
  if let Some(message) = payload.downcast_ref::<String>() {
    return message.clone();
  }
  "panic".to_string()
}

pub struct Pipeline<ContextType> {
  pub name: String,
  pub short_circuit_on_exception: bool,
  pub on_error: OnErrorFn<ContextType>,

  pub pre_actions: Vec<RegisteredAction<ContextType>>,
  pub actions: Vec<RegisteredAction<ContextType>>,
  pub post_actions: Vec<RegisteredAction<ContextType>>,
}

impl<ContextType> Pipeline<ContextType>
where
  ContextType: 'static,
{
  pub fn new(name: impl Into<String>, short_circuit_on_exception: bool) -> Self {
    Self {
      name: name.into(),
      short_circuit_on_exception,
      on_error: Arc::new(default_on_error),
      pre_actions: Vec::new(),
      actions: Vec::new(),
      post_actions: Vec::new(),
    }
  }

  pub fn on_error_handler<ErrorHandler>(&mut self, handler: ErrorHandler) -> &mut Self
  where
    ErrorHandler: Fn(ContextType, PipelineError) -> ContextType + Send + Sync + 'static,
  {
    self.on_error = Arc::new(handler);
    self
  }

  pub fn add_pre_action<ActionFn>(&mut self, action: ActionFn) -> &mut Self
  where
    ActionFn: Fn(ContextType) -> ContextType + Send + Sync + 'static,
  {
    self.add_pre_action_named("", action)
  }

  pub fn add_pre_action_named<ActionFn>(&mut self, name: impl Into<String>, action: ActionFn) -> &mut Self
  where
    ActionFn: Fn(ContextType) -> ContextType + Send + Sync + 'static,
  {
    self.pre_actions.push(RegisteredAction {
      name: name.into(),
      kind: RegisteredActionKind::Unary(Arc::new(action)),
    });
    self
  }

  pub fn add_pre_action_control<ActionFn>(&mut self, action: ActionFn) -> &mut Self
  where
    ActionFn: Fn(ContextType, &mut StepControl<ContextType>) -> ContextType + Send + Sync + 'static,
  {
    self.add_pre_action_control_named("", action)
  }

  pub fn add_pre_action_control_named<ActionFn>(
    &mut self,
    name: impl Into<String>,
    action: ActionFn,
  ) -> &mut Self
  where
    ActionFn: Fn(ContextType, &mut StepControl<ContextType>) -> ContextType + Send + Sync + 'static,
  {
    self.pre_actions.push(RegisteredAction {
      name: name.into(),
      kind: RegisteredActionKind::StepAction(Arc::new(action)),
    });
    self
  }

  pub fn add_action<ActionFn>(&mut self, action: ActionFn) -> &mut Self
  where
    ActionFn: Fn(ContextType) -> ContextType + Send + Sync + 'static,
  {
    self.add_action_named("", action)
  }

  pub fn add_action_named<ActionFn>(&mut self, name: impl Into<String>, action: ActionFn) -> &mut Self
  where
    ActionFn: Fn(ContextType) -> ContextType + Send + Sync + 'static,
  {
    self.actions.push(RegisteredAction {
      name: name.into(),
      kind: RegisteredActionKind::Unary(Arc::new(action)),
    });
    self
  }

  pub fn add_action_control<ActionFn>(&mut self, action: ActionFn) -> &mut Self
  where
    ActionFn: Fn(ContextType, &mut StepControl<ContextType>) -> ContextType + Send + Sync + 'static,
  {
    self.add_action_control_named("", action)
  }

  pub fn add_action_control_named<ActionFn>(
    &mut self,
    name: impl Into<String>,
    action: ActionFn,
  ) -> &mut Self
  where
    ActionFn: Fn(ContextType, &mut StepControl<ContextType>) -> ContextType + Send + Sync + 'static,
  {
    self.actions.push(RegisteredAction {
      name: name.into(),
      kind: RegisteredActionKind::StepAction(Arc::new(action)),
    });
    self
  }

  pub fn add_post_action<ActionFn>(&mut self, action: ActionFn) -> &mut Self
  where
    ActionFn: Fn(ContextType) -> ContextType + Send + Sync + 'static,
  {
    self.add_post_action_named("", action)
  }

  pub fn add_post_action_named<ActionFn>(&mut self, name: impl Into<String>, action: ActionFn) -> &mut Self
  where
    ActionFn: Fn(ContextType) -> ContextType + Send + Sync + 'static,
  {
    self.post_actions.push(RegisteredAction {
      name: name.into(),
      kind: RegisteredActionKind::Unary(Arc::new(action)),
    });
    self
  }

  pub fn add_post_action_control<ActionFn>(&mut self, action: ActionFn) -> &mut Self
  where
    ActionFn: Fn(ContextType, &mut StepControl<ContextType>) -> ContextType + Send + Sync + 'static,
  {
    self.add_post_action_control_named("", action)
  }

  pub fn add_post_action_control_named<ActionFn>(
    &mut self,
    name: impl Into<String>,
    action: ActionFn,
  ) -> &mut Self
  where
    ActionFn: Fn(ContextType, &mut StepControl<ContextType>) -> ContextType + Send + Sync + 'static,
  {
    self.post_actions.push(RegisteredAction {
      name: name.into(),
      kind: RegisteredActionKind::StepAction(Arc::new(action)),
    });
    self
  }
}

impl<ContextType> Pipeline<ContextType>
where
  ContextType: Clone + 'static,
{
  pub fn run(&self, input_value: ContextType) -> ContextType {
    self.execute(input_value).context
  }

  pub fn execute(&self, input_value: ContextType) -> PipelineResult<ContextType> {
    let mut ctx = input_value;
    let mut control = StepControl::new(self.name.clone(), self.on_error.clone());
    control.begin_run();

    ctx = self.run_phase("pre", ctx, &self.pre_actions, &mut control, false);
    if !control.is_short_circuited() {
      ctx = self.run_phase("main", ctx, &self.actions, &mut control, true);
    }
    ctx = self.run_phase("post", ctx, &self.post_actions, &mut control, false);

    let total_nanos = control.run_elapsed_nanos();
    PipelineResult {
      context: ctx,
      short_circuited: control.is_short_circuited(),
      errors: control.errors.clone(),
      timings: control.timings.clone(),
      total_nanos,
    }
  }

  fn run_phase(
    &self,
    phase: &str,
    start_ctx: ContextType,
    actions: &[RegisteredAction<ContextType>],
    control: &mut StepControl<ContextType>,
    stop_on_short_circuit: bool,
  ) -> ContextType {
    let mut ctx = start_ctx;
    let mut step_index: usize = 0;
    while step_index < actions.len() {
      let registered_action = actions[step_index].clone();
      let action_name = format_action_name(phase, step_index, &registered_action.name);
      control.begin_step(phase.to_string(), step_index, action_name);

      let step_start_instant = Instant::now();
      let mut step_succeeded = true;

      match registered_action.kind {
        RegisteredActionKind::Unary(unary_action) => {
          let ctx_before_step = ctx.clone();
          let call_result = std::panic::catch_unwind(AssertUnwindSafe(|| (unary_action)(ctx)));
          match call_result {
            Ok(output_ctx) => {
              ctx = output_ctx;
            }
            Err(payload) => {
              step_succeeded = false;
              ctx = control.record_error(ctx_before_step, safe_panic_to_string(payload));
              if self.short_circuit_on_exception {
                control.short_circuit();
              }
            }
          }
        }
        RegisteredActionKind::StepAction(step_action) => {
          let ctx_before_step = ctx.clone();
          let call_result =
            std::panic::catch_unwind(AssertUnwindSafe(|| (step_action)(ctx, control)));
          match call_result {
            Ok(output_ctx) => {
              ctx = output_ctx;
            }
            Err(payload) => {
              step_succeeded = false;
              ctx = control.record_error(ctx_before_step, safe_panic_to_string(payload));
              if self.short_circuit_on_exception {
                control.short_circuit();
              }
            }
          }
        }
      }

      let step_elapsed_nanos = step_start_instant.elapsed().as_nanos();
      control.record_timing(step_elapsed_nanos, step_succeeded);

      if stop_on_short_circuit && control.is_short_circuited() {
        break;
      }
      step_index += 1;
    }
    ctx
  }
}
