from std.python import Python, PythonObject
from std.collections import List
from std.memory import ArcPointer

from pipeline_services import (
    Action,
    Pipeline,
    PipelineError,
    PipelineJsonLoader,
    PipelineProvider,
    PipelineProviderMode,
    PipelineRegistry,
    PipelineRouter,
    RuntimePipeline,
    short_circuit,
)
from pipeline_services.generated import register_generated_actions
from pipeline_services.remote import RemoteSpec, remote_action


def _test_api() raises -> PythonObject:
    var module = Python.evaluate(
        """
import builtins
import types

if not hasattr(builtins, "_pipeline_services_mojo_test_api"):
    state = {"calls": [], "next_id": 0}

    def reset_calls():
        state["calls"].clear()

    def append_call(value):
        state["calls"].append(value)

    def calls_equal(expected):
        return state["calls"] == expected

    def next_id():
        state["next_id"] += 1
        return state["next_id"]

    def reset_ids():
        state["next_id"] = 0

    def make_suffix_action(suffix):
        return lambda value: str(value) + str(suffix)

    class Observer:
        def __init__(self):
            self.events = []

        def on_pipeline_started(self, name):
            self.events.append("start:" + name)

        def on_action_started(self, name, phase, index, action_name):
            self.events.append(f"{phase}:{index}:{action_name}")
            builtins._pipeline_services_execution_api.short_circuit()

        def on_action_completed(self, *args):
            pass

        def on_action_failed(self, *args):
            pass

        def on_short_circuited(self, *args):
            pass

        def on_pipeline_completed(self, *args):
            self.events.append("done")

    builtins._pipeline_services_mojo_test_api = types.SimpleNamespace(
        state=state,
        reset_calls=reset_calls,
        append_call=append_call,
        calls_equal=calls_equal,
        next_id=next_id,
        reset_ids=reset_ids,
        make_suffix_action=make_suffix_action,
        Observer=Observer,
    )

api = builtins._pipeline_services_mojo_test_api
""",
        file=True,
        name="_pipeline_services_mojo_test_loader",
    )
    return module.api


def _assert_string(actual: PythonObject, expected: String, message: String) raises:
    if String(actual) != expected:
        raise message + ": expected='" + expected + "' actual='" + String(actual) + "'"


def _assert_true(value: Bool, message: String) raises:
    if not value:
        raise message


def append_a(value: PythonObject) raises -> PythonObject:
    return PythonObject(String(value) + "A")


def append_b(value: PythonObject) raises -> PythonObject:
    return PythonObject(String(value) + "B")


def append_two(value: PythonObject) raises -> PythonObject:
    return PythonObject(String(value) + "2")


def append_post(value: PythonObject) raises -> PythonObject:
    return PythonObject(String(value) + "P")


def append_inner_post(value: PythonObject) raises -> PythonObject:
    return PythonObject(String(value) + "IP")


def append_and_stop(value: PythonObject) raises -> PythonObject:
    short_circuit()
    return PythonObject(String(value) + "S")


def fail_action(value: PythonObject) raises -> PythonObject:
    _ = value
    raise "boom"


def recovering_error_handler(
    value: PythonObject,
    error: PipelineError,
) raises -> PythonObject:
    _ = error
    return PythonObject(String(value) + "E")


def invalid_error_handler(
    value: PythonObject,
    error: PipelineError,
) raises -> PythonObject:
    _ = value
    _ = error
    raise "handler failed"


def record_post_one(value: PythonObject) raises -> PythonObject:
    _test_api().append_call("post1")
    return value


def record_post_two(value: PythonObject) raises -> PythonObject:
    _test_api().append_call("post2")
    return value


def run_inner_pipeline(value: PythonObject) raises -> PythonObject:
    var inner = Pipeline("inner", True)
    inner.add_action(append_and_stop)
    inner.add_action(append_two)
    inner.add_post_action(append_inner_post)
    return PythonObject(String(value) + ":" + String(inner.run(PythonObject("inner"))))


