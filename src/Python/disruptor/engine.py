from __future__ import annotations
import threading
import queue
from typing import Callable, TypeVar, Generic, Optional

T = TypeVar('T')

class DisruptorEngine(Generic[T]):
    def __init__(self, name: str, pipeline: Callable[[T], T], capacity: int = 8192):
        self.name = name
        self.pipeline = pipeline
        self.q: queue.Queue[T] = queue.Queue(maxsize=capacity)
        self._running = threading.Event()
        self._running.set()
        self._worker = threading.Thread(target=self._run, name=f"{name}-worker", daemon=True)
        self._worker.start()

    def _run(self):
        while self._running.is_set():
            try:
                item = self.q.get(timeout=0.1)
            except queue.Empty:
                continue
            try:
                self.pipeline(item)
            finally:
                self.q.task_done()

    def publish(self, payload: T) -> None:
        if not self._running.is_set():
            raise RuntimeError("engine stopped")
        # Backpressure policy: block until space is available
        self.q.put(payload, block=True)

    def shutdown(self) -> None:
        self._running.clear()

    def close(self) -> None:
        self.shutdown()
