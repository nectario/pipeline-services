from time import perf_counter_ns

trait RunScope:
    fn on_step_start(self, index: Int, label: String) -> None: ...
    fn on_step_end(self, index: Int, label: String, elapsed_nanos: Int, success: Bool) -> None: ...
    fn on_step_error(self, index: Int, label: String, message: String) -> None: ...
    fn on_jump(self, from_label: String, to_label: String, delay_ms: Int) -> None: ...
    fn on_pipeline_end(self, success: Bool, elapsed_nanos: Int, error_message: String) -> None: ...

trait Metrics:
    fn on_pipeline_start(self, pipeline_name: String, run_id: String, start_label: String) -> RunScope: ...

struct NoopRunScope(RunScope):
    fn on_step_start(self, index: Int, label: String) -> None: pass
    fn on_step_end(self, index: Int, label: String, elapsed_nanos: Int, success: Bool) -> None: pass
    fn on_step_error(self, index: Int, label: String, message: String) -> None: pass
    fn on_jump(self, from_label: String, to_label: String, delay_ms: Int) -> None: pass
    fn on_pipeline_end(self, success: Bool, elapsed_nanos: Int, error_message: String) -> None: pass

struct NoopMetrics(Metrics):
    fn on_pipeline_start(self, pipeline_name: String, run_id: String, start_label: String) -> RunScope:
        var scope = NoopRunScope()
        return scope

struct LoggingRunScope(RunScope):
    var pipeline_name: String
    var run_id: String
    var verbosity_level: Int

    fn __init__(out self, pipeline_name: String, run_id: String, verbosity_level: Int):
        self.pipeline_name = pipeline_name
        self.run_id = run_id
        self.verbosity_level = verbosity_level

    fn on_step_start(self, index: Int, label: String) -> None:
        if self.verbosity_level >= 1:
            print("step.start name=", self.pipeline_name, " runId=", self.run_id, " index=", index, " label=", label)

    fn on_step_end(self, index: Int, label: String, elapsed_nanos: Int, success: Bool) -> None:
        if self.verbosity_level >= 1:
            var elapsed_ms: Float64 = Float64(elapsed_nanos) / 1_000_000.0
            print("step.end   name=", self.pipeline_name, " runId=", self.run_id, " index=", index, " label=", label, " durMs=", elapsed_ms, " success=", success)

    fn on_step_error(self, index: Int, label: String, message: String) -> None:
        print("step.error name=", self.pipeline_name, " runId=", self.run_id, " index=", index, " label=", label, " message=", message)

    fn on_jump(self, from_label: String, to_label: String, delay_ms: Int) -> None:
        if self.verbosity_level >= 1:
            print("step.jump  name=", self.pipeline_name, " runId=", self.run_id, " from=", from_label, " to=", to_label, " delayMs=", delay_ms)

    fn on_pipeline_end(self, success: Bool, elapsed_nanos: Int, error_message: String) -> None:
        if self.verbosity_level >= 1:
            var elapsed_ms: Float64 = Float64(elapsed_nanos) / 1_000_000.0
            print("pipeline.end name=", self.pipeline_name, " runId=", self.run_id, " durMs=", elapsed_ms, " success=", success, " error=", error_message)

struct LoggingMetrics(Metrics):
    var verbosity_level: Int

    fn __init__(out self, verbosity_level: Int = 1):
        self.verbosity_level = verbosity_level

    fn on_pipeline_start(self, pipeline_name: String, run_id: String, start_label: String) -> RunScope:
        if self.verbosity_level >= 1:
            print("pipeline.start name=", pipeline_name, " runId=", run_id, " startLabel=", start_label)
        var scope = LoggingRunScope(pipeline_name, run_id, self.verbosity_level)
        return scope
