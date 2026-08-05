from std.python import PythonObject
from std.collections import List
from std.memory import ArcPointer

from .pipeline import PipelineResult
from .pipeline_provider import PipelineProvider


comptime PipelineRoute = def(PythonObject) thin raises -> Int


struct PipelineRouter(Movable):
    var providers: List[ArcPointer[PipelineProvider]]
    var route: PipelineRoute

    def __init__(
        out self,
        providers: List[ArcPointer[PipelineProvider]],
        route: PipelineRoute,
    ) raises:
        if len(providers) == 0:
            raise "providers must not be empty"
        self.providers = providers.copy()
        self.route = route

    def get_pipeline_provider(
        self,
        event: PythonObject,
    ) raises -> ArcPointer[PipelineProvider]:
        var selected_index = self.route(event)
        if selected_index < 0 or selected_index >= Int(len(self.providers)):
            raise "PipelineRouter selected an invalid provider index"
        return self.providers[selected_index]

    def run(
        self,
        event: PythonObject,
        input_value: PythonObject,
    ) raises -> PythonObject:
        var provider = self.get_pipeline_provider(event)
        return provider[].run(input_value)

    def run_detailed(
        self,
        event: PythonObject,
        input_value: PythonObject,
    ) raises -> PipelineResult:
        var provider = self.get_pipeline_provider(event)
        return provider[].run_detailed(input_value)
