from __future__ import annotations
import time
from dataclasses import dataclass
from typing import Callable, List, Optional, Any, Generic, TypeVar, Dict

from .metrics import Metrics, NoopMetrics
from .jumps import JumpSignal
from .short_circuit import ShortCircuit

T = TypeVar('T')
I = TypeVar('I')
O = TypeVar('O')

@dataclass
class _LabeledStep(Generic[T]):
    label: Optional[str]
    name: str
    fn: Callable[[T], T]
    section: str = 'main'   # 'pre' | 'main' | 'post'

class Pipeline(Generic[T]):
    """Unary pipeline (T -> T) with labeled steps, sections, short-circuit, and jumps."""
    def __init__(self, name: str, short_circuit: bool = True, metrics: Optional[Metrics] = None):
        self.name = name
        self.short_circuit = short_circuit
        self._steps: List[_LabeledStep[T]] = []
        self._before_each: List[Callable[[T], T]] = []
        self._after_each: List[Callable[[T], T]] = []
        self._metrics: Metrics = metrics or NoopMetrics()
        self.max_jumps: int = 1_000  # guardrail

    def before_each(self, fn: Callable[[T], T]) -> 'Pipeline[T]':
        self._before_each.append(fn); return self
    def after_each(self, fn: Callable[[T], T]) -> 'Pipeline[T]':
        self._after_each.append(fn); return self
    def step(self, fn: Callable[[T], T], *, label: Optional[str]=None, name: Optional[str]=None, section: str='main') -> 'Pipeline[T]':
        nm = name or getattr(fn, '__name__', 'step')
        self._steps.append(_LabeledStep(label=label, name=nm, fn=fn, section=section)); return self
    def add_steps(self, *fns: Callable[[T], T]) -> 'Pipeline[T]':
        for fn in fns: self.step(fn)
        return self

    def run(self, inp: T, *, start_label: Optional[str] = None, run_id: Optional[str]=None) -> T:
        rec = self._metrics.on_pipeline_start(self.name, run_id or str(int(time.time()*1e6)), start_label)
        t0_run = time.perf_counter_ns()

        pre_steps = [s for s in self._steps if s.section == 'pre']
        main_steps = [s for s in self._steps if s.section == 'main']
        post_steps = [s for s in self._steps if s.section == 'post']

        # Duplicate label check across all sections
        seen_labels: Dict[str, int] = {}
        for idx, s in enumerate(self._steps):
            if s.label:
                if s.label in seen_labels:
                    err = ValueError(f"Duplicate label detected: {s.label}")
                    rec.on_pipeline_end(False, time.perf_counter_ns()-t0_run, err)
                    raise err
                seen_labels[s.label] = idx

        # Build label index for MAIN only
        label_to_index: Dict[str, int] = {}
        for idx, s in enumerate(main_steps):
            if s.label:
                label_to_index[s.label] = idx

        if start_label:
            if start_label not in label_to_index:
                err = KeyError(f"Unknown or disallowed start label (must be a main-step label): {start_label}")
                rec.on_pipeline_end(False, time.perf_counter_ns()-t0_run, err)
                raise err
            start_index = label_to_index[start_label]
        else:
            start_index = 0

        cur: T = inp
        last_error: Optional[BaseException] = None

        # PRE once
        for s in pre_steps:
            rec.on_step_start(-1, s.label or s.name)
            try:
                t0 = time.perf_counter_ns()
                cur = s.fn(cur)
                rec.on_step_end(-1, s.label or s.name, time.perf_counter_ns()-t0, True)
            except Exception as ex:
                rec.on_step_error(-1, s.label or s.name, ex)
                last_error = ex
                if self.short_circuit:
                    rec.on_pipeline_end(False, time.perf_counter_ns()-t0_run, ex)
                    raise ex

        # MAIN
        i = start_index
        jumps = 0
        while i < len(main_steps):
            s = main_steps[i]
            rec.on_step_start(i, s.label or s.name)
            try:
                for b in self._before_each:
                    cur = b(cur)
                t0 = time.perf_counter_ns()
                cur = s.fn(cur)
                rec.on_step_end(i, s.label or s.name, time.perf_counter_ns()-t0, True)
                for a in self._after_each:
                    cur = a(cur)
                i += 1
            except ShortCircuit as sc:
                rec.on_step_end(i, s.label or s.name, 0, True)
                cur = sc.value
                last_error = None
                break
            except JumpSignal as js:
                jumps += 1
                if jumps > self.max_jumps:
                    last_error = RuntimeError(f"max_jumps exceeded ({self.max_jumps})")
                    rec.on_step_error(i, s.label or s.name, last_error)
                    break
                if js.delay_ms > 0:
                    time.sleep(js.delay_ms/1000.0)
                target = js.label
                if target not in label_to_index:
                    last_error = KeyError(f"Unknown jump label (must be a main-step label): {target}")
                    rec.on_step_error(i, s.label or s.name, last_error)
                    break
                rec.on_jump(s.label or s.name, target, js.delay_ms)
                i = label_to_index[target]
            except Exception as ex:
                rec.on_step_error(i, s.label or s.name, ex)
                last_error = ex
                if self.short_circuit:
                    break
                else:
                    i += 1

        # POST once
        if last_error is None:
            for s in post_steps:
                rec.on_step_start(-1, s.label or s.name)
                try:
                    t0 = time.perf_counter_ns()
                    cur = s.fn(cur)
                    rec.on_step_end(-1, s.label or s.name, time.perf_counter_ns()-t0, True)
                except Exception as ex:
                    rec.on_step_error(-1, s.label or s.name, ex)
                    last_error = ex
                    if self.short_circuit:
                        break

        rec.on_pipeline_end(last_error is None, time.perf_counter_ns()-t0_run, last_error)
        if last_error and self.short_circuit:
            raise last_error
        return cur

    @classmethod
    def builder(cls, name: str, *, short_circuit: bool=True, metrics: Optional[Metrics]=None) -> 'Pipeline[T]':
        return cls(name, short_circuit=short_circuit, metrics=metrics)

