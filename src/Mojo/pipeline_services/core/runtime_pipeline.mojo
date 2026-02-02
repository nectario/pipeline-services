from python import PythonObject
from collections.list import List

from .pipeline import Pipeline, RegisteredAction, StepAction, ActionControl, UnaryOperator, format_step_name, safe_error_to_string
from ..remote.http_step import RemoteSpec, http_step

struct RuntimePipeline:
    var name: String
    var short_circuit_on_exception: Bool

    var current: PythonObject
    var ended: Bool

    var pre_actions: List[RegisteredAction]
    var actions: List[RegisteredAction]
    var post_actions: List[RegisteredAction]

    var pre_index: Int
    var action_index: Int
    var post_index: Int

    var control: ActionControl

    fn __init__(out self,
                name: String,
                short_circuit_on_exception: Bool = True,
                initial: PythonObject = PythonObject(None)):
        self.name = name
        self.short_circuit_on_exception = short_circuit_on_exception
        self.current = initial
        self.ended = False
        self.pre_actions = List[RegisteredAction]()
        self.actions = List[RegisteredAction]()
        self.post_actions = List[RegisteredAction]()
        self.pre_index = 0
        self.action_index = 0
        self.post_index = 0
        self.control = ActionControl(name)

    fn value(self) -> PythonObject:
        return self.current

    fn reset(mut self, value: PythonObject) -> None:
        self.current = value
        self.ended = False
        self.control.reset()

    fn add_pre_action(mut self, action: StepAction) -> PythonObject:
        if self.ended:
            return self.current
        var registered_action = RegisteredAction("", action)
        self.pre_actions.append(registered_action)
        var output_value = self.apply_action(registered_action, "pre", self.pre_index)
        self.pre_index = self.pre_index + 1
        return output_value

    fn add_pre_action(mut self, action: UnaryOperator) -> PythonObject:
        if self.ended:
            return self.current
        var registered_action = RegisteredAction("", action)
        self.pre_actions.append(registered_action)
        var output_value = self.apply_action(registered_action, "pre", self.pre_index)
        self.pre_index = self.pre_index + 1
        return output_value

    fn add_pre_action(mut self, spec: RemoteSpec) -> PythonObject:
        if self.ended:
            return self.current
        var registered_action = RegisteredAction("", spec)
        self.pre_actions.append(registered_action)
        var output_value = self.apply_action(registered_action, "pre", self.pre_index)
        self.pre_index = self.pre_index + 1
        return output_value

    fn add_action(mut self, action: StepAction) -> PythonObject:
        if self.ended:
            return self.current
        var registered_action = RegisteredAction("", action)
        self.actions.append(registered_action)
        var output_value = self.apply_action(registered_action, "main", self.action_index)
        self.action_index = self.action_index + 1
        return output_value

    fn add_action(mut self, action: UnaryOperator) -> PythonObject:
        if self.ended:
            return self.current
        var registered_action = RegisteredAction("", action)
        self.actions.append(registered_action)
        var output_value = self.apply_action(registered_action, "main", self.action_index)
        self.action_index = self.action_index + 1
        return output_value

    fn add_action(mut self, spec: RemoteSpec) -> PythonObject:
        if self.ended:
            return self.current
        var registered_action = RegisteredAction("", spec)
        self.actions.append(registered_action)
        var output_value = self.apply_action(registered_action, "main", self.action_index)
        self.action_index = self.action_index + 1
        return output_value

    fn add_post_action(mut self, action: StepAction) -> PythonObject:
        if self.ended:
            return self.current
        var registered_action = RegisteredAction("", action)
        self.post_actions.append(registered_action)
        var output_value = self.apply_action(registered_action, "post", self.post_index)
        self.post_index = self.post_index + 1
        return output_value

    fn add_post_action(mut self, action: UnaryOperator) -> PythonObject:
        if self.ended:
            return self.current
        var registered_action = RegisteredAction("", action)
        self.post_actions.append(registered_action)
        var output_value = self.apply_action(registered_action, "post", self.post_index)
        self.post_index = self.post_index + 1
        return output_value

    fn add_post_action(mut self, spec: RemoteSpec) -> PythonObject:
        if self.ended:
            return self.current
        var registered_action = RegisteredAction("", spec)
        self.post_actions.append(registered_action)
        var output_value = self.apply_action(registered_action, "post", self.post_index)
        self.post_index = self.post_index + 1
        return output_value

    fn to_immutable(self) -> Pipeline:
        var pipeline = Pipeline(self.name, self.short_circuit_on_exception)

        var index: Int = 0
        while index < Int(len(self.pre_actions)):
            var registered_action = self.pre_actions[index]
            if registered_action.kind == 0:
                pipeline.add_pre_action(registered_action.unary)
            elif registered_action.kind == 1:
                pipeline.add_pre_action(registered_action.step_action)
            else:
                pipeline.add_pre_action(registered_action.remote_spec)
            index = index + 1

        index = 0
        while index < Int(len(self.actions)):
            var registered_action = self.actions[index]
            if registered_action.kind == 0:
                pipeline.add_action(registered_action.unary)
            elif registered_action.kind == 1:
                pipeline.add_action(registered_action.step_action)
            else:
                pipeline.add_action(registered_action.remote_spec)
            index = index + 1

        index = 0
        while index < Int(len(self.post_actions)):
            var registered_action = self.post_actions[index]
            if registered_action.kind == 0:
                pipeline.add_post_action(registered_action.unary)
            elif registered_action.kind == 1:
                pipeline.add_post_action(registered_action.step_action)
            else:
                pipeline.add_post_action(registered_action.remote_spec)
            index = index + 1

        return pipeline^

    fn freeze(self) -> Pipeline:
        return self.to_immutable()

    fn apply_action(mut self, registered_action: RegisteredAction, phase: String, index: Int) -> PythonObject:
        if self.ended:
            return self.current

        var step_name = format_step_name(phase, index, registered_action.name)
        self.control.begin_step(phase, index, step_name)
        try:
            if registered_action.kind == 0:
                self.current = registered_action.unary(self.current)
            elif registered_action.kind == 1:
                self.current = registered_action.step_action(self.current, self.control)
            else:
                self.current = http_step(registered_action.remote_spec, self.current)
        except caught_error:
            self.current = self.control.record_error(self.current, safe_error_to_string(caught_error))
            if self.short_circuit_on_exception:
                self.control.short_circuit()
                self.ended = True
        if self.control.is_short_circuited():
            self.ended = True
        return self.current
