from std.python import Python, PythonObject
from std.collections.list import List


comptime Action = def(PythonObject) raises -> PythonObject
comptime OnErrorFn = def(PythonObject, PipelineError) raises -> PythonObject


def _execution_api() raises -> PythonObject:
    var module = Python.evaluate(
        """
import builtins
import contextvars
import types

if not hasattr(builtins, "_pipeline_services_execution_api"):
    current = contextvars.ContextVar("pipeline_services_execution", default=())

    def open_scope():
        state = {"short_circuited": False, "action_executing": False}
        token = current.set(current.get() + (state,))
        return state, token

    def close_scope(token):
        current.reset(token)

    def begin_action(state):
        if state["action_executing"]:
            raise RuntimeError("A Pipeline Action is already executing")
        state["action_executing"] = True

    def end_action(state):
        if not state["action_executing"]:
            raise RuntimeError("No Pipeline Action is executing")
        state["action_executing"] = False

    def short_circuit():
        stack = current.get()
        if not stack:
            raise RuntimeError("short_circuit() can only be called during an active Pipeline run")
        state = stack[-1]
        if not state["action_executing"]:
            raise RuntimeError("short_circuit() can only be called while a Pipeline Action is executing")
        state["short_circuited"] = True

    builtins._pipeline_services_execution_api = types.SimpleNamespace(
        open_scope=open_scope,
        close_scope=close_scope,
        begin_action=begin_action,
        end_action=end_action,
        short_circuit=short_circuit,
    )

api = builtins._pipeline_services_execution_api
""",
        file=True,
        name="_pipeline_services_execution_loader",
    )
    return module.api


def short_circuit() raises:
    _execution_api().short_circuit()


def default_on_error(context: PythonObject, error: PipelineError) -> PythonObject:
    _ = error
    return context


struct PipelineError(ImplicitlyCopyable):
    var pipeline_name: String
    var phase: String
    var action_index: Int
    var action_name: String
    var message: String

    def __init__(
        out self,
        pipeline_name: String,
        phase: String,
        action_index: Int,
        action_name: String,
        message: String,
    ):
        self.pipeline_name = pipeline_name
        self.phase = phase
        self.action_index = action_index
        self.action_name = action_name
        self.message = message


struct ActionTiming(ImplicitlyCopyable):
    var phase: String
    var action_index: Int
    var action_name: String
    var elapsed_nanos: Int64
    var success: Bool

    def __init__(
        out self,
        phase: String,
        action_index: Int,
        action_name: String,
        elapsed_nanos: Int64,
        success: Bool,
    ):
        self.phase = phase
        self.action_index = action_index
        self.action_name = action_name
        self.elapsed_nanos = elapsed_nanos
        self.success = success


struct PipelineResult(Movable):
    var context: PythonObject
    var short_circuited: Bool
    var errors: List[PipelineError]
    var action_timings: List[ActionTiming]
    var total_nanos: Int64

    def __init__(
        out self,
        context: PythonObject,
        short_circuited: Bool,
        errors: List[PipelineError],
        action_timings: List[ActionTiming],
        total_nanos: Int64,
    ):
        self.context = context
        self.short_circuited = short_circuited
        self.errors = errors.copy()
        self.action_timings = action_timings.copy()
        self.total_nanos = total_nanos

    def has_errors(self) -> Bool:
        return len(self.errors) > 0


struct RegisteredAction(ImplicitlyCopyable):
    var name: String
    var action: Action

    def __init__(out self, name: String, action: Action):
        self.name = name
        self.action = action


struct ExecutionState(Movable):
    var context: PythonObject
    var scope_state: PythonObject
    var scope_token: PythonObject
    var errors: List[PipelineError]
    var action_timings: List[ActionTiming]
    var short_circuited: Bool
    var start_ns: Int64

    def __init__(
        out self,
        context: PythonObject,
        scope_state: PythonObject,
        scope_token: PythonObject,
        start_ns: Int64,
    ):
        self.context = context
        self.scope_state = scope_state
        self.scope_token = scope_token
        self.errors = List[PipelineError]()
        self.action_timings = List[ActionTiming]()
        self.short_circuited = False
        self.start_ns = start_ns


