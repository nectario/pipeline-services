from __future__ import annotations
import random, time
from dataclasses import dataclass
from ..core.pipeline import Pipeline
from ..disruptor.engine import DisruptorEngine

@dataclass
class Event:
    symbol: str
    price: float
    ts_ns: int

def enrich(ev: Event) -> Event:
    return ev

def alert(ev: Event) -> str:
    return f"ALERT {ev.symbol} {ev.price:.2f}"

def main():
    p = Pipeline("alerts").add_steps(enrich, alert)
    engine = DisruptorEngine("alerts", p.run)
    try:
        px = 100.0
        for _ in range(300):
            px += (random.random()-0.5)*2.0
            px = max(1.0, px)
            engine.publish(Event("AAPL", px, time.time_ns()))
            time.sleep(0.005)
    finally:
        engine.shutdown()

if __name__ == '__main__':
    main()
