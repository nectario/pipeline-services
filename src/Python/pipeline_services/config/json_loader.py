from __future__ import annotations

import json
from dataclasses import dataclass
from pathlib import Path
from typing import Any, Dict, Iterable, Optional

from ..core.pipeline import Action, Pipeline
from ..core.registry import PipelineRegistry
from ..remote.http_step import RemoteDefaults, RemoteSpec, http_step


@dataclass
class PipelineJsonLoader:
    def load_str(self, json_text: str, registry: PipelineRegistry) -> Pipeline[Any]:
        return self.build_from_spec(json.loads(json_text), registry)

    def load_file(self, file_path: str, registry: PipelineRegistry) -> Pipeline[Any]:
        text_value = Path(file_path).read_text(encoding="utf-8")
        spec = json.loads(text_value)
        pipeline_name = str(spec.get("pipeline", Path(file_path).stem))

        if spec_contains_prompt_steps(spec):
            compiled_path = resolve_compiled_pipeline_path(
                file_path,
                pipeline_name,
                language_name="python",
            )
            if not Path(compiled_path).exists():
                raise ValueError(
                    "Pipeline contains $prompt Actions but compiled JSON was not found. "
                    "Run prompt codegen. Expected compiled Pipeline at: "
                    + compiled_path
                )
            return self.load_str(
                Path(compiled_path).read_text(encoding="utf-8"),
                registry,
            )

        return self.build_from_spec(spec, registry)

    def build_from_spec(
        self,
        spec: Dict[str, Any],
        registry: PipelineRegistry,
    ) -> Pipeline[Any]:
        if spec_contains_prompt_steps(spec):
            raise ValueError(
                "Pipeline contains $prompt Actions. Run prompt codegen and load "
                "the compiled JSON under pipelines/generated/python/."
            )

        pipeline_name = str(spec.get("pipeline", "pipeline"))
        pipeline_type = str(spec.get("type", "unary"))
        if pipeline_type != "unary":
            raise ValueError("Only 'unary' Pipelines are supported by this loader")

        short_circuit_on_exception = bool(
            spec.get(
                "shortCircuitOnException",
                spec.get("shortCircuit", True),
            )
        )
        pipeline: Pipeline[Any] = Pipeline(
            pipeline_name,
            short_circuit_on_exception,
        )

        remote_defaults = RemoteDefaults()
        if spec.get("remoteDefaults") is not None:
            remote_defaults = self.parse_remote_defaults(
                spec["remoteDefaults"],
                remote_defaults,
            )

        self.add_section(
            self._first_section(spec, "preActions", "pre"),
            "preActions",
            pipeline,
            registry,
            remote_defaults,
        )
        self.add_section(
            self._first_section(spec, "actions", "steps"),
            "actions",
            pipeline,
            registry,
            remote_defaults,
        )
        self.add_section(
            self._first_section(spec, "postActions", "post"),
            "postActions",
            pipeline,
            registry,
            remote_defaults,
        )
        return pipeline

    @staticmethod
    def _first_section(
        spec: Dict[str, Any],
        canonical_name: str,
        legacy_name: str,
    ) -> Iterable[Dict[str, Any]]:
        value = (
            spec[canonical_name]
            if spec.get(canonical_name) is not None
            else spec.get(legacy_name, [])
        )
        if not isinstance(value, list):
            raise ValueError(f"'{canonical_name}' must be an array")
        return value

    def add_section(
        self,
        nodes: Iterable[Dict[str, Any]],
        phase: str,
        pipeline: Pipeline[Any],
        registry: PipelineRegistry,
        remote_defaults: RemoteDefaults,
    ) -> None:
        for node in nodes:
            self.add_action(
                node,
                phase,
                pipeline,
                registry,
                remote_defaults,
            )

    def add_action(
        self,
        node: Dict[str, Any],
        phase: str,
        pipeline: Pipeline[Any],
        registry: PipelineRegistry,
        remote_defaults: RemoteDefaults,
    ) -> None:
        if node.get("$prompt") is not None:
            raise ValueError(
                "Runtime does not execute $prompt Actions. Run prompt codegen "
                "to produce compiled Pipeline JSON with $local references."
            )

        display_name = node.get("name", node.get("label"))
        name = str(display_name) if display_name is not None else None

        if node.get("$local") is not None:
            action = self.resolve_local(str(node["$local"]), registry)
        elif node.get("$remote") is not None:
            remote_spec = self.parse_remote_spec(
                node["$remote"],
                remote_defaults,
            )
            action = self.remote_action(remote_spec)
        else:
            raise ValueError("Unsupported Action: expected '$local' or '$remote'")

        if phase == "preActions":
            pipeline.add_pre_action(action, name=name)
        elif phase == "postActions":
            pipeline.add_post_action(action, name=name)
        else:
            pipeline.add_action(action, name=name)

    @staticmethod
    def resolve_local(
        local_ref: str,
        registry: PipelineRegistry,
    ) -> Action[Any]:
        if registry.has_unary(local_ref):
            return registry.get_unary(local_ref)
        if registry.has_action(local_ref):
            return registry.get_action(local_ref)

        if local_ref.startswith("prompt:"):
            raise ValueError(
                "Prompt-generated Action is missing from the registry: "
                + local_ref
                + ". Run prompt codegen and register generated Actions "
                "(pipeline_services.generated.register_generated_actions)."
            )
        raise ValueError("Unknown $local reference: " + local_ref)

    @staticmethod
    def remote_action(spec: RemoteSpec) -> Action[Any]:
        def execute(context: Any) -> Any:
            return http_step(spec, context)

        return execute

    def parse_remote_spec(
        self,
        remote_node: Any,
        remote_defaults: RemoteDefaults,
    ) -> RemoteSpec:
        if isinstance(remote_node, str):
            return remote_defaults.to_spec(remote_node)
        if not isinstance(remote_node, dict):
            raise ValueError("$remote must be a string or object")

        endpoint_value = remote_node.get("endpoint", remote_node.get("path"))
        if endpoint_value is None:
            raise ValueError("Missing required $remote field: endpoint|path")

        remote_spec = remote_defaults.to_spec(str(endpoint_value))
        timeout_value = remote_node.get(
            "timeoutMillis",
            remote_node.get("timeout_millis"),
        )
        if timeout_value is not None:
            remote_spec.timeout_millis = int(timeout_value)
        if remote_node.get("retries") is not None:
            remote_spec.retries = int(remote_node["retries"])
        if remote_node.get("method") is not None:
            remote_spec.method = str(remote_node["method"])

        headers_value = remote_node.get("headers")
        if headers_value is not None:
            merged_headers: Dict[str, str] = dict(remote_spec.headers or {})
            merged_headers.update(
                {str(key): str(value) for key, value in headers_value.items()}
            )
            remote_spec.headers = merged_headers
        return remote_spec

    def parse_remote_defaults(
        self,
        node: Dict[str, Any],
        base: RemoteDefaults,
    ) -> RemoteDefaults:
        defaults = base
        base_url_value = node.get("baseUrl", node.get("endpointBase"))
        if base_url_value is not None:
            defaults.base_url = str(base_url_value)

        timeout_value = node.get(
            "timeoutMillis",
            node.get("timeout_millis"),
        )
        if timeout_value is not None:
            defaults.timeout_millis = int(timeout_value)
        if node.get("retries") is not None:
            defaults.retries = int(node["retries"])
        if node.get("method") is not None:
            defaults.method = str(node["method"])
        if node.get("headers") is not None:
            defaults.headers = {
                str(key): str(value)
                for key, value in node["headers"].items()
            }
        return defaults


def spec_contains_prompt_steps(spec: Any) -> bool:
    if not isinstance(spec, dict):
        return False
    for section_name in (
        "preActions",
        "pre",
        "actions",
        "steps",
        "postActions",
        "post",
    ):
        nodes = spec.get(section_name)
        if not isinstance(nodes, list):
            continue
        for node in nodes:
            if isinstance(node, dict) and node.get("$prompt") is not None:
                return True
    return False


def resolve_compiled_pipeline_path(
    source_file_path: str,
    pipeline_name: str,
    language_name: str,
) -> str:
    source_path = Path(source_file_path).resolve()
    pipelines_root: Optional[Path] = None
    for parent_path in (source_path.parent, *source_path.parents):
        if parent_path.name == "pipelines":
            pipelines_root = parent_path
            break

    if pipelines_root is None:
        raise ValueError(
            "Pipeline contains $prompt Actions but the pipelines root directory "
            "could not be inferred from path: "
            + str(source_path)
            + " (expected the file under a 'pipelines' directory)."
        )

    return str(
        pipelines_root
        / "generated"
        / language_name
        / f"{pipeline_name}.json"
    )
