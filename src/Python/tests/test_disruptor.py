import time
from pipeline_services.core.pipeline import Pipeline
from pipeline_services.disruptor.engine import DisruptorEngine

def test_engine_runs():
    seen = []
    p = Pipeline("t").add_steps(lambda x: seen.append(x) or x)
    e = DisruptorEngine("e", p.run, capacity=8)
    try:
        for i in range(5):
            e.publish(i)
        time.sleep(0.2)
        assert len(seen) >= 5
    finally:
        e.shutdown()
