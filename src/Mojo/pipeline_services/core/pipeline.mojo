from python import Python
from python import PythonObject
from collections.list import List

from ..remote.http_step import RemoteSpec, http_step

struct PipelineError(ImplicitlyCopyable):
    var pipeline: String
    var phase: String      # "pre" | "main" | "post"
    var index: Int
    var action_name: String
    var message: String

    fn __init__(out self,
                pipeline: String,
                phase: String,
                index: Int,
                action_name: String,
                message: String):
        self.pipeline = pipeline
        self.phase = phase
        self.index = index
        self.action_name = action_name
        self.message = message

struct ActionTiming(ImplicitlyCopyable):
    var phase: String
    var index: Int
    var action_name: String
    var elapsed_nanos: Int64
    var success: Bool

    fn __init__(out self,
                phase: String,
                index: Int,
                action_name: String,
                elapsed_nanos: Int64,
                success: Bool):
        self.phase = phase
        self.index = index
        self.action_name = action_name
        self.elapsed_nanos = elapsed_nanos
        self.success = success

comptime OnErrorFn = fn(ctx: PythonObject, err: PipelineError) -> PythonObject

fn default_on_error(ctx: PythonObject, err: PipelineError) -> PythonObject:
    return ctx

struct StepControl:
    var pipeline_name: String
    var on_error: OnErrorFn
    var errors: List[PipelineError]
    var timings: List[ActionTiming]
    var short_circuited: Bool

    var phase: String
    var index: Int
    var action_name: String

    var run_start_ns: Int64

    fn __init__(out self, pipeline_name: String, on_error: OnErrorFn = default_on_error):
        self.pipeline_name = pipeline_name
        self.on_error = on_error
        self.errors = List[PipelineError]()
        self.timings = List[ActionTiming]()
        self.short_circuited = False
        self.phase = "main"
        self.index = 0
        self.action_name = "?"
        self.run_start_ns = 0

    fn begin_step(mut self, phase: String, index: Int, action_name: String) -> None:
        self.phase = phase
        self.index = index
        self.action_name = action_name

    fn begin_run(mut self, run_start_ns: Int64) -> None:
        self.run_start_ns = run_start_ns

    fn reset(mut self) -> None:
        self.short_circuited = False
        self.errors = List[PipelineError]()
        self.timings = List[ActionTiming]()
        self.phase = "main"
        self.index = 0
        self.action_name = "?"
        self.run_start_ns = 0

    fn short_circuit(mut self) -> None:
        self.short_circuited = True

    fn is_short_circuited(self) -> Bool:
        return self.short_circuited

    fn record_error(mut self, ctx: PythonObject, message: String) -> PythonObject:
        var pipeline_error = PipelineError(self.pipeline_name, self.phase, self.index, self.action_name, message)
        self.errors.append(pipeline_error)
        return self.on_error(ctx, pipeline_error)

    fn record_timing(mut self, elapsed_nanos: Int64, success: Bool) -> None:
        var timing = ActionTiming(self.phase, self.index, self.action_name, elapsed_nanos, success)
        self.timings.append(timing)

comptime UnaryOperator = fn(ctx: PythonObject) raises -> PythonObject
comptime StepAction = fn(ctx: PythonObject, mut control: StepControl) raises -> PythonObject

struct PipelineResult:
    var context: PythonObject
    var short_circuited: Bool
    var errors: List[PipelineError]
    var timings: List[ActionTiming]
    var total_nanos: Int64

    fn __init__(out self,
                context: PythonObject,
                short_circuited: Bool,
                errors: List[PipelineError],
                timings: List[ActionTiming],
                total_nanos: Int64):
        self.context = context
        self.short_circuited = short_circuited
        self.errors = errors.copy()
        self.timings = timings.copy()
        self.total_nanos = total_nanos

    fn has_errors(self) -> Bool:
        return len(self.errors) > 0

fn noop_unary(ctx: PythonObject) -> PythonObject:
    return ctx

fn noop_action(ctx: PythonObject, mut control: StepControl) -> PythonObject:
    return ctx

fn noop_remote_spec() -> RemoteSpec:
    return RemoteSpec("")

struct RegisteredAction(ImplicitlyCopyable):
    var name: String
    var kind: Int  # 0 = unary, 1 = step_action, 2 = remote_http
    var unary: UnaryOperator
    var step_action: StepAction
    var remote_spec: RemoteSpec

    fn __init__(out self, name: String, action: UnaryOperator):
        self.name = name
        self.kind = 0
        self.unary = action
        self.step_action = noop_action
        self.remote_spec = noop_remote_spec()

    fn __init__(out self, name: String, action: StepAction):
        self.name = name
        self.kind = 1
        self.unary = noop_unary
        self.step_action = action
        self.remote_spec = noop_remote_spec()

    fn __init__(out self, name: String, spec: RemoteSpec):
        self.name = name
        self.kind = 2
        self.unary = noop_unary
        self.step_action = noop_action
        self.remote_spec = spec

