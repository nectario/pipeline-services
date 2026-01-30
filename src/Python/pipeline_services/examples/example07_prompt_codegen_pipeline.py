from __future__ import annotations

from pathlib import Path

from pipeline_services.config.json_loader import PipelineJsonLoader
from pipeline_services.core.registry import PipelineRegistry
from pipeline_services.examples.text_steps import strip
from pipeline_services.generated import register_generated_actions


def find_pipeline_file(pipeline_file_name: str) -> Path:
    current_path = Path.cwd().resolve()
    while True:
        candidate_file = current_path / "pipelines" / pipeline_file_name
        if candidate_file.exists():
            return candidate_file
        parent_path = current_path.parent
        if parent_path == current_path:
            break
        current_path = parent_path
    raise FileNotFoundError("Could not locate pipelines directory from current working directory")


def main() -> None:
    pipeline_file = find_pipeline_file("normalize_name.json")

    registry = PipelineRegistry()
    registry.register_unary("strip", strip)
    register_generated_actions(registry)

    loader = PipelineJsonLoader()
    pipeline = loader.load_file(str(pipeline_file), registry)
    result = pipeline.run("  john   SMITH ")
    print("output=" + str(result.context))


if __name__ == "__main__":
    main()
