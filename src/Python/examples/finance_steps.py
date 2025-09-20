from __future__ import annotations
from dataclasses import dataclass

@dataclass(frozen=True)
class Tick:
    symbol: str
    price: float

@dataclass(frozen=True)
class Features:
    r1: float
    vol: float

@dataclass(frozen=True)
class Score:
    value: float

class OrderResponse: pass

@dataclass(frozen=True)
class Accept(OrderResponse):
    symbol: str
    qty: int
    price: float

@dataclass(frozen=True)
class Reject(OrderResponse):
    reason: str

class FinanceSteps:
    @staticmethod
    def compute_features(t: Tick) -> Features:
        r1 = 0.0
        vol = abs(t.price) * 0.01
        return Features(r1, vol)

    @staticmethod
    def score(f: Features) -> Score:
        return Score(max(0.0, 1.0 - f.vol))

    @staticmethod
    def decide(s: Score) -> OrderResponse:
        return Accept("AAPL", 10, 101.25) if s.value >= 0.5 else Reject("LowScore")
