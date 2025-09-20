from __future__ import annotations
import importlib
import json
from typing import Any, Callable, Dict, Optional

from ..core.pipeline import Pipeline, Pipe
from ..core.jumps import jump_now, jump_after
from ..core.metrics import Metrics, NoopMetrics

class PipelineJsonLoader:
    def __init__(self, *, beans: Optional[Dict[str, Any]]=None, instance: Optional[Any]=None, metrics: Optional[Metrics]=None):
        self.beans = dict(beans or {})
        self.instance = instance
        self.metrics = metrics or NoopMetrics()

    def load_str(self, s: str):
        spec = json.loads(s)
        return self._build(spec)

    def load_file(self, path: str):
        with open(path, 'r', encoding='utf-8') as f:
            return self._build(json.load(f))

    def _build(self, root: Dict[str, Any]):
        name = root.get('pipeline', 'pipeline')
        typ  = root.get('type', 'unary')
        sc   = bool(root.get('shortCircuit', True))

        if typ == 'unary':
            p = Pipeline(name, short_circuit=sc, metrics=self.metrics)
            for sec in ('pre', 'steps', 'post'):
                for node in root.get(sec, []) or []:
                    fn, label = self._compile_step(node)
                    section = 'main' if sec == 'steps' else sec
                    p.step(fn, label=label, section=section)
            return p

        elif typ == 'typed':
            pipe = Pipe(name, short_circuit=sc, metrics=self.metrics)
            for sec in ('pre', 'steps', 'post'):
                for node in root.get(sec, []) or []:
                    fn, _ = self._compile_step(node, typed=True)
                    pipe.step(fn)
            return pipe
        else:
            raise ValueError(f"Unsupported pipeline type: {typ}")

    def _compile_step(self, node: Dict[str, Any], typed: bool=False):
        label = node.get('label')

        jw = node.get('jumpWhen')
        pred_fn = None
        jump_label = None
        jump_delay = 0
        if jw:
            jump_label = jw.get('label')
            jump_delay = int(jw.get('delayMillis', 0))
            pred_spec = jw.get('predicate')
            if pred_spec:
                pred_fn, _ = self._compile_step(pred_spec, typed=True)

        if '$local' in node:
            fqcn = node['$local']
            step_callable = self._resolve_local(fqcn)

        elif '$method' in node:
            step_callable = self._resolve_method(node['$method'])

        elif '$prompt' in node:
            step_callable = self._resolve_prompt(node['$prompt'])

        elif '$remote' in node:
            step_callable = self._resolve_remote(node['$remote'])

        else:
            raise ValueError(f"Unsupported step node: {node}")

        if jw and pred_fn and jump_label:
            def wrapped(x, _pred=pred_fn, _inner=step_callable, _label=jump_label, _delay=jump_delay):
                if bool(_pred(x)):
                    if _delay > 0: jump_after(_label, _delay)
                    else: jump_now(_label)
                return _inner(x)
            return wrapped, label
        else:
            return step_callable, label

    def _resolve_local(self, fqcn: str):
        module_name, cls_name = fqcn.rsplit('.', 1)
        mod = importlib.import_module(module_name)
        cls = getattr(mod, cls_name)
        inst = cls()
        if not hasattr(inst, 'apply'):
            raise TypeError(f"Class {fqcn} lacks an 'apply' method")
        return getattr(inst, 'apply')

    def _resolve_method(self, spec: Dict[str, Any]):
        ref = spec.get('ref')
        target = spec.get('target')
        if '#' in ref:
            mod_cls, meth = ref.split('#', 1)
            module_name, cls_name = mod_cls.rsplit('.', 1)
            mod = importlib.import_module(module_name)
            cls = getattr(mod, cls_name)
            if target:
                if target == '@this':
                    obj = self.instance
                    if obj is None: raise ValueError("target=@this but loader 'instance' is None")
                elif target.startswith('@'):
                    bean_id = target[1:]
                    if bean_id not in self.beans: raise ValueError(f"Unknown bean id: {bean_id}")
                    obj = self.beans[bean_id]
                else:
                    raise ValueError(f"Unsupported target: {target}")
                return getattr(obj, meth)
            else:
                return getattr(cls, meth)
        else:
            module_name, func_name = ref.rsplit(':', 1)
            mod = importlib.import_module(module_name)
            return getattr(mod, func_name)

    def _resolve_prompt(self, spec: Dict[str, Any]):
        adapter = self.beans.get('llm_adapter')
        if adapter is None:
            def _raise(_): raise NotImplementedError("No 'llm_adapter' bean provided for $prompt step")
            return _raise
        def _call(x, _adapter=adapter, _spec=spec):
            return _adapter(x, _spec)
        return _call

    def _resolve_remote(self, spec: Dict[str, Any]):
        from ..remote.http_step import http_step, RemoteSpec
        rs = RemoteSpec(
            endpoint = spec['endpoint'],
            timeout_millis = int(spec.get('timeoutMillis', 1000)),
            retries = int(spec.get('retries', 0)),
            headers = dict(spec.get('headers', {})),
            method = spec.get('method', 'POST').upper(),
        )
        to_json_id = spec.get('toJsonBean')
        from_json_id = spec.get('fromJsonBean')
        rs.to_json = self.beans[to_json_id] if to_json_id else (lambda i: json.dumps(i))
        rs.from_json = self.beans[from_json_id] if from_json_id else (lambda s: json.loads(s))
        return http_step(rs)
