from __future__ import annotations
from typing import Dict, Optional, Callable, TypeVar, Generic

T = TypeVar('T')

class PipelineRegistry(Generic[T]):
    def __init__(self):
        self._map: Dict[str, Callable[[T], T]] = {}

    def register(self, key: str, pipeline: Callable[[T], T]) -> None:
        self._map[key] = pipeline

    def lookup(self, key: str) -> Optional[Callable[[T], T]]:
        return self._map.get(key)

    def as_map(self):
        return dict(self._map)

    def size(self) -> int:
        return len(self._map)