struct Pipeline(Movable):
    var pipeline_name: String
    var short_circuit_on_exception: Bool
    var error_handler: OnErrorFn
    var pre_actions: List[RegisteredAction]
    var actions: List[RegisteredAction]
    var post_actions: List[RegisteredAction]
    var frozen: Bool

    def __init__(
        out self,
        pipeline_name: String,
        short_circuit_on_exception: Bool = True,
    ):
        if pipeline_name == "":
            raise "pipeline_name must not be blank"
        self.pipeline_name = pipeline_name
        self.short_circuit_on_exception = short_circuit_on_exception
        self.error_handler = default_on_error
        self.pre_actions = List[RegisteredAction]()
        self.actions = List[RegisteredAction]()
        self.post_actions = List[RegisteredAction]()
        self.frozen = False

    def on_error(mut self, handler: OnErrorFn):
        self._ensure_mutable()
        self.error_handler = handler

    def add_pre_action(mut self, action: Action):
        self.add_pre_action_named("", action)

    def add_pre_action_named(mut self, name: String, action: Action):
        self._ensure_mutable()
        self.pre_actions.append(RegisteredAction(name, action))

    def add_action(mut self, action: Action):
        self.add_action_named("", action)

    def add_action_named(mut self, name: String, action: Action):
        self._ensure_mutable()
        self.actions.append(RegisteredAction(name, action))

    def add_post_action(mut self, action: Action):
        self.add_post_action_named("", action)

    def add_post_action_named(mut self, name: String, action: Action):
        self._ensure_mutable()
        self.post_actions.append(RegisteredAction(name, action))

    def freeze(mut self):
        self.frozen = True

    def is_frozen(self) -> Bool:
        return self.frozen

    def short_circuit(self) raises:
        short_circuit()

    def run(mut self, input_value: PythonObject) raises -> PythonObject:
        return self._execute(input_value, False).context

    def run_detailed(mut self, input_value: PythonObject) raises -> PipelineResult:
        return self._execute(input_value, True)

    def execute(mut self, input_value: PythonObject) raises -> PipelineResult:
        return self.run_detailed(input_value)

    def _execute(
        mut self,
        input_value: PythonObject,
        collect_timings: Bool,
    ) raises -> PipelineResult:
        self.frozen = True
        var execution_api = _execution_api()
        var scope = execution_api.open_scope()
        var state = ExecutionState(
            input_value,
            scope[0],
            scope[1],
            _now_ns(),
        )

        try:
            self._execute_actions(
                state,
                "preActions",
                self.pre_actions,
                False,
                collect_timings,
            )
            if not state.short_circuited:
                self._execute_actions(
                    state,
                    "actions",
                    self.actions,
                    True,
                    collect_timings,
                )
        finally:
            self._execute_actions(
                state,
                "postActions",
                self.post_actions,
                False,
                collect_timings,
            )
            execution_api.close_scope(state.scope_token)

        return PipelineResult(
            state.context,
            state.short_circuited,
            state.errors,
            state.action_timings,
            _now_ns() - state.start_ns,
        )

    def _execute_actions(
        self,
        mut state: ExecutionState,
        phase: String,
        registered_actions: List[RegisteredAction],
        stop_on_short_circuit: Bool,
        collect_timings: Bool,
    ) raises:
        var execution_api = _execution_api()
        var action_index = 0
        while action_index < Int(len(registered_actions)):
            var registered_action = registered_actions[action_index]
            var action_name = _format_action_name(
                phase,
                action_index,
                registered_action.name,
            )
            var action_start_ns = _now_ns()
            var succeeded = True

            execution_api.begin_action(state.scope_state)
            try:
                state.context = registered_action.action(state.context)
            except caught_error:
                succeeded = False
                var pipeline_error = PipelineError(
                    self.pipeline_name,
                    phase,
                    action_index,
                    action_name,
                    String(caught_error),
                )
                state.errors.append(pipeline_error)
                state.context = self.error_handler(state.context, pipeline_error)
                if self.short_circuit_on_exception:
                    execution_api.short_circuit()
            finally:
                execution_api.end_action(state.scope_state)

            state.short_circuited = Bool(py=state.scope_state["short_circuited"])
            if collect_timings:
                state.action_timings.append(
                    ActionTiming(
                        phase,
                        action_index,
                        action_name,
                        _now_ns() - action_start_ns,
                        succeeded,
                    )
                )

            if stop_on_short_circuit and state.short_circuited:
                break
            action_index += 1

    def _ensure_mutable(self):
        if self.frozen:
            raise "Pipeline '" + self.pipeline_name + "' is frozen"


def _format_action_name(phase: String, index: Int, name: String) -> String:
    var prefix = "s"
    if phase == "preActions":
        prefix = "pre"
    elif phase == "postActions":
        prefix = "post"
    if name == "":
        return prefix + String(index)
    return prefix + String(index) + ":" + name


def _now_ns() raises -> Int64:
    var time_module = Python.import_module("time")
    return Int64(py=time_module.perf_counter_ns())
