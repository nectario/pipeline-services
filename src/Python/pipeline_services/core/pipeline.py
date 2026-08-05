from __future__ import annotations

import inspect
import time
import warnings
from contextvars import ContextVar
from dataclasses import dataclass, field
from typing import Any, Callable, Generic, Optional, Protocol, Sequence, TypeVar, cast

ContextType = TypeVar("ContextType")
Action = Callable[[ContextType], ContextType]
UnaryOperator = Action
StepAction = Callable[[ContextType, "ActionControl[ContextType]"], ContextType]
OnErrorFn = Callable[[ContextType, "PipelineError"], ContextType]


@dataclass(frozen=True)
class PipelineError:
    pipeline_name: str
    phase: str
    action_index: int
    action_name: str
    exception: Exception

    @property
    def pipeline(self) -> str:
        return self.pipeline_name

    @property
    def index(self) -> int:
        return self.action_index

    @property
    def message(self) -> str:
        return str(self.exception)


@dataclass(frozen=True)
class ActionTiming:
    phase: str
    action_index: int
    action_name: str
    elapsed_nanos: int
    success: bool

    @property
    def index(self) -> int:
        return self.action_index


@dataclass(frozen=True)
class PipelineResult(Generic[ContextType]):
    context: ContextType
    short_circuited: bool
    errors: tuple[PipelineError, ...]
    action_timings: tuple[ActionTiming, ...]
    total_nanos: int

    @property
    def timings(self) -> tuple[ActionTiming, ...]:
        return self.action_timings

    def has_errors(self) -> bool:
        return bool(self.errors)


class PipelineObserver:
    def on_pipeline_started(self, pipeline_name: str) -> None:
        pass

    def on_action_started(
        self,
        pipeline_name: str,
        phase: str,
        action_index: int,
        action_name: str,
    ) -> None:
        pass

    def on_action_completed(
        self,
        pipeline_name: str,
        phase: str,
        action_index: int,
        action_name: str,
        elapsed_nanos: int,
    ) -> None:
        pass

    def on_action_failed(
        self,
        pipeline_name: str,
        phase: str,
        action_index: int,
        action_name: str,
        exception: Exception,
        elapsed_nanos: int,
    ) -> None:
        pass

    def on_short_circuited(
        self,
        pipeline_name: str,
        phase: str,
        action_index: int,
        action_name: str,
    ) -> None:
        pass

    def on_pipeline_completed(
        self,
        pipeline_name: str,
        short_circuited: bool,
        error_count: int,
        elapsed_nanos: int,
    ) -> None:
        pass


_NOOP_OBSERVER = PipelineObserver()


@dataclass(frozen=True)
class _RegisteredAction(Generic[ContextType]):
    name: Optional[str]
    action: Action[ContextType]


@dataclass(frozen=True)
class _PipelinePlan(Generic[ContextType]):
    pipeline_name: str
    short_circuit_on_exception: bool
    error_handler: OnErrorFn[ContextType]
    observer: PipelineObserver
    pre_actions: tuple[_RegisteredAction[ContextType], ...]
    actions: tuple[_RegisteredAction[ContextType], ...]
    post_actions: tuple[_RegisteredAction[ContextType], ...]


@dataclass
class _ExecutionState(Generic[ContextType]):
    plan: _PipelinePlan[ContextType]
    context: ContextType
    collect_timings: bool
    run_start_nanos: int
    errors: list[PipelineError] = field(default_factory=list)
    action_timings: list[ActionTiming] = field(default_factory=list)
    short_circuited: bool = False
    action_executing: bool = False
    phase: str = "actions"
    action_index: int = 0
    action_name: str = "?"


_EXECUTION_STACK: ContextVar[tuple[_ExecutionState[Any], ...]] = ContextVar(
    "pipeline_services_execution_stack",
    default=(),
)


class InvalidErrorHandlerError(RuntimeError):
    def __init__(
        self,
        message: str,
        action_exception: Exception,
        handler_exception: Optional[BaseException] = None,
    ) -> None:
        super().__init__(message)
        self.action_exception = action_exception
        self.handler_exception = handler_exception
        self.__cause__ = handler_exception or action_exception


def default_on_error(context: ContextType, error: PipelineError) -> ContextType:
    del error
    return context


def short_circuit() -> None:
    stack = _EXECUTION_STACK.get()
    if not stack:
        raise RuntimeError("short_circuit() can only be called during an active pipeline run")

    state = stack[-1]
    if not state.action_executing:
        raise RuntimeError("short_circuit() can only be called while a Pipeline Action is executing")
    state.short_circuited = True


def _current_state() -> _ExecutionState[Any]:
    stack = _EXECUTION_STACK.get()
    if not stack:
        raise RuntimeError("No active Pipeline execution")
    return stack[-1]


