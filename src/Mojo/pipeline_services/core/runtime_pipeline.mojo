from std.python import PythonObject
from std.collections import List

from .pipeline import Action, ActionFunction, Pipeline, PipelineResult


struct RuntimePipeline(Movable):
    """Deprecated immediate-execution facade backed by canonical Pipelines."""

    var pipeline_name: String
    var short_circuit_on_exception: Bool
    var current_context: PythonObject
    var ended: Bool
    var pre_actions: List[Action]
    var actions: List[Action]
    var post_actions: List[Action]

    def __init__(
        out self,
        pipeline_name: String,
        short_circuit_on_exception: Bool,
        initial_context: PythonObject,
    ):
        self.pipeline_name = pipeline_name
        self.short_circuit_on_exception = short_circuit_on_exception
        self.current_context = initial_context
        self.ended = False
        self.pre_actions = List[Action]()
        self.actions = List[Action]()
        self.post_actions = List[Action]()

    def value(self) -> PythonObject:
        return self.current_context

    def reset(mut self, context: PythonObject):
        self.current_context = context
        self.ended = False

    def add_pre_action(
        mut self,
        action: ActionFunction,
    ) raises -> PythonObject:
        return self.add_pre_action(Action(action))

    def add_pre_action(
        mut self,
        action: Action,
    ) raises -> PythonObject:
        if self.ended:
            return self.current_context
        self.pre_actions.append(action)
        return self._apply_action(action, "preActions")

    def add_action(
        mut self,
        action: ActionFunction,
    ) raises -> PythonObject:
        return self.add_action(Action(action))

    def add_action(
        mut self,
        action: Action,
    ) raises -> PythonObject:
        if self.ended:
            return self.current_context
        self.actions.append(action)
        return self._apply_action(action, "actions")

    def add_post_action(
        mut self,
        action: ActionFunction,
    ) raises -> PythonObject:
        return self.add_post_action(Action(action))

    def add_post_action(
        mut self,
        action: Action,
    ) raises -> PythonObject:
        if self.ended:
            return self.current_context
        self.post_actions.append(action)
        return self._apply_action(action, "postActions")

    def freeze(self) raises -> Pipeline:
        var pipeline = Pipeline(
            self.pipeline_name,
            self.short_circuit_on_exception,
        )
        for action in self.pre_actions:
            pipeline.add_pre_action(action)
        for action in self.actions:
            pipeline.add_action(action)
        for action in self.post_actions:
            pipeline.add_post_action(action)
        pipeline.freeze()
        return pipeline^

    def to_immutable(self) raises -> Pipeline:
        return self.freeze()

    def _apply_action(
        mut self,
        action: Action,
        phase: String,
    ) raises -> PythonObject:
        var pipeline = Pipeline(
            self.pipeline_name + ":runtime",
            self.short_circuit_on_exception,
        )
        if phase == "preActions":
            pipeline.add_pre_action(action)
        elif phase == "postActions":
            pipeline.add_post_action(action)
        else:
            pipeline.add_action(action)

        var result = pipeline.run_detailed(self.current_context)
        self.current_context = result.context
        self.ended = result.short_circuited
        return self.current_context
