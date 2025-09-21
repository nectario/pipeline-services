from python import PythonObject
from time import perf_counter_ns, sleep
from collections.list import List
from collections.dict import Dict

from .metrics import Metrics, NoopMetrics
from .jumps import reset_signals, get_current_signals
alias StepFunction = fn(input_value: PythonObject) raises -> PythonObject

struct LabeledStep:
    var label: String
    var name: String
    var function_pointer: StepFunction
    var section: String   # "pre" | "main" | "post"

    fn __init__(out self, label: String, name: String, function_pointer: StepFunction, section: String):
        self.label = label
        self.name = name
        self.function_pointer = function_pointer
        self.section = section

struct Pipeline:
    var name: String
    var short_circuit_enabled: Bool
    var steps: List[LabeledStep]
    var before_each_steps: List[StepFunction]
    var after_each_steps: List[StepFunction]
    var metrics: Metrics
    var max_jumps: Int

    fn __init__(out self, name: String, short_circuit: Bool = True, metrics: Metrics = NoopMetrics()):
        self.name = name
        self.short_circuit_enabled = short_circuit
        self.steps = List[LabeledStep]()
        self.before_each_steps = List[StepFunction]()
        self.after_each_steps = List[StepFunction]()
        self.metrics = metrics
        self.max_jumps = 1000

    fn before_each(self, step_function: StepFunction) -> Self:
        self.before_each_steps.push_back(step_function); return self

    fn after_each(self, step_function: StepFunction) -> Self:
        self.after_each_steps.push_back(step_function); return self

    fn step(self, step_function: StepFunction, label: String = "", name: String = "", section: String = "main") -> Self:
        var step_name: String = name if name != "" else "step"
        var labeled = LabeledStep(label, step_name, step_function, section)
        self.steps.push_back(labeled); return self

    fn add_steps(self, first: StepFunction) -> Self:
        self.step(first); return self

    fn run(self, input_value: PythonObject, start_label: String = "", run_id: String = "") raises -> PythonObject:
        var run_scope = self.metrics.on_pipeline_start(self.name, run_id, start_label)
        var run_start_ns: Int = perf_counter_ns()

        # Partition steps
        var pre_steps = List[LabeledStep]()
        var main_steps = List[LabeledStep]()
        var post_steps = List[LabeledStep]()

        var build_index: Int = 0
        while build_index < Int(len(self.steps)):
            var step_to_index = self.steps[build_index]
            if step_to_index.section == "pre":
                pre_steps.push_back(step_to_index)
            elif step_to_index.section == "post":
                post_steps.push_back(step_to_index)
            else:
                main_steps.push_back(step_to_index)
            build_index = build_index + 1

        # Duplicate label check across all sections
        var label_seen = Dict[String, Int]()
        var label_scan_index: Int = 0
        while label_scan_index < Int(len(self.steps)):
            var s = self.steps[label_scan_index]
            if s.label != "":
                if s.label in label_seen:
                    var msg = "Duplicate label detected: " + s.label
                    run_scope.on_pipeline_end(False, Int(perf_counter_ns()-run_start_ns), msg)
                    raise msg
                label_seen[s.label] = label_scan_index
            label_scan_index = label_scan_index + 1

        # Build label index for MAIN only
        var label_to_index = Dict[String, Int]()
        var main_index: Int = 0
        while main_index < Int(len(main_steps)):
            var ms = main_steps[main_index]
            if ms.label != "":
                label_to_index[ms.label] = main_index
            main_index = main_index + 1

        # Validate start_label (must be main)
        var index: Int = 0
        if start_label != "":
            if start_label in label_to_index:
                index = label_to_index[start_label]
            else:
                var msg = "Unknown or disallowed start label (must be a main-step label): " + start_label
                run_scope.on_pipeline_end(False, Int(perf_counter_ns()-run_start_ns), msg)
                raise msg
        else:
            index = 0

        var current_value: PythonObject = input_value
        var last_error_message: String = ""

        # PRE once
        var pre_index: Int = 0
        while pre_index < Int(len(pre_steps)):
            var pre_step = pre_steps[pre_index]
            run_scope.on_step_start(-1, pre_step.label if pre_step.label != "" else pre_step.name)
            try:
                var t0 = perf_counter_ns()
                current_value = pre_step.function_pointer(current_value)
                run_scope.on_step_end(-1, pre_step.label if pre_step.label != "" else pre_step.name, Int(perf_counter_ns()-t0), True)
            except caught_error:
                var msg = String(caught_error)
                run_scope.on_step_error(-1, pre_step.label if pre_step.label != "" else pre_step.name, msg)
                last_error_message = msg
                if self.short_circuit_enabled:
                    run_scope.on_pipeline_end(False, Int(perf_counter_ns()-run_start_ns), msg)
                    raise msg
            pre_index = pre_index + 1

        # MAIN with jumps
        var jump_count: Int = 0
        while index < Int(len(main_steps)):
            var current_step = main_steps[index]
            var step_label: String = current_step.label
            var label_or_name: String = step_label if step_label != "" else current_step.name

            run_scope.on_step_start(index, label_or_name)
            reset_signals()

            try:
                # before_each
                var before_each_index: Int = 0
                while before_each_index < Int(len(self.before_each_steps)):
                    var before_function = self.before_each_steps[before_each_index]
                    current_value = before_function(current_value)
                    before_each_index = before_each_index + 1

                var step_start_ns: Int = perf_counter_ns()
                current_value = current_step.function_pointer(current_value)
                var elapsed_ns: Int = Int(perf_counter_ns() - step_start_ns)
                run_scope.on_step_end(index, label_or_name, elapsed_ns, True)

                # after_each
                var after_each_index: Int = 0
                while after_each_index < Int(len(self.after_each_steps)):
                    var after_function = self.after_each_steps[after_each_index]
                    current_value = after_function(current_value)
                    after_each_index = after_each_index + 1

                index = index + 1
            except caught_error:
                var signals = get_current_signals()
                if signals.signal_kind == "short":
                    run_scope.on_step_end(index, label_or_name, 0, True)
                    current_value = signals.short_value
                    last_error_message = ""
                    break
                elif signals.signal_kind == "jump":
                    jump_count = jump_count + 1
                    if jump_count > self.max_jumps:
                        var msg = "max_jumps exceeded"
                        run_scope.on_step_error(index, label_or_name, msg)
                        last_error_message = msg
                        break
                    if signals.jump_delay_ms > 0:
                        var seconds: Float64 = Float64(signals.jump_delay_ms) / 1000.0
                        sleep(seconds)
                    if signals.jump_label in label_to_index:
                        var to_index: Int = label_to_index[signals.jump_label]
                        run_scope.on_jump(label_or_name, signals.jump_label, signals.jump_delay_ms)
                        index = to_index
                        continue
                    else:
                        var msg = "Unknown jump label (must be a main-step label): " + signals.jump_label
                        run_scope.on_step_error(index, label_or_name, msg)
                        last_error_message = msg
                        break
                else:
                    var msg = String(caught_error)
                    run_scope.on_step_error(index, label_or_name, msg)
                    last_error_message = msg
                    if self.short_circuit_enabled:
                        break

        # POST once
        if last_error_message == "":
            var post_index: Int = 0
            while post_index < Int(len(post_steps)):
                var post_step = post_steps[post_index]
                run_scope.on_step_start(-1, post_step.label if post_step.label != "" else post_step.name)
                try:
                    var t0 = perf_counter_ns()
                    current_value = post_step.function_pointer(current_value)
                    run_scope.on_step_end(-1, post_step.label if post_step.label != "" else post_step.name, Int(perf_counter_ns()-t0), True)
                except caught_error:
                    var msg = String(caught_error)
                    run_scope.on_step_error(-1, post_step.label if post_step.label != "" else post_step.name, msg)
                    last_error_message = msg
                    if self.short_circuit_enabled:
                        break
                post_index = post_index + 1

        var total_elapsed_ns: Int = Int(perf_counter_ns() - run_start_ns)
        var success_flag: Bool = last_error_message == ""
        run_scope.on_pipeline_end(success_flag, total_elapsed_ns, last_error_message)

        if last_error_message != "" and self.short_circuit_enabled:
            raise last_error_message
        return current_value

    @staticmethod
    fn builder(name: String, short_circuit: Bool = True, metrics: Metrics = NoopMetrics()) -> Pipeline:
        var p = Pipeline(name, short_circuit, metrics)
        return p

