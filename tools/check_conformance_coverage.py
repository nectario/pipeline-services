#!/usr/bin/env python3
"""Validate vNext conformance declarations and native test wiring."""

from __future__ import annotations

import json
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
CONTRACT_PATH = ROOT / "spec/conformance/vnext/pipeline-core.yaml"
COVERAGE_PATH = ROOT / "spec/conformance/vnext/port-coverage.json"
REQUIRED_PORTS = {
    "java",
    "python",
    "typescript",
    "rust",
    "go",
    "csharp",
    "cpp",
    "mojo",
}
OPTIONAL_SCENARIOS = {
    "direct_and_builder_equivalence",
    "subclass_equivalence",
}


def fail(message: str) -> None:
    print(f"conformance coverage error: {message}", file=sys.stderr)
    raise SystemExit(1)


def scenario_ids() -> list[str]:
    text = CONTRACT_PATH.read_text(encoding="utf-8")
    identifiers = re.findall(r"^\s*- id:\s*([a-z0-9_]+)\s*$", text, re.MULTILINE)
    if not identifiers:
        fail(f"no scenario IDs found in {CONTRACT_PATH.relative_to(ROOT)}")
    if len(identifiers) != len(set(identifiers)):
        fail("duplicate scenario IDs in shared contract")
    return identifiers


def require_text(path: Path, fragments: list[str]) -> None:
    text = path.read_text(encoding="utf-8")
    for fragment in fragments:
        if fragment not in text:
            fail(f"{path.relative_to(ROOT)} is missing required wiring: {fragment!r}")


def main() -> None:
    scenarios = set(scenario_ids())
    data = json.loads(COVERAGE_PATH.read_text(encoding="utf-8"))
    ports = data.get("ports")
    if not isinstance(ports, dict):
        fail("port-coverage.json must contain an object named 'ports'")

    missing_ports = REQUIRED_PORTS - set(ports)
    extra_ports = set(ports) - REQUIRED_PORTS
    if missing_ports:
        fail(f"missing port declarations: {sorted(missing_ports)}")
    if extra_ports:
        fail(f"unknown port declarations: {sorted(extra_ports)}")

    for port_name, port in ports.items():
        covered = set(port.get("covered_scenarios", []))
        not_applicable = set(port.get("not_applicable", []))
        evidence = port.get("evidence", [])

        unknown = (covered | not_applicable) - scenarios
        if unknown:
            fail(f"{port_name} declares unknown scenarios: {sorted(unknown)}")
        overlap = covered & not_applicable
        if overlap:
            fail(f"{port_name} both covers and excludes: {sorted(overlap)}")
        invalid_exclusions = not_applicable - OPTIONAL_SCENARIOS
        if invalid_exclusions:
            fail(
                f"{port_name} excludes required scenarios: {sorted(invalid_exclusions)}"
            )
        undeclared = scenarios - covered - not_applicable
        if undeclared:
            fail(f"{port_name} has undeclared scenarios: {sorted(undeclared)}")
        if not evidence:
            fail(f"{port_name} has no native test evidence")
        for relative_path in evidence:
            evidence_path = ROOT / relative_path
            if not evidence_path.is_file():
                fail(f"{port_name} evidence file does not exist: {relative_path}")

    # Guard the exact wiring gaps that allowed Phase 3 to appear green.
    require_text(
        ROOT / "src/Cpp/CMakeLists.txt",
        ["tests/vnext_pipeline_test.cpp", "add_test(NAME pipeline_services_vnext_tests"],
    )
    require_text(
        ROOT / ".github/workflows/ci.yml",
        [
            "./mvnw -q test",
            "python -m unittest discover",
            "npm test",
            "cargo test",
            "go test ./...",
            "go test -race ./...",
            "dotnet test",
            "ctest --test-dir",
            "vnext_pipeline_test.mojo",
            "python tools/check_conformance_coverage.py",
        ],
    )

    print(
        f"vNext conformance coverage OK: {len(scenarios)} scenarios, "
        f"{len(REQUIRED_PORTS)} ports"
    )


if __name__ == "__main__":
    main()
