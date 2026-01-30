from python import Python
from python import PythonObject
from collections.list import List
from memory.arc import ArcPointer

from .pipeline import Pipeline, PipelineResult

comptime PipelineFactory = fn() -> Pipeline


struct PipelineProviderMode(ImplicitlyCopyable):
    var value: Int

    fn __init__(out self, value: Int):
        self.value = value

    fn shared() -> PipelineProviderMode:
        return PipelineProviderMode(0)

    fn pooled() -> PipelineProviderMode:
        return PipelineProviderMode(1)

    fn per_run() -> PipelineProviderMode:
        return PipelineProviderMode(2)


fn default_pool_max() -> Int:
    var processor_count: Int = 1
    try:
        var os_module = Python.import_module("os")
        var cpu_count_value = os_module.cpu_count()
        if cpu_count_value is not None:
            processor_count = Int(cpu_count_value)
    except:
        processor_count = 1

    var computed = processor_count * 8
    if computed < 1:
        computed = 1
    if computed > 256:
        computed = 256
    return computed


fn default_pipeline_factory() -> Pipeline:
    return Pipeline("pipeline", True)


struct PipelinePool(Movable):
    var max_size: Int
    var created_count: Int
    var factory: PipelineFactory
    var available: List[ArcPointer[Pipeline]]

    fn __init__(out self, max_size: Int, factory: PipelineFactory):
        if max_size < 1:
            raise "max_size must be >= 1"
        self.max_size = max_size
        self.created_count = 0
        self.factory = factory
        self.available = List[ArcPointer[Pipeline]]()

    fn borrow(mut self) -> ArcPointer[Pipeline]:
        if len(self.available) > 0:
            return self.available.pop()

        if self.created_count < self.max_size:
            self.created_count = self.created_count + 1
            var new_pipeline = self.factory()
            var pipeline_ptr = ArcPointer(new_pipeline)
            return pipeline_ptr

        raise "Pipeline pool exhausted. Increase pool size."

    fn release(mut self, pipeline_ptr: ArcPointer[Pipeline]) -> None:
        self.available.append(pipeline_ptr)


struct PipelineProvider(Movable):
    var mode: PipelineProviderMode
    var shared_pipeline: ArcPointer[Pipeline]
    var pool: PipelinePool
    var factory: PipelineFactory

    fn __init__(out self, pipeline: Pipeline):
        self.mode = PipelineProviderMode.shared()
        self.shared_pipeline = ArcPointer(pipeline)
        self.pool = PipelinePool(1, default_pipeline_factory)
        self.factory = default_pipeline_factory

    fn __init__(out self, pool_max: Int, factory: PipelineFactory):
        self.mode = PipelineProviderMode.pooled()
        self.shared_pipeline = ArcPointer(Pipeline("pipeline", True))
        self.pool = PipelinePool(pool_max, factory)
        self.factory = default_pipeline_factory

    fn __init__(out self, factory: PipelineFactory):
        self.mode = PipelineProviderMode.per_run()
        self.shared_pipeline = ArcPointer(Pipeline("pipeline", True))
        self.pool = PipelinePool(1, default_pipeline_factory)
        self.factory = factory

    fn run(mut self, input_value: PythonObject) -> PipelineResult:
        if self.mode.value == PipelineProviderMode.shared().value:
            return self.shared_pipeline[].run(input_value)

        if self.mode.value == PipelineProviderMode.pooled().value:
            var borrowed_pipeline_ptr = self.pool.borrow()
            var result = borrowed_pipeline_ptr[].run(input_value)
            self.pool.release(borrowed_pipeline_ptr)
            return result

        var new_pipeline = self.factory()
        return new_pipeline.run(input_value)