class ActionControl(Generic[ContextType]):
    """Deprecated compatibility view over the current Action execution."""

    def short_circuit(self) -> None:
        short_circuit()

    def is_short_circuited(self) -> bool:
        return _current_state().short_circuited

    @property
    def short_circuited(self) -> bool:
        return self.is_short_circuited()

    @property
    def pipeline_name(self) -> str:
        return _current_state().plan.pipeline_name

    @property
    def errors(self) -> list[PipelineError]:
        return list(_current_state().errors)

    @property
    def timings(self) -> list[ActionTiming]:
        return list(_current_state().action_timings)

    @property
    def phase(self) -> str:
        return _current_state().phase

    @property
    def index(self) -> int:
        return _current_state().action_index

    @property
    def action_name(self) -> str:
        return _current_state().action_name

    @property
    def run_start_ns(self) -> int:
        return _current_state().run_start_nanos

    def record_error(self, context: ContextType, error: Exception | str) -> ContextType:
        state = cast(_ExecutionState[ContextType], _current_state())
        exception = error if isinstance(error, Exception) else RuntimeError(str(error))
        return _record_error(state, context, exception)


StepControl = ActionControl


def _callable_accepts_two_positional_args(callable_value: Callable[..., Any]) -> bool:
    try:
        signature = inspect.signature(callable_value)
    except (TypeError, ValueError):
        return False

    required_positional_count = 0
    for parameter in signature.parameters.values():
        if parameter.kind in (
            inspect.Parameter.POSITIONAL_ONLY,
            inspect.Parameter.POSITIONAL_OR_KEYWORD,
        ) and parameter.default is inspect.Parameter.empty:
            required_positional_count += 1
    return required_positional_count >= 2


def _normalize_action(
    action: Action[ContextType] | StepAction[ContextType],
) -> Action[ContextType]:
    if not callable(action):
        raise TypeError("Action must be callable")

    if _callable_accepts_two_positional_args(action):
        warnings.warn(
            "Control-aware Actions are deprecated; call short_circuit() from a one-argument Action.",
            DeprecationWarning,
            stacklevel=3,
        )

        def compatibility_action(context: ContextType) -> ContextType:
            return cast(StepAction[ContextType], action)(context, ActionControl())

        return compatibility_action

    return cast(Action[ContextType], action)


def _format_action_name(
    phase: str,
    action_index: int,
    registered_name: Optional[str],
) -> str:
    prefix = {
        "preActions": "pre",
        "actions": "s",
        "postActions": "post",
    }[phase]
    if not registered_name:
        return f"{prefix}{action_index}"
    return f"{prefix}{action_index}:{registered_name}"


def _notify(callback: Callable[[], None]) -> None:
    try:
        callback()
    except Exception:
        pass


def _record_error(
    state: _ExecutionState[ContextType],
    context: ContextType,
    exception: Exception,
) -> ContextType:
    pipeline_error = PipelineError(
        pipeline_name=state.plan.pipeline_name,
        phase=state.phase,
        action_index=state.action_index,
        action_name=state.action_name,
        exception=exception,
    )
    state.errors.append(pipeline_error)

    was_action_executing = state.action_executing
    state.action_executing = False
    try:
        updated_context = state.plan.error_handler(context, pipeline_error)
    except BaseException as handler_exception:
        raise InvalidErrorHandlerError(
            "on_error handler raised while recovering from an Action failure",
            exception,
            handler_exception,
        ) from handler_exception
    finally:
        state.action_executing = was_action_executing

    if updated_context is None:
        raise InvalidErrorHandlerError(
            "on_error handler returned None",
            exception,
        )
    state.context = updated_context
    return updated_context


