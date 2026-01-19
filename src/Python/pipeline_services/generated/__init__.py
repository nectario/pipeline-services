from __future__ import annotations

from pipeline_services.core.registry import PipelineRegistry

from .normalize_name_action import normalize_name_action

def register_generated_actions(registry: PipelineRegistry) -> None:
    if registry is None:
        raise ValueError("registry is required")
    registry.register_unary("prompt:normalize_name", normalize_name_action)
