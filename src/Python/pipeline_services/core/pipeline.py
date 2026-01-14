from __future__ import annotations

import inspect
import time
from dataclasses import dataclass
from typing import Any, Callable, List, Optional, Union

from ..remote.http_step import RemoteSpec, http_step

UnaryOperator = Callable[[Any], Any]
StepAction = Callable[[Any, "StepControl"], Any]
OnErrorFn = Callable[[Any, "PipelineError"], Any]


@dataclass(frozen=True)
class PipelineError:
    pipeline: str
    phase: str
    index: int
    action_name: str
    message: str


@dataclass(frozen=True)
class ActionTiming:
    phase: str
    index: int
    action_name: str
    elapsed_nanos: int
    success: bool


def default_on_error(ctx: Any, err: PipelineError) -> Any:
    return ctx


class StepControl:
    def __init__(self, pipeline_name: str, on_error: OnErrorFn = default_on_error):
        self.pipeline_name = pipeline_name
        self.on_error = on_error
        self.errors: List[PipelineError] = []
        self.timings: List[ActionTiming] = []
        self.short_circuited = False

        self.phase = "main"
        self.index = 0
        self.action_name = "?"

        self.run_start_ns = 0

    def begin_step(self, phase: str, index: int, action_name: str) -> None:
        self.phase = phase
        self.index = index
        self.action_name = action_name

    def begin_run(self, run_start_ns: int) -> None:
        self.run_start_ns = run_start_ns

    def reset(self) -> None:
        self.short_circuited = False
        self.errors = []
        self.timings = []
        self.phase = "main"
        self.index = 0
        self.action_name = "?"
        self.run_start_ns = 0

    def short_circuit(self) -> None:
        self.short_circuited = True

    def is_short_circuited(self) -> bool:
        return self.short_circuited

    def record_error(self, ctx: Any, message: str) -> Any:
        pipeline_error = PipelineError(
            pipeline=self.pipeline_name,
            phase=self.phase,
            index=self.index,
            action_name=self.action_name,
            message=message,
        )
        self.errors.append(pipeline_error)
        return self.on_error(ctx, pipeline_error)

    def record_timing(self, elapsed_nanos: int, success: bool) -> None:
        timing = ActionTiming(
            phase=self.phase,
            index=self.index,
            action_name=self.action_name,
            elapsed_nanos=elapsed_nanos,
            success=success,
        )
        self.timings.append(timing)


@dataclass(frozen=True)
class PipelineResult:
    context: Any
    short_circuited: bool
    errors: List[PipelineError]
    timings: List[ActionTiming]
    total_nanos: int

    def has_errors(self) -> bool:
        return len(self.errors) > 0


@dataclass(frozen=True)
class RegisteredAction:
    name: str
    kind: int  # 0 = unary, 1 = step_action, 2 = remote_http
    unary: Optional[UnaryOperator] = None
    step_action: Optional[StepAction] = None
    remote_spec: Optional[RemoteSpec] = None


def format_action_name(phase: str, index: int, name: str) -> str:
    prefix = "s"
    if phase == "pre":
        prefix = "pre"
    elif phase == "post":
        prefix = "post"

    if name == "":
        return f"{prefix}{index}"
    return f"{prefix}{index}:{name}"


def format_step_name(phase: str, index: int, name: str) -> str:
    return format_action_name(phase, index, name)


def safe_error_to_string(value: BaseException) -> str:
    return str(value)


def callable_accepts_two_positional_args(callable_value: Callable[..., Any]) -> bool:
    try:
        signature = inspect.signature(callable_value)
    except (TypeError, ValueError):
        return False

    positional_count = 0
    for parameter in signature.parameters.values():
        if parameter.kind in (
            inspect.Parameter.POSITIONAL_ONLY,
            inspect.Parameter.POSITIONAL_OR_KEYWORD,
        ):
            positional_count += 1
        elif parameter.kind == inspect.Parameter.VAR_POSITIONAL:
            return True
    return positional_count >= 2


def to_registered_action(name: str, action: Union[UnaryOperator, StepAction, RemoteSpec]) -> RegisteredAction:
    if isinstance(action, RemoteSpec):
        return RegisteredAction(name=name, kind=2, remote_spec=action)

    if not callable(action):
        raise TypeError("Action must be callable or a RemoteSpec")

    if callable_accepts_two_positional_args(action):
        return RegisteredAction(name=name, kind=1, step_action=action)

    return RegisteredAction(name=name, kind=0, unary=action)