class _PipelineRunner:
    @staticmethod
    def run(plan: _PipelinePlan[ContextType], input_value: ContextType) -> ContextType:
        return _PipelineRunner._execute(plan, input_value, collect_timings=False).context

    @staticmethod
    def run_detailed(
        plan: _PipelinePlan[ContextType],
        input_value: ContextType,
    ) -> PipelineResult[ContextType]:
        state = _PipelineRunner._execute(plan, input_value, collect_timings=True)
        return PipelineResult(
            context=state.context,
            short_circuited=state.short_circuited,
            errors=tuple(state.errors),
            action_timings=tuple(state.action_timings),
            total_nanos=max(0, time.perf_counter_ns() - state.run_start_nanos),
        )

    @staticmethod
    def _execute(
        plan: _PipelinePlan[ContextType],
        input_value: ContextType,
        collect_timings: bool,
    ) -> _ExecutionState[ContextType]:
        if input_value is None:
            raise ValueError("input_value must not be None")

        run_start_nanos = time.perf_counter_ns()
        state = _ExecutionState(
            plan=plan,
            context=input_value,
            collect_timings=collect_timings,
            run_start_nanos=run_start_nanos,
        )
        _notify(lambda: plan.observer.on_pipeline_started(plan.pipeline_name))

        stack = _EXECUTION_STACK.get()
        token = _EXECUTION_STACK.set(stack + (cast(_ExecutionState[Any], state),))
        pending_failure: Optional[BaseException] = None
        try:
            try:
                pending_failure = _PipelineRunner._execute_actions(
                    state,
                    "preActions",
                    plan.pre_actions,
                    stop_on_short_circuit=False,
                    pending_failure=pending_failure,
                )
                if pending_failure is None and not state.short_circuited:
                    pending_failure = _PipelineRunner._execute_actions(
                        state,
                        "actions",
                        plan.actions,
                        stop_on_short_circuit=True,
                        pending_failure=pending_failure,
                    )
            finally:
                pending_failure = _PipelineRunner._execute_actions(
                    state,
                    "postActions",
                    plan.post_actions,
                    stop_on_short_circuit=False,
                    pending_failure=pending_failure,
                )
        finally:
            _EXECUTION_STACK.reset(token)
            elapsed_nanos = max(0, time.perf_counter_ns() - run_start_nanos)
            _notify(
                lambda: plan.observer.on_pipeline_completed(
                    plan.pipeline_name,
                    state.short_circuited,
                    len(state.errors),
                    elapsed_nanos,
                )
            )

        if pending_failure is not None:
            raise pending_failure
        return state

    @staticmethod
    def _execute_actions(
        state: _ExecutionState[ContextType],
        phase: str,
        actions: Sequence[_RegisteredAction[ContextType]],
        stop_on_short_circuit: bool,
        pending_failure: Optional[BaseException],
    ) -> Optional[BaseException]:
        if pending_failure is not None and phase != "postActions":
            return pending_failure

        observer = state.plan.observer
        observer_enabled = observer is not _NOOP_OBSERVER

        for action_index, registered_action in enumerate(actions):
            state.phase = phase
            state.action_index = action_index
            state.action_name = _format_action_name(
                phase,
                action_index,
                registered_action.name,
            )
            was_short_circuited = state.short_circuited

            if observer_enabled:
                _notify(
                    lambda: observer.on_action_started(
                        state.plan.pipeline_name,
                        phase,
                        action_index,
                        state.action_name,
                    )
                )

            action_start_nanos = (
                time.perf_counter_ns()
                if state.collect_timings or observer_enabled
                else 0
            )
            action_succeeded = True
            action_failure: Optional[Exception] = None

            try:
                state.action_executing = True
                try:
                    next_context = registered_action.action(state.context)
                finally:
                    state.action_executing = False

                if next_context is None:
                    raise ValueError(f"Action returned None: {state.action_name}")
                state.context = next_context
            except Exception as exception:
                action_succeeded = False
                action_failure = exception
                try:
                    _record_error(state, state.context, exception)
                except InvalidErrorHandlerError as handler_failure:
                    if pending_failure is None:
                        pending_failure = handler_failure
                    state.short_circuited = True
                if state.plan.short_circuit_on_exception:
                    state.short_circuited = True

            elapsed_nanos = (
                max(0, time.perf_counter_ns() - action_start_nanos)
                if state.collect_timings or observer_enabled
                else 0
            )
            if state.collect_timings:
                state.action_timings.append(
                    ActionTiming(
                        phase=phase,
                        action_index=action_index,
                        action_name=state.action_name,
                        elapsed_nanos=elapsed_nanos,
                        success=action_succeeded,
                    )
                )

            if observer_enabled:
                if action_succeeded:
                    _notify(
                        lambda: observer.on_action_completed(
                            state.plan.pipeline_name,
                            phase,
                            action_index,
                            state.action_name,
                            elapsed_nanos,
                        )
                    )
                else:
                    assert action_failure is not None
                    _notify(
                        lambda: observer.on_action_failed(
                            state.plan.pipeline_name,
                            phase,
                            action_index,
                            state.action_name,
                            action_failure,
                            elapsed_nanos,
                        )
                    )

            if not was_short_circuited and state.short_circuited:
                _notify(
                    lambda: observer.on_short_circuited(
                        state.plan.pipeline_name,
                        phase,
                        action_index,
                        state.action_name,
                    )
                )

            if pending_failure is not None and phase != "postActions":
                break
            if stop_on_short_circuit and state.short_circuited:
                break

        return pending_failure


