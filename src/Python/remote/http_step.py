from __future__ import annotations
import json
import time
from typing import Callable, Dict, Any
from urllib import request, error, parse

class RemoteSpec:
    def __init__(self, *, endpoint: str, timeout_millis: int = 1000, retries: int = 0,
                 headers: Dict[str, str] | None = None, method: str = 'POST'):
        self.endpoint = endpoint
        self.timeout_millis = timeout_millis
        self.retries = retries
        self.headers = headers or {}
        self.method = method.upper()
        self.to_json: Callable[[Any], str] = lambda i: json.dumps(i)
        self.from_json: Callable[[str], Any] = lambda s: json.loads(s)

def http_step(spec: RemoteSpec):
    def _call(x):
        data_str = spec.to_json(x)
        last_err: Exception | None = None
        for attempt in range(spec.retries + 1):
            try:
                if spec.method == 'GET':
                    # Build a query string from dict/JSON when possible
                    query = ''
                    try:
                        obj = json.loads(data_str)
                        if isinstance(obj, dict):
                            query = parse.urlencode(obj, doseq=True)
                        else:
                            query = str(obj)
                    except Exception:
                        # Not JSON; treat string as already-encoded query fragment
                        query = str(data_str) if data_str else ''
                    url = spec.endpoint
                    if query:
                        url = url + ('&' if ('?' in url) else '?') + query
                    req = request.Request(url, method='GET', headers=spec.headers)
                    with request.urlopen(req, timeout=spec.timeout_millis / 1000.0) as resp:
                        body = resp.read().decode('utf-8')
                        return spec.from_json(body)
                else:
                    req = request.Request(spec.endpoint, data=data_str.encode('utf-8'), method='POST', headers={
                        'Content-Type': 'application/json', **spec.headers
                    })
                    with request.urlopen(req, timeout=spec.timeout_millis / 1000.0) as resp:
                        body = resp.read().decode('utf-8')
                        return spec.from_json(body)
            except Exception as e:
                last_err = e
                if attempt < spec.retries:
                    time.sleep(0.05 * (attempt + 1))
                else:
                    raise
        if last_err:
            raise last_err
    return _call