class Pipe(Generic[I, O]):
    """Typed pipeline (I -> O); no labels/jumps. pre/steps/post run in order."""
    def __init__(self, name: str, short_circuit: bool=True, metrics: Optional[Metrics]=None):
        self.name = name
        self.short_circuit = short_circuit
        self._steps: List[Callable[..., Any]] = []
        self._metrics: Metrics = metrics or NoopMetrics()

    def step(self, fn: Callable[..., Any]) -> 'Pipe[I, O]':
        self._steps.append(fn)
        return self

    def run(self, inp: I, *, run_id: Optional[str]=None) -> O:
        rec = self._metrics.on_pipeline_start(self.name, run_id or str(int(time.time()*1e6)), None)
        cur: Any = inp
        t0_run = time.perf_counter_ns()
        last_error: Optional[BaseException] = None
        for i, fn in enumerate(self._steps):
            name = getattr(fn, '__name__', f'step{i}')
            rec.on_step_start(i, name)
            try:
                t0 = time.perf_counter_ns()
                cur = fn(cur)
                rec.on_step_end(i, name, time.perf_counter_ns()-t0, True)
            except ShortCircuit as sc:
                cur = sc.value
                rec.on_step_end(i, name, 0, True)
                break
            except Exception as ex:
                rec.on_step_error(i, name, ex)
                last_error = ex
                if self.short_circuit:
                    break
        rec.on_pipeline_end(last_error is None, time.perf_counter_ns()-t0_run, last_error)
        if last_error and self.short_circuit:
            raise last_error
        return cur  # type: ignore[return-value]

class RuntimePipeline(Generic[T]):
    def __init__(self, name: str, *, short_circuit: bool=True, metrics: Optional[Metrics]=None):
        self.name = name
        self.short_circuit = short_circuit
        self._pre: List[Callable[[T], T]] = []
        self._steps: List[Callable[[T], T]] = []
        self._post: List[Callable[[T], T]] = []
        self._metrics: Metrics = metrics or NoopMetrics()
        self._ended = False
        self._current: Optional[T] = None

    def reset(self, value: T) -> None:
        self._current = value; self._ended = False

    def add_pre(self, fn: Callable[[T], T]) -> 'RuntimePipeline[T]':
        self._pre.append(fn)
        if not self._ended and self._current is not None:
            self._current = fn(self._current)
        return self

    def step(self, fn: Callable[[T], T]) -> 'RuntimePipeline[T]':
        self._steps.append(fn)
        if not self._ended and self._current is not None:
            try:
                self._current = fn(self._current)
            except ShortCircuit as sc:
                self._current = sc.value; self._ended = True
            except Exception:
                if self.short_circuit: self._ended = True
        return self

    def add_post(self, fn: Callable[[T], T]) -> 'RuntimePipeline[T]':
        self._post.append(fn)
        if not self._ended and self._current is not None:
            self._current = fn(self._current)
        return self

    def value(self) -> Optional[T]:
        return self._current