def provider_factory() raises -> Pipeline:
    var instance_id = Int(py=_test_api().next_id())
    var pipeline = Pipeline("provider" + String(instance_id), True)
    pipeline.add_action(Action(_test_api().make_suffix_action(instance_id)))
    return pipeline^


def route_event(event: PythonObject) raises -> Int:
    if String(event) == "TRADE":
        return 0
    return 1


def test_run_and_detailed() raises:
    var pipeline = Pipeline("simple", True)
    pipeline.add_pre_action(append_a)
    pipeline.add_action(append_b)
    pipeline.add_post_action(append_post)
    _assert_string(pipeline.run(PythonObject("X")), "XABP", "run result")
    var result = pipeline.run_detailed(PythonObject("X"))
    _assert_string(result.context, "XABP", "detailed result")
    _assert_true(len(result.action_timings) == 3, "detailed timing count")


def test_short_circuit_rules() raises:
    var main = Pipeline("main", True)
    main.add_action(append_and_stop)
    main.add_action(append_b)
    main.add_post_action(append_post)
    _assert_string(main.run(PythonObject("X")), "XSP", "main short circuit")

    var pre = Pipeline("pre", True)
    pre.add_pre_action(append_and_stop)
    pre.add_pre_action(append_two)
    pre.add_action(append_b)
    pre.add_post_action(append_post)
    _assert_string(pre.run(PythonObject("X")), "XS2P", "pre short circuit")

    var post = Pipeline("post", True)
    post.add_action(append_a)
    post.add_post_action(append_and_stop)
    post.add_post_action(append_two)
    var result = post.run_detailed(PythonObject("X"))
    _assert_string(result.context, "XAS2", "post short circuit")
    _assert_true(result.short_circuited, "post short circuit diagnostic")


def test_error_policies_and_cleanup() raises:
    var continuing = Pipeline("continue", False)
    continuing.on_error(recovering_error_handler)
    continuing.add_action(append_a)
    continuing.add_action(fail_action)
    continuing.add_action(append_b)
    var continued = continuing.run_detailed(PythonObject("X"))
    _assert_string(continued.context, "XAEB", "continue on exception")
    _assert_true(len(continued.errors) == 1, "captured error")

    var stopping = Pipeline("stop", True)
    stopping.add_action(append_a)
    stopping.add_action(fail_action)
    stopping.add_action(append_b)
    stopping.add_post_action(append_post)
    var stopped = stopping.run_detailed(PythonObject("X"))
    _assert_string(stopped.context, "XAP", "stop on exception")
    _assert_true(stopped.short_circuited, "exception short circuit")

    _test_api().reset_calls()
    var invalid = Pipeline("invalid", True)
    invalid.on_error(invalid_error_handler)
    invalid.add_action(fail_action)
    invalid.add_post_action(record_post_one)
    invalid.add_post_action(record_post_two)
    var failed_clearly = False
    try:
        _ = invalid.run(PythonObject("X"))
    except:
        failed_clearly = True
    _assert_true(failed_clearly, "invalid handler must fail")
    _assert_true(
        Bool(py=_test_api().calls_equal(["post1", "post2"])),
        "all post Actions must run after invalid handler",
    )


def test_nested_freeze_and_observer() raises:
    var outer = Pipeline("outer", True)
    outer.add_action(run_inner_pipeline)
    outer.add_action(append_two)
    _assert_string(
        outer.run(PythonObject("outer")),
        "outer:innerSIP2",
        "nested Pipeline scope",
    )

    var mutation_failed = False
    try:
        outer.add_action(append_b)
    except:
        mutation_failed = True
    _assert_true(mutation_failed, "Pipeline must freeze after run")

    var outside_failed = False
    try:
        short_circuit()
    except:
        outside_failed = True
    _assert_true(outside_failed, "short_circuit outside run")

    var observer = _test_api().Observer()
    var observed = Pipeline("observed", True)
    observed.observer(observer)
    observed.add_action(append_a)
    observed.add_action(append_b)
    _assert_string(
        observed.run(PythonObject("X")),
        "XAB",
        "observer cannot alter semantics",
    )
    _assert_true(len(observer.events) == 4, "observer event count")