alias TypedStepFunction = fn(input_value: PythonObject) raises -> PythonObject

struct Pipe:
    var name: String
    var short_circuit_enabled: Bool
    var steps: List[TypedStepFunction]
    var metrics: Metrics

    fn __init__(out self, name: String, short_circuit: Bool = True, metrics: Metrics = NoopMetrics()):
        self.name = name
        self.short_circuit_enabled = short_circuit
        self.steps = List[TypedStepFunction]()
        self.metrics = metrics

    fn step(self, step_function: TypedStepFunction) -> Self:
        self.steps.push_back(step_function); return self

    fn run(self, input_value: PythonObject, run_id: String = "") raises -> PythonObject:
        var run_scope = self.metrics.on_pipeline_start(self.name, run_id, "")
        var run_start_ns: Int = perf_counter_ns()
        var current_value: PythonObject = input_value
        var last_error_message: String = ""

        var loop_index: Int = 0
        while loop_index < Int(len(self.steps)):
            var function_pointer = self.steps[loop_index]
            var step_name: String = "step"
            run_scope.on_step_start(loop_index, step_name)
            try:
                var step_start_ns: Int = perf_counter_ns()
                current_value = function_pointer(current_value)
                var elapsed_ns: Int = Int(perf_counter_ns() - step_start_ns)
                run_scope.on_step_end(loop_index, step_name, elapsed_ns, True)
            except caught_error:
                last_error_message = String(caught_error)
                run_scope.on_step_error(loop_index, step_name, last_error_message)
                if self.short_circuit_enabled:
                    break
            loop_index = loop_index + 1

        var total_elapsed_ns: Int = Int(perf_counter_ns() - run_start_ns)
        var success_flag: Bool = last_error_message == ""
        run_scope.on_pipeline_end(success_flag, total_elapsed_ns, last_error_message)

        if last_error_message != "" and self.short_circuit_enabled:
            raise last_error_message
        return current_value

