from __future__ import annotations

import json
from dataclasses import dataclass
from pathlib import Path
from typing import Any, Dict, Optional

from ..core.pipeline import Pipeline
from ..core.registry import PipelineRegistry
from ..remote.http_step import RemoteDefaults, RemoteSpec


@dataclass
class PipelineJsonLoader:
    def load_str(self, json_text: str, registry: PipelineRegistry) -> Pipeline:
        spec = json.loads(json_text)
        return self.build_from_spec(spec, registry)

    def load_file(self, file_path: str, registry: PipelineRegistry) -> Pipeline:
        text_value = Path(file_path).read_text(encoding="utf-8")
        spec = json.loads(text_value)
        pipeline_name = str(spec.get("pipeline", Path(file_path).stem))

        if spec_contains_prompt_steps(spec):
            compiled_path = resolve_compiled_pipeline_path(file_path, pipeline_name, language_name="python")
            if not Path(compiled_path).exists():
                raise ValueError(
                    "Pipeline contains $prompt steps but compiled JSON was not found. "
                    "Run prompt codegen. Expected compiled pipeline at: "
                    + compiled_path
                )
            compiled_text = Path(compiled_path).read_text(encoding="utf-8")
            return self.load_str(compiled_text, registry)

        return self.build_from_spec(spec, registry)

    def build_from_spec(self, spec: Dict[str, Any], registry: PipelineRegistry) -> Pipeline:
        if spec_contains_prompt_steps(spec):
            raise ValueError(
                "Pipeline contains $prompt steps. Run prompt codegen and load the compiled JSON "
                "under pipelines/generated/python/."
            )

        pipeline_name = str(spec.get("pipeline", "pipeline"))
        pipeline_type = str(spec.get("type", "unary"))

        if pipeline_type != "unary":
            raise ValueError("Only 'unary' pipelines are supported by this loader")

        short_circuit_on_exception = True
        short_circuit_on_exception_value = spec.get("shortCircuitOnException")
        if short_circuit_on_exception_value is not None:
            short_circuit_on_exception = bool(short_circuit_on_exception_value)
        else:
            short_circuit_value = spec.get("shortCircuit")
            if short_circuit_value is not None:
                short_circuit_on_exception = bool(short_circuit_value)

        pipeline = Pipeline(pipeline_name, short_circuit_on_exception)

        remote_defaults = RemoteDefaults()
        remote_defaults_node = spec.get("remoteDefaults")
        if remote_defaults_node is not None:
            remote_defaults = self.parse_remote_defaults(remote_defaults_node, remote_defaults)

        self.add_section(spec, "pre", pipeline, registry, remote_defaults)
        if spec.get("actions") is not None:
            self.add_section(spec, "actions", pipeline, registry, remote_defaults)
        else:
            self.add_section(spec, "steps", pipeline, registry, remote_defaults)
        self.add_section(spec, "post", pipeline, registry, remote_defaults)

        return pipeline

    def add_section(
        self,
        spec: Dict[str, Any],
        section_name: str,
        pipeline: Pipeline,
        registry: PipelineRegistry,
        remote_defaults: RemoteDefaults,
    ) -> None:
        nodes = spec.get(section_name)
        if nodes is None:
            return

        for node in nodes:
            self.add_step(node, section_name, pipeline, registry, remote_defaults)

    def add_step(
        self,
        node: Dict[str, Any],
        section_name: str,
        pipeline: Pipeline,
        registry: PipelineRegistry,
        remote_defaults: RemoteDefaults,
    ) -> None:
        if node.get("$prompt") is not None:
            raise ValueError(
                "Runtime does not execute $prompt steps. Run prompt codegen to produce a compiled pipeline JSON "
                "with $local references."
            )

        display_name = ""
        name_value = node.get("name")
        if name_value is not None:
            display_name = str(name_value)
        else:
            label_value = node.get("label")
            if label_value is not None:
                display_name = str(label_value)

        local_ref_value = node.get("$local")
        if local_ref_value is not None:
            local_ref = str(local_ref_value)
            self.add_local(local_ref, display_name, section_name, pipeline, registry)
            return

        remote_node = node.get("$remote")
        if remote_node is not None:
            remote_spec = self.parse_remote_spec(remote_node, remote_defaults)
            self.add_remote(remote_spec, display_name, section_name, pipeline)
            return

        raise ValueError("Unsupported action: expected '$local' or '$remote'")

    def add_local(
        self,
        local_ref: str,
        display_name: str,
        section_name: str,
        pipeline: Pipeline,
        registry: PipelineRegistry,
    ) -> None:
        if registry.has_unary(local_ref):
            unary_action = registry.get_unary(local_ref)
            if section_name == "pre":
                pipeline.add_pre_action_named(display_name, unary_action)
            elif section_name == "post":
                pipeline.add_post_action_named(display_name, unary_action)
            else:
                pipeline.add_action_named(display_name, unary_action)
            return

        if registry.has_action(local_ref):
            step_action = registry.get_action(local_ref)
            if section_name == "pre":
                pipeline.add_pre_action_named(display_name, step_action)
            elif section_name == "post":
                pipeline.add_post_action_named(display_name, step_action)
            else:
                pipeline.add_action_named(display_name, step_action)
            return

        if local_ref.startswith("prompt:"):
            raise ValueError(
                "Prompt-generated action is missing from the registry: "
                + local_ref
                + ". Run prompt codegen and register generated actions (pipeline_services.generated.register_generated_actions)."
            )

        raise ValueError("Unknown $local reference: " + local_ref)

    def parse_remote_spec(self, remote_node: Any, remote_defaults: RemoteDefaults) -> RemoteSpec:
        if isinstance(remote_node, str):
            return remote_defaults.to_spec(remote_node)

        endpoint_value = remote_node.get("endpoint")
        if endpoint_value is None:
            endpoint_value = remote_node.get("path")
        if endpoint_value is None:
            raise ValueError("Missing required $remote field: endpoint|path")

        remote_spec = remote_defaults.to_spec(str(endpoint_value))

        timeout_value = remote_node.get("timeoutMillis")
        if timeout_value is None:
            timeout_value = remote_node.get("timeout_millis")
        if timeout_value is not None:
            remote_spec.timeout_millis = int(timeout_value)

        retries_value = remote_node.get("retries")
        if retries_value is not None:
            remote_spec.retries = int(retries_value)

        method_value = remote_node.get("method")
        if method_value is not None:
            remote_spec.method = str(method_value)

        headers_value = remote_node.get("headers")
        if headers_value is not None:
            merged_headers: Dict[str, str] = {}
            base_headers = remote_spec.headers
            if base_headers is not None:
                merged_headers.update(base_headers)
            merged_headers.update(headers_value)
            remote_spec.headers = merged_headers

        return remote_spec

    def parse_remote_defaults(self, node: Dict[str, Any], base: RemoteDefaults) -> RemoteDefaults:
        defaults = base
        base_url_value = node.get("baseUrl")
        if base_url_value is None:
            base_url_value = node.get("endpointBase")
        if base_url_value is not None:
            defaults.base_url = str(base_url_value)

        timeout_value = node.get("timeoutMillis")
        if timeout_value is None:
            timeout_value = node.get("timeout_millis")
        if timeout_value is not None:
            defaults.timeout_millis = int(timeout_value)

        retries_value = node.get("retries")
        if retries_value is not None:
            defaults.retries = int(retries_value)

        method_value = node.get("method")
        if method_value is not None:
            defaults.method = str(method_value)

        headers_value = node.get("headers")
        if headers_value is not None:
            defaults.headers = headers_value

        return defaults

    def add_remote(self, spec: RemoteSpec, display_name: str, section_name: str, pipeline: Pipeline) -> None:
        if section_name == "pre":
            pipeline.add_pre_action_named(display_name, spec)
        elif section_name == "post":
            pipeline.add_post_action_named(display_name, spec)
        else:
            pipeline.add_action_named(display_name, spec)


def spec_contains_prompt_steps(spec: Any) -> bool:
    if not isinstance(spec, dict):
        return False
    for section_name in ("pre", "actions", "steps", "post"):
        nodes = spec.get(section_name)
        if not isinstance(nodes, list):
            continue
        for node in nodes:
            if isinstance(node, dict) and node.get("$prompt") is not None:
                return True
    return False


def resolve_compiled_pipeline_path(source_file_path: str, pipeline_name: str, language_name: str) -> str:
    source_path = Path(source_file_path).resolve()
    pipelines_root: Optional[Path] = None
    for parent_path in (source_path.parent, *source_path.parents):
        if parent_path.name == "pipelines":
            pipelines_root = parent_path
            break

    if pipelines_root is None:
        raise ValueError(
            "Pipeline contains $prompt steps but the pipelines root directory could not be inferred from path: "
            + str(source_path)
            + " (expected the file to be under a 'pipelines' directory)."
        )

    compiled_path = pipelines_root / "generated" / language_name / f"{pipeline_name}.json"
    return str(compiled_path)
