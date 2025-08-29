# Repository Guidelines

## Project Structure & Module Organization
- Multi-module Gradle (Java 21). Expected layout under `ps/`:
  - `ps-core/`: runtime (`Pipeline`, `Pipe`, `ShortCircuit`, `Steps`).
  - `ps-config/`: pipeline JSON, schemas, examples.
  - `ps-prompt/`: build-time prompt step codegen + manifest.
  - `ps-remote/`: HTTP/gRPC adapters.
  - `ps-disruptor/`: optional low-latency adapter.
  - `ps-examples/`: runnable samples.
- Tests live in each module at `ps-*/src/test/java`.

## Build, Test, and Development Commands
- `./gradlew clean build`: compile all modules and run tests.
- `./gradlew psGenerate`: scan for prompt steps, generate sources/tests/manifest.
- `./gradlew psVerify`: compile generated code and run its tests; fails on violations.
- `./gradlew :ps-examples:run`: run sample apps (when present).
- Requires Java 21 (`java -version`). Ensure `JAVA_HOME` points to JDK 21.

## Coding Style & Naming Conventions
- Java 21, 4-space indent, 100-char line width.
- Packages under `com.ps.*`; modules follow `ps-<name>`; classes `PascalCase`, methods/fields `camelCase`.
- Prefer `final` classes for generated code; avoid mutable statics.
- Generated steps must be deterministic: no I/O, networking, randomness, reflection, or threads.

## Testing Guidelines
- Framework: JUnit 5.
- Location: `ps-*/src/test/java` with `*Test.java` naming.
- Focus areas:
  - `shortCircuit` behavior (true/false) and `ShortCircuit.now(...)`.
  - Prompt-step determinism and example/property compliance.
  - Remote-step timeouts/retries mapping to errors.
- Run tests with `./gradlew test` and validate generated code with `./gradlew psVerify`.

## Commit & Pull Request Guidelines
- Commits: small, imperative summaries, optional scope prefix (e.g., `core:`, `prompt:`, `remote:`, `config:`, `docs:`). Reference issues (`#123`).
- PRs must include:
  - Clear description and rationale; linked issues.
  - Tests for new/changed behavior; sample JSON under `ps-config/` if applicable.
  - Notes on performance or deterministic constraints when touching prompt/remote code.

## Security & Configuration Tips
- Keep secrets out of the repo; use environment variables (e.g., `${SCORE_KEY}` in JSON).
- Place pipeline JSON in `ps-config/`; one file per pipeline.
- `psVerify` rejects banned APIs in generated code; run it before opening a PR.

