from __future__ import annotations

from pipeline_services import Pipeline


def main() -> None:
    pipeline = Pipeline("example00_import", True)
    result = pipeline.run("ok")
    print(result.context)


if __name__ == "__main__":
    main()
