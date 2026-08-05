from std.collections import Dict

from .pipeline import Action, ActionFunction


struct PipelineRegistry(Movable):
    var actions: Dict[String, Action]

    def __init__(out self):
        self.actions = Dict[String, Action]()

    def register_action(mut self, name: String, action: Action) raises:
        if name == "":
            raise "name must not be blank"
        self.actions[name] = action

    def register_action(
        mut self,
        name: String,
        action: ActionFunction,
    ) raises:
        self.register_action(name, Action(action))

    def register_unary(
        mut self,
        name: String,
        action: ActionFunction,
    ) raises:
        self.register_action(name, action)

    def has_action(self, name: String) -> Bool:
        return name in self.actions

    def has_unary(self, name: String) -> Bool:
        return self.has_action(name)

    def get_action(self, name: String) raises -> Action:
        if name in self.actions:
            return self.actions[name]
        raise "Unknown Action: " + name

    def get_unary(self, name: String) raises -> Action:
        return self.get_action(name)
