use std::any::Any;
use std::cell::RefCell;
use std::panic::{catch_unwind, resume_unwind, AssertUnwindSafe};
use std::sync::atomic::{AtomicBool, Ordering};
use std::sync::{Arc, OnceLock};
use std::time::Instant;

pub type Action<ContextType> = Arc<dyn Fn(ContextType) -> ContextType + Send + Sync + 'static>;
pub type UnaryOperator<ContextType> = Action<ContextType>;
pub type StepAction<ContextType> =
  Arc<dyn Fn(ContextType, &mut ActionControl<ContextType>) -> ContextType + Send + Sync + 'static>;
pub type OnErrorFn<ContextType> =
  Arc<dyn Fn(ContextType, PipelineError) -> ContextType + Send + Sync + 'static>;

#[derive(Clone, Debug, PartialEq, Eq)]
pub struct PipelineError {
  pub pipeline_name: String,
  pub pipeline: String,
  pub phase: String,
  pub action_index: usize,
  pub index: usize,
  pub action_name: String,
  pub message: String,
}

#[derive(Clone, Debug, PartialEq, Eq)]
pub struct ActionTiming {
  pub phase: String,
  pub action_index: usize,
  pub index: usize,
  pub action_name: String,
  pub elapsed_nanos: u128,
  pub success: bool,
}

#[derive(Clone, Debug)]
pub struct PipelineResult<ContextType> {
  pub context: ContextType,
  pub short_circuited: bool,
  pub errors: Vec<PipelineError>,
  pub action_timings: Vec<ActionTiming>,
  pub timings: Vec<ActionTiming>,
  pub total_nanos: u128,
}

impl<ContextType> PipelineResult<ContextType> {
  pub fn has_errors(&self) -> bool {
    !self.errors.is_empty()
  }
}

pub trait PipelineObserver: Send + Sync + 'static {
  fn on_pipeline_started(&self, _pipeline_name: &str) {}
  fn on_action_started(&self, _pipeline_name: &str, _phase: &str, _index: usize, _name: &str) {}
  fn on_action_completed(
    &self,
    _pipeline_name: &str,
    _phase: &str,
    _index: usize,
    _name: &str,
    _elapsed_nanos: u128,
  ) {
  }
  fn on_action_failed(
    &self,
    _pipeline_name: &str,
    _phase: &str,
    _index: usize,
    _name: &str,
    _message: &str,
    _elapsed_nanos: u128,
  ) {
  }
  fn on_short_circuited(&self, _pipeline_name: &str, _phase: &str, _index: usize, _name: &str) {}
  fn on_pipeline_completed(
    &self,
    _pipeline_name: &str,
    _short_circuited: bool,
    _error_count: usize,
    _elapsed_nanos: u128,
  ) {
  }
}

struct NoopObserver;
impl PipelineObserver for NoopObserver {}

#[derive(Default)]
struct ExecutionSignal {
  short_circuited: AtomicBool,
  action_executing: AtomicBool,
}

thread_local! {
  static EXECUTION_STACK: RefCell<Vec<Arc<ExecutionSignal>>> = const { RefCell::new(Vec::new()) };
}

struct ExecutionScope {
  signal: Arc<ExecutionSignal>,
}

impl ExecutionScope {
  fn open(signal: Arc<ExecutionSignal>) -> Self {
    EXECUTION_STACK.with(|stack| stack.borrow_mut().push(signal.clone()));
    Self { signal }
  }
}

impl Drop for ExecutionScope {
  fn drop(&mut self) {
    EXECUTION_STACK.with(|stack| {
      let mut stack = stack.borrow_mut();
      let current = stack.pop().expect("Pipeline execution scope stack is empty");
      assert!(Arc::ptr_eq(&current, &self.signal), "Pipeline execution scopes closed out of order");
    });
  }
}

pub fn short_circuit() {
  EXECUTION_STACK.with(|stack| {
    let signal = stack
      .borrow()
      .last()
      .cloned()
      .unwrap_or_else(|| panic!("short_circuit() can only be called during an active Pipeline run"));
    assert!(
      signal.action_executing.load(Ordering::Acquire),
      "short_circuit() can only be called while a Pipeline Action is executing"
    );
    signal.short_circuited.store(true, Ordering::Release);
  });
}