# === Java API parity: add_action helpers ===
from typing import Optional, Callable, Any

def pipeline_add_action(self, action_function: Callable[[Any], Any], label: Optional[str] = None):
    if label is not None and not isinstance(label, str):
        raise TypeError("label must be a string if provided")
    # Add to main section to allow jumps/labels to match Java semantics
    return self.step(action_function, label=label, section="main")

def pipe_add_action(self, action_function: Callable[[Any], Any]):
    # Typed pipe is linear; just append the step
    return self.step(action_function)

try:
    Pipeline.add_action = pipeline_add_action  # type: ignore[attr-defined]
except Exception:
    pass
try:
    Pipe.add_action = pipe_add_action  # type: ignore[attr-defined]
except Exception:
    pass

# === Java API parity: additional Pipeline methods and run wrapper ===
from typing import Callable, Optional, Any
import time

def pipeline_before(self, label: str, action_function: Callable[[Any], Any]):
    if not isinstance(label, str) or len(label.strip()) == 0:
        raise ValueError("before(label, fn) requires a non-empty label")
    target_index = -1
    for i, s in enumerate(self._steps):
        if s.section == 'main' and s.label == label:
            target_index = i
            break
    if target_index < 0:
        raise ValueError(f"before: label not found among main steps: {label}")
    name_value = getattr(action_function, '__name__', 'step')
    self._steps.insert(target_index, _LabeledStep(label=None, name=name_value, fn=action_function, section='main'))
    return self

def pipeline_after(self, label: str, action_function: Callable[[Any], Any]):
    if not isinstance(label, str) or len(label.strip()) == 0:
        raise ValueError("after(label, fn) requires a non-empty label")
    target_index = -1
    for i, s in enumerate(self._steps):
        if s.section == 'main' and s.label == label:
            target_index = i
            break
    if target_index < 0:
        raise ValueError(f"after: label not found among main steps: {label}")
    name_value = getattr(action_function, '__name__', 'step')
    self._steps.insert(target_index + 1, _LabeledStep(label=None, name=name_value, fn=action_function, section='main'))
    return self

def pipeline_jump_to(self, label: str):
    self.default_start_label = str(label)
    return self

def pipeline_set_metrics(self, metrics: Metrics):
    self._metrics = metrics
    return self

def pipeline_set_max_jumps_per_run(self, n: int):
    try:
        value = int(n)
    except Exception:
        value = 0
    if value < 0:
        value = 0
    self.max_jumps = value
    return self

def pipeline_enable_jumps(self, enabled: bool):
    if enabled:
        prev = getattr(self, 'previous_max_jumps', None)
        if isinstance(prev, int) and prev >= 0:
            self.max_jumps = prev
        elif not isinstance(getattr(self, 'max_jumps', None), int) or self.max_jumps <= 0:
            self.max_jumps = 1000
    else:
        self.previous_max_jumps = getattr(self, 'max_jumps', 1000)
        self.max_jumps = 0
    return self

def pipeline_set_name(self, new_name: str):
    self.name = str(new_name)
    return self

def pipeline_set_short_circuit(self, enabled: bool):
    self.short_circuit = bool(enabled)
    return self

def pipeline_on_error_return(self, handler: Callable[[Exception], Any]):
    if not callable(handler):
        raise ValueError("on_error_return(handler) requires a function")
    self.error_handler = handler
    return self

def pipeline_sleeper(self, sleeper_fn: Callable[[int], None]):
    if not callable(sleeper_fn):
        raise ValueError("sleeper(fn) requires a function that accepts milliseconds")
    self.sleeper_function = sleeper_fn
    return self

