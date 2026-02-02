import unittest
from dataclasses import dataclass
from typing import List

from pipeline_services.core.pipeline import ActionControl, Pipeline


@dataclass
class RecordedUnaryAction:
    calls: List[str]
    name: str
    suffix: str = ""

    def __call__(self, ctx: str) -> str:
        self.calls.append(self.name)
        return ctx + self.suffix


@dataclass
class RecordedShortCircuitAction:
    calls: List[str]
    name: str
    suffix: str = ""

    def __call__(self, ctx: str, control: ActionControl) -> str:
        self.calls.append(self.name)
        control.short_circuit()
        return ctx + self.suffix


@dataclass
class RecordedFailingAction:
    calls: List[str]
    name: str
    exception_message: str = "boom"

    def __call__(self, ctx: str) -> str:
        self.calls.append(self.name)
        raise ValueError(self.exception_message)


class PipelineTests(unittest.TestCase):
    def test_short_circuit_stops_main_only(self) -> None:
        calls: List[str] = []

        pipeline = Pipeline("t", True)
        pipeline.add_pre_action(RecordedUnaryAction(calls, "pre", "pre|"))
        pipeline.add_action(RecordedUnaryAction(calls, "a1", "a1|"))
        pipeline.add_action(RecordedShortCircuitAction(calls, "a2", "a2|"))
        pipeline.add_action(RecordedUnaryAction(calls, "a3", "a3|"))
        pipeline.add_post_action(RecordedUnaryAction(calls, "post", "post|"))

        result = pipeline.run("")
        self.assertTrue(result.short_circuited)
        self.assertEqual(calls, ["pre", "a1", "a2", "post"])

    def test_short_circuit_on_exception_stops_main(self) -> None:
        calls: List[str] = []

        pipeline = Pipeline("t", True)
        pipeline.add_action(RecordedFailingAction(calls, "fail", "boom"))
        pipeline.add_action(RecordedUnaryAction(calls, "later", "later"))
        pipeline.add_post_action(RecordedUnaryAction(calls, "post", "post"))

        result = pipeline.run("start")
        self.assertTrue(result.short_circuited)
        self.assertEqual(len(result.errors), 1)
        self.assertEqual(calls, ["fail", "post"])

    def test_continue_on_exception_runs_remaining_actions(self) -> None:
        calls: List[str] = []

        pipeline = Pipeline("t", False)
        pipeline.add_action(RecordedFailingAction(calls, "fail", "boom"))
        pipeline.add_action(RecordedUnaryAction(calls, "later", "|later"))

        result = pipeline.run("start")
        self.assertFalse(result.short_circuited)
        self.assertEqual(len(result.errors), 1)
        self.assertEqual(result.context, "start|later")
        self.assertEqual(calls, ["fail", "later"])


if __name__ == "__main__":
    unittest.main()