class Pipeline:
    def __init__(self, name: str, short_circuit_on_exception: bool = True):
        self.name = name
        self.short_circuit_on_exception = short_circuit_on_exception
        self.on_error: OnErrorFn = default_on_error

        self.pre_actions: List[RegisteredAction] = []
        self.actions: List[RegisteredAction] = []
        self.post_actions: List[RegisteredAction] = []

    def on_error_handler(self, handler: OnErrorFn) -> None:
        self.on_error = handler

    def add_pre_action(self, action: Union[UnaryOperator, StepAction, RemoteSpec]) -> None:
        self.add_pre_action_named("", action)

    def add_pre_action_named(self, name: str, action: Union[UnaryOperator, StepAction, RemoteSpec]) -> None:
        self.pre_actions.append(to_registered_action(name, action))

    def add_action(self, action: Union[UnaryOperator, StepAction, RemoteSpec]) -> None:
        self.add_action_named("", action)

    def add_action_named(self, name: str, action: Union[UnaryOperator, StepAction, RemoteSpec]) -> None:
        self.actions.append(to_registered_action(name, action))

    def add_post_action(self, action: Union[UnaryOperator, StepAction, RemoteSpec]) -> None:
        self.add_post_action_named("", action)

    def add_post_action_named(self, name: str, action: Union[UnaryOperator, StepAction, RemoteSpec]) -> None:
        self.post_actions.append(to_registered_action(name, action))

    def run(self, input_value: Any) -> Any:
        return self.execute(input_value).context

    def execute(self, input_value: Any) -> PipelineResult:
        ctx = input_value
        control = StepControl(self.name, self.on_error)
        control.begin_run(time.perf_counter_ns())

        ctx = self.run_phase(
            phase="pre",
            start_ctx=ctx,
            actions=self.pre_actions,
            control=control,
            stop_on_short_circuit=False,
        )
        if not control.is_short_circuited():
            ctx = self.run_phase(
                phase="main",
                start_ctx=ctx,
                actions=self.actions,
                control=control,
                stop_on_short_circuit=True,
            )
        ctx = self.run_phase(
            phase="post",
            start_ctx=ctx,
            actions=self.post_actions,
            control=control,
            stop_on_short_circuit=False,
        )

        total_nanos = time.perf_counter_ns() - control.run_start_ns
        return PipelineResult(
            context=ctx,
            short_circuited=control.is_short_circuited(),
            errors=list(control.errors),
            timings=list(control.timings),
            total_nanos=total_nanos,
        )

    def run_phase(
        self,
        phase: str,
        start_ctx: Any,
        actions: List[RegisteredAction],
        control: StepControl,
        stop_on_short_circuit: bool,
    ) -> Any:
        ctx = start_ctx
        step_index = 0
        while step_index < len(actions):
            registered_action = actions[step_index]
            action_name = format_action_name(phase, step_index, registered_action.name)
            control.begin_step(phase, step_index, action_name)

            step_start_ns = time.perf_counter_ns()
            step_succeeded = True
            try:
                if registered_action.kind == 0:
                    if registered_action.unary is None:
                        raise RuntimeError("Registered unary action is missing")
                    ctx = registered_action.unary(ctx)
                elif registered_action.kind == 1:
                    if registered_action.step_action is None:
                        raise RuntimeError("Registered step action is missing")
                    ctx = registered_action.step_action(ctx, control)
                else:
                    if registered_action.remote_spec is None:
                        raise RuntimeError("Registered remote spec is missing")
                    ctx = http_step(registered_action.remote_spec, ctx)
            except Exception as caught_error:
                step_succeeded = False
                ctx = control.record_error(ctx, safe_error_to_string(caught_error))
                if self.short_circuit_on_exception:
                    control.short_circuit()

            step_elapsed_nanos = time.perf_counter_ns() - step_start_ns
            control.record_timing(step_elapsed_nanos, step_succeeded)

            if stop_on_short_circuit and control.is_short_circuited():
                break
            step_index += 1
        return ctx