struct Pipeline(Movable):
    var name: String
    var short_circuit_on_exception: Bool
    var on_error: OnErrorFn

    var pre_actions: List[RegisteredAction]
    var actions: List[RegisteredAction]
    var post_actions: List[RegisteredAction]

    fn __init__(out self, name: String, short_circuit_on_exception: Bool = True):
        self.name = name
        self.short_circuit_on_exception = short_circuit_on_exception
        self.on_error = default_on_error
        self.pre_actions = List[RegisteredAction]()
        self.actions = List[RegisteredAction]()
        self.post_actions = List[RegisteredAction]()

    fn on_error_handler(mut self, handler: OnErrorFn) -> None:
        self.on_error = handler

    # --- add pre ---
    fn add_pre_action(mut self, action: StepAction) -> None:
        self.add_pre_action_named("", action)

    fn add_pre_action(mut self, action: UnaryOperator) -> None:
        self.add_pre_action_named("", action)

    fn add_pre_action(mut self, spec: RemoteSpec) -> None:
        self.add_pre_action_named("", spec)

    fn add_pre_action_named(mut self, name: String, action: StepAction) -> None:
        self.pre_actions.append(RegisteredAction(name, action))

    fn add_pre_action_named(mut self, name: String, action: UnaryOperator) -> None:
        self.pre_actions.append(RegisteredAction(name, action))

    fn add_pre_action_named(mut self, name: String, spec: RemoteSpec) -> None:
        self.pre_actions.append(RegisteredAction(name, spec))

    # --- add main ---
    fn add_action(mut self, action: StepAction) -> None:
        self.add_action_named("", action)

    fn add_action(mut self, action: UnaryOperator) -> None:
        self.add_action_named("", action)

    fn add_action(mut self, spec: RemoteSpec) -> None:
        self.add_action_named("", spec)

    fn add_action_named(mut self, name: String, action: StepAction) -> None:
        self.actions.append(RegisteredAction(name, action))

    fn add_action_named(mut self, name: String, action: UnaryOperator) -> None:
        self.actions.append(RegisteredAction(name, action))

    fn add_action_named(mut self, name: String, spec: RemoteSpec) -> None:
        self.actions.append(RegisteredAction(name, spec))

    # --- add post ---
    fn add_post_action(mut self, action: StepAction) -> None:
        self.add_post_action_named("", action)

    fn add_post_action(mut self, action: UnaryOperator) -> None:
        self.add_post_action_named("", action)

    fn add_post_action(mut self, spec: RemoteSpec) -> None:
        self.add_post_action_named("", spec)

    fn add_post_action_named(mut self, name: String, action: StepAction) -> None:
        self.post_actions.append(RegisteredAction(name, action))

    fn add_post_action_named(mut self, name: String, action: UnaryOperator) -> None:
        self.post_actions.append(RegisteredAction(name, action))

    fn add_post_action_named(mut self, name: String, spec: RemoteSpec) -> None:
        self.post_actions.append(RegisteredAction(name, spec))

    fn run(self, input_value: PythonObject) -> PythonObject:
        return self.execute(input_value).context

    fn execute(self, input_value: PythonObject) -> PipelineResult:
        var perf_counter_ns_fn: PythonObject
        try:
            var time_module = Python.import_module("time")
            perf_counter_ns_fn = time_module.perf_counter_ns
        except:
            perf_counter_ns_fn = PythonObject(None)

        var ctx: PythonObject = input_value
        var control = StepControl(self.name, self.on_error)
        control.begin_run(now_ns(perf_counter_ns_fn))

        ctx = self.run_phase("pre", ctx, self.pre_actions, perf_counter_ns_fn, control, stop_on_short_circuit = False)
        if not control.is_short_circuited():
            ctx = self.run_phase("main", ctx, self.actions, perf_counter_ns_fn, control, stop_on_short_circuit = True)
        ctx = self.run_phase("post", ctx, self.post_actions, perf_counter_ns_fn, control, stop_on_short_circuit = False)

        var total_nanos = now_ns(perf_counter_ns_fn) - control.run_start_ns
        return PipelineResult(ctx, control.is_short_circuited(), control.errors, control.timings, total_nanos)

    fn run_phase(self,
                 phase: String,
                 start_ctx: PythonObject,
                 actions: List[RegisteredAction],
                 perf_counter_ns_fn: PythonObject,
                 mut control: StepControl,
                 stop_on_short_circuit: Bool) -> PythonObject:
        var ctx = start_ctx
        var step_index: Int = 0
        while step_index < Int(len(actions)):
            var registered_action = actions[step_index]
            var action_name = format_action_name(phase, step_index, registered_action.name)
            control.begin_step(phase, step_index, action_name)
            var step_start_ns = now_ns(perf_counter_ns_fn)
            var step_succeeded = True
            try:
                if registered_action.kind == 0:
                    ctx = registered_action.unary(ctx)
                elif registered_action.kind == 1:
                    ctx = registered_action.step_action(ctx, control)
                else:
                    ctx = http_step(registered_action.remote_spec, ctx)
            except caught_error:
                step_succeeded = False
                ctx = control.record_error(ctx, safe_error_to_string(caught_error))
                if self.short_circuit_on_exception:
                    control.short_circuit()
            var step_elapsed_nanos = now_ns(perf_counter_ns_fn) - step_start_ns
            control.record_timing(step_elapsed_nanos, step_succeeded)
            if stop_on_short_circuit and control.is_short_circuited():
                break
            step_index = step_index + 1
        return ctx

fn format_action_name(phase: String, index: Int, name: String) -> String:
    var prefix = "s"
    if phase == "pre":
        prefix = "pre"
    elif phase == "post":
        prefix = "post"

    if name == "":
        return prefix + String(index)
    return prefix + String(index) + ":" + name

fn format_step_name(phase: String, index: Int, name: String) -> String:
    return format_action_name(phase, index, name)

fn safe_error_to_string(value: Error) -> String:
    return String(value)

fn now_ns(perf_counter_ns_fn: PythonObject) -> Int64:
    if perf_counter_ns_fn is None:
        return 0
    try:
        var value = perf_counter_ns_fn()
        return Int64(value)
    except:
        return 0
