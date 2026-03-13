#!/usr/bin/env python3

from __future__ import annotations

import json
import sys
import tomllib
import xml.etree.ElementTree as ET
from pathlib import Path


EXPECTED_VERSION = "0.1.0"
EXPECTED_LICENSE = "Apache-2.0"
POM_NAMESPACE = {"m": "http://maven.apache.org/POM/4.0.0"}
REPO_ROOT = Path(__file__).resolve().parents[1]


def read_toml(path: Path) -> dict:
    return tomllib.loads(path.read_text(encoding="utf-8"))


def read_json(path: Path) -> dict:
    return json.loads(path.read_text(encoding="utf-8"))


def read_pom_version(path: Path) -> str | None:
    root = ET.fromstring(path.read_text(encoding="utf-8"))
    return root.findtext("m:version", namespaces=POM_NAMESPACE)


def main() -> int:
    errors: list[str] = []

    version_file = REPO_ROOT / "VERSION"
    if not version_file.is_file():
        errors.append("VERSION is missing.")
    else:
        version_value = version_file.read_text(encoding="utf-8").strip()
        if version_value != EXPECTED_VERSION:
            errors.append(f"VERSION must be {EXPECTED_VERSION}, found {version_value!r}.")

    pom_version = read_pom_version(REPO_ROOT / "pom.xml")
    if pom_version != EXPECTED_VERSION:
        errors.append(f"Root pom.xml version must be {EXPECTED_VERSION}, found {pom_version!r}.")

    python_project = read_toml(REPO_ROOT / "src" / "Python" / "pyproject.toml").get("project", {})
    python_version = python_project.get("version")
    if python_version != EXPECTED_VERSION:
        errors.append(f"src/Python/pyproject.toml version must be {EXPECTED_VERSION}, found {python_version!r}.")

    typescript_package = read_json(REPO_ROOT / "src" / "typescript" / "package.json")
    typescript_version = typescript_package.get("version")
    if typescript_version != EXPECTED_VERSION:
        errors.append(
            f"src/typescript/package.json version must be {EXPECTED_VERSION}, found {typescript_version!r}."
        )
    typescript_license = typescript_package.get("license")
    if typescript_license != EXPECTED_LICENSE:
        errors.append(
            f"src/typescript/package.json license must be {EXPECTED_LICENSE}, found {typescript_license!r}."
        )

    rust_package = read_toml(REPO_ROOT / "src" / "Rust" / "Cargo.toml").get("package", {})
    rust_version = rust_package.get("version")
    if rust_version != EXPECTED_VERSION:
        errors.append(f"src/Rust/Cargo.toml version must be {EXPECTED_VERSION}, found {rust_version!r}.")
    rust_license = rust_package.get("license")
    if rust_license != EXPECTED_LICENSE:
        errors.append(f"src/Rust/Cargo.toml license must be {EXPECTED_LICENSE}, found {rust_license!r}.")

    if not (REPO_ROOT / "LICENSE").is_file():
        errors.append("LICENSE is missing.")

    if errors:
        print("Metadata check failed:")
        for error in errors:
            print(f"- {error}")
        return 1

    print("Metadata check passed.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
