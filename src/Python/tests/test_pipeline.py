import unittest

from pipeline_services.core.pipeline import Pipeline, StepControl


class PipelineTests(unittest.TestCase):
    def test_short_circuit_stops_main_only(self) -> None:
        calls = []

        def pre_action(ctx: str) -> str:
            calls.append("pre")
            return ctx + "pre|"

        def action_one(ctx: str) -> str:
            calls.append("a1")
            return ctx + "a1|"

        def action_two(ctx: str, control: StepControl) -> str:
            calls.append("a2")
            control.short_circuit()
            return ctx + "a2|"

        def action_three(ctx: str) -> str:
            calls.append("a3")
            return ctx + "a3|"

        def post_action(ctx: str) -> str:
            calls.append("post")
            return ctx + "post|"

        pipeline = Pipeline("t", True)
        pipeline.add_pre_action(pre_action)
        pipeline.add_action(action_one)
        pipeline.add_action(action_two)
        pipeline.add_action(action_three)
        pipeline.add_post_action(post_action)

        result = pipeline.execute("")
        self.assertTrue(result.short_circuited)
        self.assertEqual(calls, ["pre", "a1", "a2", "post"])

    def test_short_circuit_on_exception_stops_main(self) -> None:
        calls = []

        def failing_action(ctx: str) -> str:
            calls.append("fail")
            raise ValueError("boom")

        def later_action(ctx: str) -> str:
            calls.append("later")
            return ctx + "later"

        def post_action(ctx: str) -> str:
            calls.append("post")
            return ctx + "post"

        pipeline = Pipeline("t", True)
        pipeline.add_action(failing_action)
        pipeline.add_action(later_action)
        pipeline.add_post_action(post_action)

        result = pipeline.execute("start")
        self.assertTrue(result.short_circuited)
        self.assertEqual(len(result.errors), 1)
        self.assertEqual(calls, ["fail", "post"])

    def test_continue_on_exception_runs_remaining_actions(self) -> None:
        calls = []

        def failing_action(ctx: str) -> str:
            calls.append("fail")
            raise ValueError("boom")

        def later_action(ctx: str) -> str:
            calls.append("later")
            return ctx + "|later"

        pipeline = Pipeline("t", False)
        pipeline.add_action(failing_action)
        pipeline.add_action(later_action)

        result = pipeline.execute("start")
        self.assertFalse(result.short_circuited)
        self.assertEqual(len(result.errors), 1)
        self.assertEqual(result.context, "start|later")
        self.assertEqual(calls, ["fail", "later"])


if __name__ == "__main__":
    unittest.main()

