from std.python import Python, PythonObject
from std.collections import List
from std.memory import ArcPointer

from .pipeline import Pipeline, PipelineResult


comptime PipelineFactory = def() thin raises -> Pipeline


struct PipelineProviderMode(ImplicitlyCopyable, Equatable):
    var value: Int

    def __init__(out self, value: Int):
        self.value = value

    @staticmethod
    def new_instance_per_event() -> PipelineProviderMode:
        return PipelineProviderMode(0)

    @staticmethod
    def singleton() -> PipelineProviderMode:
        return PipelineProviderMode(1)

    @staticmethod
    def pooled() -> PipelineProviderMode:
        return PipelineProviderMode(2)

    def __eq__(self, other: PipelineProviderMode) -> Bool:
        return self.value == other.value

    def __ne__(self, other: PipelineProviderMode) -> Bool:
        return self.value != other.value


def default_instance_count() raises -> Int:
    var processor_count = 1
    try:
        var os_module = Python.import_module("os")
        var cpu_count_value = os_module.cpu_count()
        if cpu_count_value is not None:
            processor_count = Int(py=cpu_count_value)
    except:
        processor_count = 1

    if processor_count < 1:
        processor_count = 1
    return processor_count


def _default_pipeline_factory() raises -> Pipeline:
    return Pipeline("pipeline", True)


struct PipelineProvider(Movable):
    var provider_mode: PipelineProviderMode
    var singleton_pipeline: ArcPointer[Pipeline]
    var pooled_pipelines: List[ArcPointer[Pipeline]]
    var pipeline_factory: PipelineFactory
    var next_pipeline_index: Int

    def __init__(out self, var singleton_pipeline: Pipeline) raises:
        singleton_pipeline.freeze()
        self.provider_mode = PipelineProviderMode.singleton()
        self.singleton_pipeline = ArcPointer(singleton_pipeline^)
        self.pooled_pipelines = List[ArcPointer[Pipeline]]()
        self.pipeline_factory = _default_pipeline_factory
        self.next_pipeline_index = 0

    def __init__(
        out self,
        pipeline_factory: PipelineFactory,
    ) raises:
        self.provider_mode = PipelineProviderMode.new_instance_per_event()
        self.singleton_pipeline = ArcPointer(Pipeline("unused", True))
        self.pooled_pipelines = List[ArcPointer[Pipeline]]()
        self.pipeline_factory = pipeline_factory
        self.next_pipeline_index = 0

    def __init__(
        out self,
        pipeline_factory: PipelineFactory,
        instance_count: Int,
    ) raises:
        if instance_count < 1:
            raise "instance_count must be >= 1"
        self.provider_mode = PipelineProviderMode.pooled()
        self.singleton_pipeline = ArcPointer(Pipeline("unused", True))
        self.pooled_pipelines = List[ArcPointer[Pipeline]]()
        self.pipeline_factory = pipeline_factory
        self.next_pipeline_index = 0

        var index = 0
        while index < instance_count:
            var pipeline = pipeline_factory()
            pipeline.freeze()
            self.pooled_pipelines.append(ArcPointer(pipeline^))
            index += 1

    @staticmethod
    def new_instance_per_event(
        pipeline_factory: PipelineFactory,
    ) raises -> PipelineProvider:
        return PipelineProvider(pipeline_factory)

    @staticmethod
    def singleton(var pipeline: Pipeline) raises -> PipelineProvider:
        return PipelineProvider(pipeline^)

    @staticmethod
    def pooled(
        pipeline_factory: PipelineFactory,
        instance_count: Int,
    ) raises -> PipelineProvider:
        return PipelineProvider(pipeline_factory, instance_count)

    def mode(self) -> PipelineProviderMode:
        return self.provider_mode

    def instance_count(self) -> Int:
        if self.provider_mode == PipelineProviderMode.new_instance_per_event():
            return 0
        if self.provider_mode == PipelineProviderMode.pooled():
            return Int(len(self.pooled_pipelines))
        return 1

    def get_pipeline(mut self) raises -> ArcPointer[Pipeline]:
        if self.provider_mode == PipelineProviderMode.singleton():
            return self.singleton_pipeline

        if self.provider_mode == PipelineProviderMode.pooled():
            var selected_index = self.next_pipeline_index
            self.next_pipeline_index = (
                self.next_pipeline_index + 1
            ) % Int(len(self.pooled_pipelines))
            return self.pooled_pipelines[selected_index]

        var pipeline = self.pipeline_factory()
        pipeline.freeze()
        return ArcPointer(pipeline^)

    def run(
        mut self,
        input_value: PythonObject,
    ) raises -> PythonObject:
        var pipeline = self.get_pipeline()
        return pipeline[].run(input_value)

    def run_detailed(
        mut self,
        input_value: PythonObject,
    ) raises -> PipelineResult:
        var pipeline = self.get_pipeline()
        return pipeline[].run_detailed(input_value)