pub fn default_on_error<ContextType>(context: ContextType, error: PipelineError) -> ContextType {
  drop(error);
  context
}

pub struct ActionControl<ContextType> {
  pub pipeline_name: String,
  pub on_error: OnErrorFn<ContextType>,
  pub errors: Vec<PipelineError>,
  pub timings: Vec<ActionTiming>,
  pub phase: String,
  pub index: usize,
  pub action_name: String,
  pub run_start_instant: Option<Instant>,
  signal: Arc<ExecutionSignal>,
  collect_timings: bool,
}

impl<ContextType> ActionControl<ContextType> {
  pub fn new(pipeline_name: impl Into<String>, on_error: OnErrorFn<ContextType>) -> Self {
    Self::with_signal(
      pipeline_name.into(),
      on_error,
      Arc::new(ExecutionSignal::default()),
      true,
    )
  }

  fn with_signal(
    pipeline_name: String,
    on_error: OnErrorFn<ContextType>,
    signal: Arc<ExecutionSignal>,
    collect_timings: bool,
  ) -> Self {
    Self {
      pipeline_name,
      on_error,
      errors: Vec::new(),
      timings: Vec::new(),
      phase: "actions".to_string(),
      index: 0,
      action_name: "?".to_string(),
      run_start_instant: None,
      signal,
      collect_timings,
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
    self.signal.short_circuited.store(false, Ordering::Release);
    self.signal.action_executing.store(false, Ordering::Release);
    self.errors.clear();
    self.timings.clear();
    self.phase = "actions".to_string();
    self.index = 0;
    self.action_name = "?".to_string();
    self.run_start_instant = None;
  }

  pub fn short_circuit(&mut self) {
    self.signal.short_circuited.store(true, Ordering::Release);
  }

  pub fn is_short_circuited(&self) -> bool {
    self.signal.short_circuited.load(Ordering::Acquire)
  }

  pub fn record_error(&mut self, context: ContextType, message: impl Into<String>) -> ContextType {
    let error = make_pipeline_error(
      &self.pipeline_name,
      &self.phase,
      self.index,
      &self.action_name,
      message.into(),
    );
    self.errors.push(error.clone());
    let was_executing = self.signal.action_executing.swap(false, Ordering::AcqRel);
    let output = (self.on_error)(context, error);
    self.signal.action_executing.store(was_executing, Ordering::Release);
    output
  }

  pub fn record_timing(&mut self, elapsed_nanos: u128, success: bool) {
    if self.collect_timings {
      self.timings.push(ActionTiming {
        phase: self.phase.clone(),
        action_index: self.index,
        index: self.index,
        action_name: self.action_name.clone(),
        elapsed_nanos,
        success,
      });
    }
  }

  pub fn run_elapsed_nanos(&self) -> u128 {
    self
      .run_start_instant
      .map(|started| started.elapsed().as_nanos())
      .unwrap_or(0)
  }
}

#[deprecated(note = "Renamed to ActionControl.")]
pub type StepControl<ContextType> = ActionControl<ContextType>;

#[derive(Clone)]
pub enum RegisteredActionKind<ContextType> {
  Action(Action<ContextType>),
  StepAction(StepAction<ContextType>),
}

#[derive(Clone)]
pub struct RegisteredAction<ContextType> {
  pub name: String,
  pub kind: RegisteredActionKind<ContextType>,
}

#[derive(Clone)]
struct PipelinePlan<ContextType> {
  pipeline_name: String,
  short_circuit_on_exception: bool,
  on_error: OnErrorFn<ContextType>,
  observer: Arc<dyn PipelineObserver>,
  pre_actions: Vec<RegisteredAction<ContextType>>,
  actions: Vec<RegisteredAction<ContextType>>,
  post_actions: Vec<RegisteredAction<ContextType>>,
}

pub struct Pipeline<ContextType>
where
  ContextType: Clone + 'static,
{
  pub name: String,
  pub short_circuit_on_exception: bool,
  on_error: OnErrorFn<ContextType>,
  observer: Arc<dyn PipelineObserver>,
  pre_actions: Vec<RegisteredAction<ContextType>>,
  actions: Vec<RegisteredAction<ContextType>>,
  post_actions: Vec<RegisteredAction<ContextType>>,
  frozen_plan: OnceLock<Arc<PipelinePlan<ContextType>>>,
}

impl<ContextType> Pipeline<ContextType>
where
  ContextType: Clone + 'static,
{
  pub fn new(name: impl Into<String>, short_circuit_on_exception: bool) -> Self {
    let name = name.into();
    assert!(!name.trim().is_empty(), "Pipeline name must not be blank");
    Self {
      name,
      short_circuit_on_exception,
      on_error: Arc::new(default_on_error),
      observer: Arc::new(NoopObserver),
      pre_actions: Vec::new(),
      actions: Vec::new(),
      post_actions: Vec::new(),
      frozen_plan: OnceLock::new(),
    }
  }

  pub fn on_error_handler<ErrorHandler>(&mut self, handler: ErrorHandler) -> &mut Self
  where
    ErrorHandler: Fn(ContextType, PipelineError) -> ContextType + Send + Sync + 'static,
  {
    self.ensure_mutable();
    self.on_error = Arc::new(handler);
    self
  }

  pub fn observer<ObserverType>(&mut self, observer: ObserverType) -> &mut Self
  where
    ObserverType: PipelineObserver,
  {
    self.ensure_mutable();
    self.observer = Arc::new(observer);
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
    self.ensure_mutable();
    self.pre_actions.push(RegisteredAction {
      name: name.into(),
      kind: RegisteredActionKind::Action(Arc::new(action)),
    });
    self
  }

  #[allow(deprecated)]
  #[deprecated(note = "Use a one-argument Action and short_circuit().")]
  pub fn add_pre_action_control<ActionFn>(&mut self, action: ActionFn) -> &mut Self
  where
    ActionFn: Fn(ContextType, &mut ActionControl<ContextType>) -> ContextType + Send + Sync + 'static,
  {
    self.add_pre_action_control_named("", action)
  }

  #[deprecated(note = "Use a one-argument Action and short_circuit().")]
  pub fn add_pre_action_control_named<ActionFn>(
    &mut self,
    name: impl Into<String>,
    action: ActionFn,
  ) -> &mut Self
  where
    ActionFn: Fn(ContextType, &mut ActionControl<ContextType>) -> ContextType + Send + Sync + 'static,
  {
    self.ensure_mutable();
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
    self.ensure_mutable();
    self.actions.push(RegisteredAction {
      name: name.into(),
      kind: RegisteredActionKind::Action(Arc::new(action)),
    });
    self
  }

  #[allow(deprecated)]
  #[deprecated(note = "Use a one-argument Action and short_circuit().")]
  pub fn add_action_control<ActionFn>(&mut self, action: ActionFn) -> &mut Self
  where
    ActionFn: Fn(ContextType, &mut ActionControl<ContextType>) -> ContextType + Send + Sync + 'static,
  {
    self.add_action_control_named("", action)
  }

  #[deprecated(note = "Use a one-argument Action and short_circuit().")]
  pub fn add_action_control_named<ActionFn>(
    &mut self,
    name: impl Into<String>,
    action: ActionFn,
  ) -> &mut Self
  where
    ActionFn: Fn(ContextType, &mut ActionControl<ContextType>) -> ContextType + Send + Sync + 'static,
  {
    self.ensure_mutable();
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
    self.ensure_mutable();
    self.post_actions.push(RegisteredAction {
      name: name.into(),
      kind: RegisteredActionKind::Action(Arc::new(action)),
    });
    self
  }

  #[allow(deprecated)]
  #[deprecated(note = "Use a one-argument Action and short_circuit().")]
  pub fn add_post_action_control<ActionFn>(&mut self, action: ActionFn) -> &mut Self
  where
    ActionFn: Fn(ContextType, &mut ActionControl<ContextType>) -> ContextType + Send + Sync + 'static,
  {
    self.add_post_action_control_named("", action)
  }

  #[deprecated(note = "Use a one-argument Action and short_circuit().")]
  pub fn add_post_action_control_named<ActionFn>(
    &mut self,
    name: impl Into<String>,
    action: ActionFn,
  ) -> &mut Self
  where
    ActionFn: Fn(ContextType, &mut ActionControl<ContextType>) -> ContextType + Send + Sync + 'static,
  {
    self.ensure_mutable();
    self.post_actions.push(RegisteredAction {
      name: name.into(),
      kind: RegisteredActionKind::StepAction(Arc::new(action)),
    });
    self
  }

  pub(crate) fn add_registered_pre_action(&mut self, action: RegisteredAction<ContextType>) {
    self.ensure_mutable();
    self.pre_actions.push(action);
  }

  pub(crate) fn add_registered_action(&mut self, action: RegisteredAction<ContextType>) {
    self.ensure_mutable();
    self.actions.push(action);
  }

  pub(crate) fn add_registered_post_action(&mut self, action: RegisteredAction<ContextType>) {
    self.ensure_mutable();
    self.post_actions.push(action);
  }

  pub fn freeze(&self) -> &Self {
    self.plan();
    self
  }

  pub fn is_frozen(&self) -> bool {
    self.frozen_plan.get().is_some()
  }

  pub fn size(&self) -> usize {
    self
      .frozen_plan
      .get()
      .map(|plan| plan.actions.len())
      .unwrap_or(self.actions.len())
  }

  pub fn run(&self, input_value: ContextType) -> ContextType {
    PipelineRunner::execute(self.plan(), input_value, false).context
  }

  pub fn run_detailed(&self, input_value: ContextType) -> PipelineResult<ContextType> {
    let state = PipelineRunner::execute(self.plan(), input_value, true);
    let action_timings = state.control.timings.clone();
    PipelineResult {
      context: state.context,
      short_circuited: state.control.is_short_circuited(),
      errors: state.control.errors.clone(),
      action_timings: action_timings.clone(),
      timings: action_timings,
      total_nanos: state.control.run_elapsed_nanos(),
    }
  }

  #[deprecated(note = "Use run() or run_detailed().")]
  pub fn execute(&self, input_value: ContextType) -> PipelineResult<ContextType> {
    self.run_detailed(input_value)
  }

  fn plan(&self) -> Arc<PipelinePlan<ContextType>> {
    self
      .frozen_plan
      .get_or_init(|| {
        Arc::new(PipelinePlan {
          pipeline_name: self.name.clone(),
          short_circuit_on_exception: self.short_circuit_on_exception,
          on_error: self.on_error.clone(),
          observer: self.observer.clone(),
          pre_actions: self.pre_actions.clone(),
          actions: self.actions.clone(),
          post_actions: self.post_actions.clone(),
        })
      })
      .clone()
  }

  fn ensure_mutable(&self) {
    assert!(self.frozen_plan.get().is_none(), "Pipeline '{}' is frozen", self.name);
  }
}

struct ExecutionState<ContextType> {
  context: ContextType,
  control: ActionControl<ContextType>,
}

struct PipelineRunner;

impl PipelineRunner {
  fn execute<ContextType>(
    plan: Arc<PipelinePlan<ContextType>>,
    input_value: ContextType,
    collect_timings: bool,
  ) -> ExecutionState<ContextType>
  where
    ContextType: Clone + 'static,
  {
    let signal = Arc::new(ExecutionSignal::default());
    let mut control = ActionControl::with_signal(
      plan.pipeline_name.clone(),
      plan.on_error.clone(),
      signal.clone(),
      collect_timings,
    );
    control.begin_run();
    notify(|| plan.observer.on_pipeline_started(&plan.pipeline_name));
    let _scope = ExecutionScope::open(signal);

    let mut context = input_value;
    let mut pending_failure: Option<Box<dyn Any + Send>> = None;
    context = execute_actions(
      &plan,
      context,
      &plan.pre_actions,
      "preActions",
      false,
      &mut control,
      &mut pending_failure,
    );
    if pending_failure.is_none() && !control.is_short_circuited() {
      context = execute_actions(
        &plan,
        context,
        &plan.actions,
        "actions",
        true,
        &mut control,
        &mut pending_failure,
      );
    }
    context = execute_actions(
      &plan,
      context,
      &plan.post_actions,
      "postActions",
      false,
      &mut control,
      &mut pending_failure,
    );

    notify(|| {
      plan.observer.on_pipeline_completed(
        &plan.pipeline_name,
        control.is_short_circuited(),
        control.errors.len(),
        control.run_elapsed_nanos(),
      )
    });
    if let Some(payload) = pending_failure {
      resume_unwind(payload);
    }
    ExecutionState { context, control }
  }
}

fn execute_actions<ContextType>(
  plan: &PipelinePlan<ContextType>,
  start_context: ContextType,
  actions: &[RegisteredAction<ContextType>],
  phase: &str,
  stop_on_short_circuit: bool,
  control: &mut ActionControl<ContextType>,
  pending_failure: &mut Option<Box<dyn Any + Send>>,
) -> ContextType
where
  ContextType: Clone + 'static,
{
  if pending_failure.is_some() && phase != "postActions" {
    return start_context;
  }

  let mut context = start_context;
  for (action_index, registered_action) in actions.iter().enumerate() {
    let action_name = format_action_name(phase, action_index, &registered_action.name);
    control.begin_step(phase, action_index, action_name.clone());
    let was_short_circuited = control.is_short_circuited();
    notify(|| plan.observer.on_action_started(&plan.pipeline_name, phase, action_index, &action_name));

    let started = Instant::now();
    let context_before_action = context.clone();
    control.signal.action_executing.store(true, Ordering::Release);
    let action_result = catch_unwind(AssertUnwindSafe(|| match &registered_action.kind {
      RegisteredActionKind::Action(action) => action(context),
      RegisteredActionKind::StepAction(action) => action(context, control),
    }));
    control.signal.action_executing.store(false, Ordering::Release);

    let mut succeeded = true;
    let mut failure_message = String::new();
    match action_result {
      Ok(output_context) => context = output_context,
      Err(action_payload) => {
        succeeded = false;
        failure_message = safe_panic_to_string(&action_payload);
        let pipeline_error = make_pipeline_error(
          &plan.pipeline_name,
          phase,
          action_index,
          &action_name,
          failure_message.clone(),
        );
        control.errors.push(pipeline_error.clone());

        let handler_result = catch_unwind(AssertUnwindSafe(|| {
          (plan.on_error)(context_before_action.clone(), pipeline_error)
        }));
        match handler_result {
          Ok(updated_context) => context = updated_context,
          Err(handler_payload) => {
            context = context_before_action;
            control.short_circuit();
            if pending_failure.is_none() {
              *pending_failure = Some(handler_payload);
            }
          }
        }
        if plan.short_circuit_on_exception {
          control.short_circuit();
        }
      }
    }

    let elapsed_nanos = started.elapsed().as_nanos();
    control.record_timing(elapsed_nanos, succeeded);
    if succeeded {
      notify(|| {
        plan.observer.on_action_completed(
          &plan.pipeline_name,
          phase,
          action_index,
          &action_name,
          elapsed_nanos,
        )
      });
    } else {
      notify(|| {
        plan.observer.on_action_failed(
          &plan.pipeline_name,
          phase,
          action_index,
          &action_name,
          &failure_message,
          elapsed_nanos,
        )
      });
    }
    if !was_short_circuited && control.is_short_circuited() {
      notify(|| plan.observer.on_short_circuited(&plan.pipeline_name, phase, action_index, &action_name));
    }
    if pending_failure.is_some() && phase != "postActions" {
      break;
    }
    if stop_on_short_circuit && control.is_short_circuited() {
      break;
    }
  }
  context
}

fn notify<Callback>(callback: Callback)
where
  Callback: FnOnce(),
{
  let _ = catch_unwind(AssertUnwindSafe(callback));
}

fn make_pipeline_error(
  pipeline_name: &str,
  phase: &str,
  action_index: usize,
  action_name: &str,
  message: String,
) -> PipelineError {
  PipelineError {
    pipeline_name: pipeline_name.to_string(),
    pipeline: pipeline_name.to_string(),
    phase: phase.to_string(),
    action_index,
    index: action_index,
    action_name: action_name.to_string(),
    message,
  }
}

pub fn format_action_name(phase: &str, index: usize, name: &str) -> String {
  let prefix = match phase {
    "pre" | "preActions" => "pre",
    "post" | "postActions" => "post",
    _ => "s",
  };
  if name.is_empty() {
    format!("{prefix}{index}")
  } else {
    format!("{prefix}{index}:{name}")
  }
}

pub fn format_step_name(phase: &str, index: usize, name: &str) -> String {
  format_action_name(phase, index, name)
}

pub fn safe_panic_to_string(payload: &Box<dyn Any + Send>) -> String {
  if let Some(message) = payload.downcast_ref::<&str>() {
    return message.to_string();
  }
  if let Some(message) = payload.downcast_ref::<String>() {
    return message.clone();
  }
  "panic".to_string()
}