def pipeline_fork(self):
    clone = Pipeline(self.name, self.short_circuit, self._metrics)
    clone.max_jumps = getattr(self, 'max_jumps', 1000)
    clone._steps = [_LabeledStep(label=s.label, name=s.name, fn=s.fn, section=s.section) for s in self._steps]
    clone._before_each = list(getattr(self, '_before_each', []))
    clone._after_each = list(getattr(self, '_after_each', []))
    for attr in ('default_start_label', 'error_handler', 'previous_max_jumps'):
        if hasattr(self, attr):
            setattr(clone, attr, getattr(self, attr))
    return clone

def pipeline_is_sealed(self) -> bool:
    return bool(getattr(self, 'sealed', False))

def pipeline_add_bean(self, bean_id: str, instance: Any):
    beans = getattr(self, 'beans', None)
    if beans is None:
        beans = {}
        setattr(self, 'beans', beans)
    beans[str(bean_id)] = instance
    return self

def pipeline_add_pipeline_config(self, text_or_path: str):
    from ..config.json_loader import PipelineJsonLoader
    loader = PipelineJsonLoader(beans=getattr(self, 'beans', {}), metrics=self._metrics)
    txt = str(text_or_path or "").strip()
    if txt.startswith('{') or txt.startswith('['):
        loaded = loader.load_str(txt)
    else:
        loaded = loader.load_file(txt)
    # Merge steps and hooks
    self._steps.extend(getattr(loaded, '_steps', []))
    be = getattr(loaded, '_before_each', [])
    ae = getattr(loaded, '_after_each', [])
    if be: getattr(self, '_before_each', []).extend(be)
    if ae: getattr(self, '_after_each', []).extend(ae)
    return self

# Monkey-patch methods onto Pipeline
try:
    Pipeline.before = pipeline_before  # type: ignore
    Pipeline.after = pipeline_after  # type: ignore
    Pipeline.jump_to = pipeline_jump_to  # type: ignore
    Pipeline.set_metrics = pipeline_set_metrics  # type: ignore
    Pipeline.set_max_jumps_per_run = pipeline_set_max_jumps_per_run  # type: ignore
    Pipeline.enable_jumps = pipeline_enable_jumps  # type: ignore
    Pipeline.set_name = pipeline_set_name  # type: ignore
    Pipeline.set_short_circuit = pipeline_set_short_circuit  # type: ignore
    Pipeline.on_error_return = pipeline_on_error_return  # type: ignore
    Pipeline.sleeper = pipeline_sleeper  # type: ignore
    Pipeline.fork = pipeline_fork  # type: ignore
    Pipeline.is_sealed = pipeline_is_sealed  # type: ignore
    Pipeline.add_bean = pipeline_add_bean  # type: ignore
    Pipeline.add_pipeline_config = pipeline_add_pipeline_config  # type: ignore
except Exception:
    pass

# Wrap run to support default start label, sleeper override, and on_error_return
try:
    Pipeline.run_original = Pipeline.run  # type: ignore
    def run(self, inp, *, start_label: Optional[str] = None, run_id: Optional[str] = None):  # type: ignore[override]
        chosen_label = start_label if start_label is not None else getattr(self, 'default_start_label', None)
        using_override = False
        old_sleep = None
        if hasattr(self, 'sleeper_function') and callable(getattr(self, 'sleeper_function')):
            using_override = True
            old_sleep = time.sleep
            def sleep_override(seconds: float):
                try:
                    millis = int(seconds * 1000.0)
                except Exception:
                    millis = 0
                self.sleeper_function(millis)  # type: ignore[attr-defined]
            time.sleep = sleep_override  # type: ignore[assignment]
        self.sealed = True
        try:
            return Pipeline.run_original(self, inp, start_label=chosen_label, run_id=run_id)  # type: ignore[attr-defined]
        except Exception as err:
            handler = getattr(self, 'error_handler', None)
            if callable(handler):
                return handler(err)
            raise
        finally:
            if using_override and old_sleep is not None:
                time.sleep = old_sleep  # type: ignore[assignment]
    Pipeline.run = run  # type: ignore[assignment]
except Exception:
    pass
