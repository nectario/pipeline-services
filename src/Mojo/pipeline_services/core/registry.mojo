from collections.dict import Dict

from .pipeline import StepAction, UnaryOperator

struct PipelineRegistry:
    var unary_actions: Dict[String, UnaryOperator]
    var step_actions: Dict[String, StepAction]

    fn __init__(out self):
        self.unary_actions = Dict[String, UnaryOperator]()
        self.step_actions = Dict[String, StepAction]()

    fn register_unary(mut self, name: String, action: UnaryOperator) -> None:
        self.unary_actions[name] = action

    fn register_action(mut self, name: String, action: StepAction) -> None:
        self.step_actions[name] = action

    fn has_unary(self, name: String) -> Bool:
        return name in self.unary_actions

    fn has_action(self, name: String) -> Bool:
        return name in self.step_actions

    fn get_unary(self, name: String) raises -> UnaryOperator:
        if name in self.unary_actions:
            return self.unary_actions[name]
        raise "Unknown unary action: " + name

    fn get_action(self, name: String) raises -> StepAction:
        if name in self.step_actions:
            return self.step_actions[name]
        raise "Unknown step action: " + name

