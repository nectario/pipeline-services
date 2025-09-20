from __future__ import annotations
from typing import Callable, TypeVar, Any

T = TypeVar('T')
I = TypeVar('I')
O = TypeVar('O')

def ignore_errors(step: Callable[[T], T]) -> Callable[[T], T]:
    def _wrapped(inp: T) -> T:
        try:
            return step(inp)
        except Exception:
            return inp
    return _wrapped

def with_fallback(step: Callable[[I], O], fallback: Callable[[Exception], O]) -> Callable[[I], O]:
    def _wrapped(inp: I) -> O:
        try:
            return step(inp)
        except Exception as e:
            return fallback(e)
    return _wrapped
