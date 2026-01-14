from __future__ import annotations

from pipeline_services import Pipeline


def main() -> None:
    pipeline = Pipeline("example00_import", True)
    output_value = pipeline.run("ok")
    print(output_value)


if __name__ == "__main__":
    main()

