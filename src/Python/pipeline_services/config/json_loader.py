from __future__ import annotations

import json
from dataclasses import dataclass
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
        with open(file_path, "r", encoding="utf-8") as file_object:
            text_value = file_object.read()
        return self.load_str(text_value, registry)

    def build_from_spec(self, spec: Dict[str, Any], registry: PipelineRegistry) -> Pipeline:
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