class Pipeline(Generic[ContextType]):
    def __init__(
        self,
        pipeline_name: str,
        short_circuit_on_exception: bool = True,
    ) -> None:
        normalized_name = pipeline_name.strip()
        if not normalized_name:
            raise ValueError("pipeline_name must not be blank")

        self.pipeline_name = normalized_name
        self.name = normalized_name
        self.short_circuit_on_exception = short_circuit_on_exception
        self._error_handler: OnErrorFn[ContextType] = default_on_error
        self._observer: PipelineObserver = _NOOP_OBSERVER
        self._pre_actions: list[_RegisteredAction[ContextType]] = []
        self._actions: list[_RegisteredAction[ContextType]] = []
        self._post_actions: list[_RegisteredAction[ContextType]] = []
        self._frozen_plan: Optional[_PipelinePlan[ContextType]] = None

    def on_error(
        self,
        handler: Optional[OnErrorFn[ContextType]],
    ) -> "Pipeline[ContextType]":
        self._ensure_mutable()
        self._error_handler = handler or default_on_error
        return self

    def on_error_handler(
        self,
        handler: Optional[OnErrorFn[ContextType]],
    ) -> "Pipeline[ContextType]":
        return self.on_error(handler)

    def observer(
        self,
        observer: Optional[PipelineObserver],
    ) -> "Pipeline[ContextType]":
        self._ensure_mutable()
        self._observer = observer or _NOOP_OBSERVER
        return self

    def add_pre_action(
        self,
        action: Action[ContextType] | StepAction[ContextType],
        *,
        name: Optional[str] = None,
    ) -> "Pipeline[ContextType]":
        return self._register(self._pre_actions, name, action)

    def add_action(
        self,
        action: Action[ContextType] | StepAction[ContextType],
        *,
        name: Optional[str] = None,
    ) -> "Pipeline[ContextType]":
        return self._register(self._actions, name, action)

    def add_post_action(
        self,
        action: Action[ContextType] | StepAction[ContextType],
        *,
        name: Optional[str] = None,
    ) -> "Pipeline[ContextType]":
        return self._register(self._post_actions, name, action)

    def add_pre_action_named(
        self,
        name: str,
        action: Action[ContextType] | StepAction[ContextType],
    ) -> "Pipeline[ContextType]":
        return self.add_pre_action(action, name=name)

    def add_action_named(
        self,
        name: str,
        action: Action[ContextType] | StepAction[ContextType],
    ) -> "Pipeline[ContextType]":
        return self.add_action(action, name=name)

    def add_post_action_named(
        self,
        name: str,
        action: Action[ContextType] | StepAction[ContextType],
    ) -> "Pipeline[ContextType]":
        return self.add_post_action(action, name=name)

    def short_circuit(self) -> None:
        short_circuit()

    def run(self, input_value: ContextType) -> ContextType:
        return _PipelineRunner.run(self._plan(), input_value)

    def run_detailed(self, input_value: ContextType) -> PipelineResult[ContextType]:
        return _PipelineRunner.run_detailed(self._plan(), input_value)

    def execute(self, input_value: ContextType) -> PipelineResult[ContextType]:
        warnings.warn(
            "execute() is deprecated; use run() or run_detailed().",
            DeprecationWarning,
            stacklevel=2,
        )
        return self.run_detailed(input_value)

    def freeze(self) -> "Pipeline[ContextType]":
        self._plan()
        return self

    def is_frozen(self) -> bool:
        return self._frozen_plan is not None

    def size(self) -> int:
        plan = self._frozen_plan
        return len(plan.actions if plan is not None else self._actions)

    @property
    def pre_actions(self) -> tuple[_RegisteredAction[ContextType], ...]:
        plan = self._frozen_plan
        return plan.pre_actions if plan is not None else tuple(self._pre_actions)

    @property
    def actions(self) -> tuple[_RegisteredAction[ContextType], ...]:
        plan = self._frozen_plan
        return plan.actions if plan is not None else tuple(self._actions)

    @property
    def post_actions(self) -> tuple[_RegisteredAction[ContextType], ...]:
        plan = self._frozen_plan
        return plan.post_actions if plan is not None else tuple(self._post_actions)

    def _register(
        self,
        destination: list[_RegisteredAction[ContextType]],
        name: Optional[str],
        action: Action[ContextType] | StepAction[ContextType],
    ) -> "Pipeline[ContextType]":
        self._ensure_mutable()
        destination.append(
            _RegisteredAction(
                name=name.strip() if name and name.strip() else None,
                action=_normalize_action(action),
            )
        )
        return self

    def _plan(self) -> _PipelinePlan[ContextType]:
        if self._frozen_plan is None:
            self._frozen_plan = _PipelinePlan(
                pipeline_name=self.pipeline_name,
                short_circuit_on_exception=self.short_circuit_on_exception,
                error_handler=self._error_handler,
                observer=self._observer,
                pre_actions=tuple(self._pre_actions),
                actions=tuple(self._actions),
                post_actions=tuple(self._post_actions),
            )
        return self._frozen_plan

    def _ensure_mutable(self) -> None:
        if self._frozen_plan is not None:
            raise RuntimeError(f"Pipeline '{self.pipeline_name}' is frozen")