def test_provider_and_router() raises:
    _test_api().reset_ids()
    var pooled = PipelineProvider.pooled(provider_factory, 3)
    _assert_true(
        pooled.mode() == PipelineProviderMode.pooled(),
        "pooled mode",
    )
    _assert_true(pooled.instance_count() == 3, "eager pool size")
    _assert_string(pooled.run(PythonObject("")), "1", "pooled first")
    _assert_string(pooled.run(PythonObject("")), "2", "pooled second")
    _assert_string(pooled.run(PythonObject("")), "3", "pooled third")
    _assert_string(pooled.run(PythonObject("")), "1", "pooled wraps")

    var singleton_pipeline = Pipeline("singleton", True)
    var singleton = PipelineProvider.singleton(singleton_pipeline^)
    var singleton_one = singleton.get_pipeline()
    var singleton_two = singleton.get_pipeline()
    _assert_true(singleton_one is singleton_two, "singleton identity")

    var per_event = PipelineProvider.new_instance_per_event(provider_factory)
    var event_one = per_event.get_pipeline()
    var event_two = per_event.get_pipeline()
    _assert_true(not (event_one is event_two), "per-event identity")

    var trade_pipeline = Pipeline("trade", True)
    trade_pipeline.add_action(append_a)
    var quote_pipeline = Pipeline("quote", True)
    quote_pipeline.add_action(append_b)
    var providers = List[ArcPointer[PipelineProvider]]()
    providers.append(
        ArcPointer(PipelineProvider.singleton(trade_pipeline^))
    )
    providers.append(
        ArcPointer(PipelineProvider.singleton(quote_pipeline^))
    )
    var router = PipelineRouter(providers, route_event)
    _assert_string(
        router.run(PythonObject("TRADE"), PythonObject("X")),
        "XA",
        "trade route",
    )
    _assert_string(
        router.run(PythonObject("QUOTE"), PythonObject("X")),
        "XB",
        "quote route",
    )


def test_json_remote_generated_and_runtime() raises:
    var registry = PipelineRegistry()
    registry.register_action("appendA", append_a)
    var json_text = """
    {
      "pipeline": "json",
      "preActions": [{"$local": "appendA"}],
      "actions": [{"$local": "appendA"}],
      "postActions": [{"$local": "appendA"}]
    }
    """
    var json_pipeline = PipelineJsonLoader().load_str(json_text, registry)
    _assert_string(
        json_pipeline.run(PythonObject("X")),
        "XAAA",
        "canonical JSON arrays",
    )

    var spec = RemoteSpec("data:text/plain,remote")
    spec.method = "GET"
    var remote_pipeline = Pipeline("remote", True)
    remote_pipeline.add_action(remote_action(spec))
    _assert_string(
        remote_pipeline.run(PythonObject("ignored")),
        "remote",
        "ordinary remote Action",
    )

    register_generated_actions(registry)
    var generated_pipeline = Pipeline("generated", True)
    generated_pipeline.add_action(
        registry.get_action("prompt:normalize_name")
    )
    _assert_string(
        generated_pipeline.run(PythonObject("  john   SMITH ")),
        "John Smith",
        "generated Action",
    )

    var runtime = RuntimePipeline("runtime", True, PythonObject("X"))
    _ = runtime.add_action(append_a)
    _assert_string(runtime.value(), "XA", "RuntimePipeline immediate run")
    var frozen = runtime.freeze()
    _assert_string(
        frozen.run(PythonObject("Y")),
        "YA",
        "RuntimePipeline frozen runner",
    )


def main() raises:
    test_run_and_detailed()
    test_short_circuit_rules()
    test_error_policies_and_cleanup()
    test_nested_freeze_and_observer()
    test_provider_and_router()
    test_json_remote_generated_and_runtime()
    print("Mojo vNext conformance: PASS")
