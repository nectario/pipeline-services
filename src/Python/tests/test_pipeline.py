import threading
import unittest
import warnings
from dataclasses import dataclass

from pipeline_services import (
    ActionControl,
    InvalidErrorHandlerError,
    Pipeline,
    PipelineObserver,
    PipelineProvider,
    PipelineProviderMode,
    PipelineRouter,
    short_circuit,
)


def append_and_stop(value: str) -> str:
    short_circuit()
    return value + "S"


def fail(value: str) -> str:
    del value
    raise ValueError("boom")


class RecordingObserver(PipelineObserver):
    def __init__(self) -> None:
        self.events: list[str] = []

    def on_pipeline_started(self, pipeline_name: str) -> None:
        self.events.append("pipeline:start:" + pipeline_name)

    def on_action_started(
        self,
        pipeline_name: str,
        phase: str,
        action_index: int,
        action_name: str,
    ) -> None:
        del pipeline_name
        self.events.append(f"action:start:{phase}:{action_index}:{action_name}")

    def on_action_completed(
        self,
        pipeline_name: str,
        phase: str,
        action_index: int,
        action_name: str,
        elapsed_nanos: int,
    ) -> None:
        del pipeline_name, elapsed_nanos
        self.events.append(f"action:completed:{phase}:{action_index}:{action_name}")

    def on_short_circuited(
        self,
        pipeline_name: str,
        phase: str,
        action_index: int,
        action_name: str,
    ) -> None:
        del pipeline_name
        self.events.append(f"pipeline:shortCircuit:{phase}:{action_index}:{action_name}")


class PipelineTests(unittest.TestCase):
    def test_run_returns_context_and_detailed_uses_same_semantics(self) -> None:
        pipeline = (
            Pipeline[str]("simple")
            .add_pre_action(lambda value: value + "P")
            .add_action(lambda value: value + "A")
            .add_post_action(lambda value: value + "Z")
        )

        self.assertEqual(pipeline.run("X"), "XPAZ")
        result = pipeline.run_detailed("X")
        self.assertEqual(result.context, "XPAZ")
        self.assertEqual(len(result.action_timings), 3)

    def test_short_circuit_rules_in_all_phases(self) -> None:
        main = (
            Pipeline[str]("main")
            .add_action(append_and_stop)
            .add_action(lambda value: value + "B")
            .add_post_action(lambda value: value + "P")
        )
        self.assertEqual(main.run("X"), "XSP")

        pre = (
            Pipeline[str]("pre")
            .add_pre_action(append_and_stop)
            .add_pre_action(lambda value: value + "2")
            .add_action(lambda value: value + "M")
            .add_post_action(lambda value: value + "P")
        )
        self.assertEqual(pre.run("X"), "XS2P")

        post = (
            Pipeline[str]("post")
            .add_action(lambda value: value + "M")
            .add_post_action(append_and_stop)
            .add_post_action(lambda value: value + "2")
        )
        result = post.run_detailed("X")
        self.assertEqual(result.context, "XMS2")
        self.assertTrue(result.short_circuited)

    def test_exception_policies_and_error_context(self) -> None:
        continuing = (
            Pipeline[str]("continue", False)
            .add_action(lambda value: value + "A")
            .add_action(fail)
            .add_action(lambda value: value + "B")
        )
        result = continuing.run_detailed("X")
        self.assertEqual(result.context, "XAB")
        self.assertFalse(result.short_circuited)
        self.assertEqual(result.errors[0].action_index, 1)
        self.assertEqual(str(result.errors[0].exception), "boom")

        stopping = (
            Pipeline[str]("stop", True)
            .add_action(lambda value: value + "A")
            .add_action(fail)
            .add_action(lambda value: value + "B")
            .add_post_action(lambda value: value + "P")
        )
        result = stopping.run_detailed("X")
        self.assertEqual(result.context, "XAP")
        self.assertTrue(result.short_circuited)

    def test_error_handler_updates_immutable_context(self) -> None:
        @dataclass(frozen=True)
        class Context:
            value: str
            error: str = ""

        pipeline = (
            Pipeline[Context]("immutable", False)
            .on_error(
                lambda context, error: Context(
                    context.value,
                    str(error.exception),
                )
            )
            .add_action(lambda context: (_ for _ in ()).throw(ValueError("bad")))
            .add_action(lambda context: Context(context.value + "A", context.error))
        )
        result = pipeline.run_detailed(Context("X"))
        self.assertEqual(result.context, Context("XA", "bad"))

    def test_invalid_error_handler_fails_after_all_post_actions(self) -> None:
        calls: list[str] = []
        pipeline = (
            Pipeline[str]("invalid_handler")
            .on_error(lambda context, error: None)
            .add_action(fail)
            .add_post_action(lambda value: (calls.append("post1"), value)[1])
            .add_post_action(lambda value: (calls.append("post2"), value)[1])
        )

        with self.assertRaises(InvalidErrorHandlerError):
            pipeline.run("X")
        self.assertEqual(calls, ["post1", "post2"])

    def test_nested_and_overlapping_runs_isolate_control_state(self) -> None:
        inner = (
            Pipeline[str]("inner")
            .add_action(append_and_stop)
            .add_action(lambda value: value + "I2")
            .add_post_action(lambda value: value + "IP")
        )
        outer = (
            Pipeline[str]("outer")
            .add_action(lambda value: value + ":" + inner.run("inner"))
            .add_action(lambda value: value + "O2")
        )
        self.assertEqual(outer.run("outer"), "outer:innerSIPO2")

        barrier = threading.Barrier(2)
        shared = (
            Pipeline[str]("shared")
            .add_action(
                lambda value: (
                    barrier.wait(),
                    short_circuit() if value == "stop" else None,
                    value + "A",
                )[2]
            )
            .add_action(lambda value: value + "B")
            .add_post_action(lambda value: value + "P")
        )
        provider = PipelineProvider.singleton(shared)
        outputs: dict[str, str] = {}

        first = threading.Thread(
            target=lambda: outputs.__setitem__("stop", provider.run("stop")),
        )
        second = threading.Thread(
            target=lambda: outputs.__setitem__("go", provider.run("go")),
        )
        first.start()
        second.start()
        first.join(2)
        second.join(2)

        self.assertEqual(outputs["stop"], "stopAP")
        self.assertEqual(outputs["go"], "goABP")

    def test_plan_freezes_and_short_circuit_outside_action_fails(self) -> None:
        pipeline = Pipeline[str]("frozen").add_action(lambda value: value + "A")
        self.assertEqual(pipeline.run("X"), "XA")
        self.assertTrue(pipeline.is_frozen())
        with self.assertRaises(RuntimeError):
            pipeline.add_action(lambda value: value + "B")
        with self.assertRaises(RuntimeError):
            short_circuit()

    def test_legacy_control_aware_action_adapts_to_same_runner(self) -> None:
        def legacy(value: str, control: ActionControl[str]) -> str:
            control.short_circuit()
            return value + "L"

        with warnings.catch_warnings():
            warnings.simplefilter("ignore", DeprecationWarning)
            pipeline = (
                Pipeline[str]("legacy")
                .add_action(legacy)
                .add_action(lambda value: value + "B")
            )
        self.assertEqual(pipeline.run("X"), "XL")

    def test_observer_order_and_control_isolation(self) -> None:
        observer = RecordingObserver()
        pipeline = (
            Pipeline[str]("observed")
            .observer(observer)
            .add_action(append_and_stop, name="stop")
            .add_action(lambda value: value + "B")
            .add_post_action(lambda value: value + "P", name="audit")
        )
        self.assertEqual(pipeline.run("X"), "XSP")
        self.assertIn(
            "pipeline:shortCircuit:actions:0:s0:stop",
            observer.events,
        )

        class ControllingObserver(PipelineObserver):
            def on_action_started(self, *args: object) -> None:
                del args
                short_circuit()

        isolated = (
            Pipeline[str]("observer_isolation")
            .observer(ControllingObserver())
            .add_action(lambda value: value + "A")
            .add_action(lambda value: value + "B")
        )
        self.assertEqual(isolated.run("X"), "XAB")


