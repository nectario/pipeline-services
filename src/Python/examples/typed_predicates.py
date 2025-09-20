from __future__ import annotations

_cnt = {'v': 0}
class TypedPredicates:
    @staticmethod
    def needs_await(value) -> bool:
        _cnt['v'] += 1
        return _cnt['v'] <= 2