struct RuntimePipeline:
    var name: String
    var short_circuit_enabled: Bool
    var pre_steps: List[StepFunction]
    var main_steps: List[StepFunction]
    var post_steps: List[StepFunction]
    var ended: Bool
    var current_value: PythonObject

    fn __init__(out self, name: String, short_circuit: Bool = True):
        self.name = name
        self.short_circuit_enabled = short_circuit
        self.pre_steps = List[StepFunction]()
        self.main_steps = List[StepFunction]()
        self.post_steps = List[StepFunction]()
        self.ended = False
        self.current_value = PythonObject(None)

    fn reset(self, value: PythonObject) -> None:
        self.current_value = value; self.ended = False

    fn add_pre(self, step_function: StepFunction) raises -> Self:
        self.pre_steps.push_back(step_function)
        if not self.ended and self.current_value is not None:
            self.current_value = step_function(self.current_value)
        return self

    fn step(self, step_function: StepFunction) raises -> Self:
        self.main_steps.push_back(step_function)
        if not self.ended and self.current_value is not None:
            try:
                self.current_value = step_function(self.current_value)
            except caught_error:
                if self.short_circuit_enabled:
                    self.ended = True
        return self

    fn add_post(self, step_function: StepFunction) raises -> Self:
        self.post_steps.push_back(step_function)
        if not self.ended and self.current_value is not None:
            self.current_value = step_function(self.current_value)
        return self

    fn value(self) -> PythonObject:
        return self.current_value

# === Java API parity: add_action helpers ===
# Adds an unlabeled action into the main section.
fn add_action(self, step_function: StepFunction) -> Self:
    return self.step(step_function, label = "", name = "", section = "main")

# Adds a labeled action into the main section.
fn add_action_with_label(self, label: String, step_function: StepFunction) -> Self:
    return self.step(step_function, label = label, name = "", section = "main")