class ProviderAndRouterTests(unittest.TestCase):
    def test_provider_modes_and_round_robin(self) -> None:
        created = 0

        def factory() -> Pipeline[str]:
            nonlocal created
            created += 1
            instance_id = created
            return Pipeline[str](f"p{instance_id}").add_action(
                lambda value, instance_id=instance_id: value + str(instance_id)
            )

        pooled = PipelineProvider.pooled(factory, 3)
        self.assertEqual(created, 3)
        self.assertEqual(pooled.mode, PipelineProviderMode.POOLED)
        self.assertEqual(pooled.instance_count, 3)
        self.assertEqual(
            [pooled.run("") for _ in range(5)],
            ["1", "2", "3", "1", "2"],
        )

        singleton = PipelineProvider.singleton(
            Pipeline[str]("singleton").add_action(lambda value: value + "S")
        )
        self.assertIs(singleton.get_pipeline(), singleton.get_pipeline())

        per_event = PipelineProvider.new_instance_per_event(factory)
        self.assertIsNot(per_event.get_pipeline(), per_event.get_pipeline())

    def test_router_selects_without_changing_provider(self) -> None:
        trade = PipelineProvider.singleton(Pipeline[str]("trade"))
        quote = PipelineProvider.singleton(Pipeline[str]("quote"))
        router = PipelineRouter[str, str](
            lambda event: trade if event == "TRADE" else quote
        )
        self.assertIs(router.get_pipeline_provider("TRADE"), trade)
        self.assertIs(router.get_pipeline_provider("QUOTE"), quote)


if __name__ == "__main__":
    unittest.main()
