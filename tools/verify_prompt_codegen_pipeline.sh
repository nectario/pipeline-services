#!/usr/bin/env bash
set -euo pipefail

repository_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

expected_output="output=John Smith"

echo "Expected: ${expected_output}"
echo "----"

java_output="$(
  cd "${repository_root}"
  ./mvnw -q -DskipTests=true install >/dev/null 2>&1
  ./mvnw -q -f src/Java/pipeline-examples/pom.xml exec:java -Dexec.mainClass=com.pipeline.examples.Example16PromptCodegenPipeline 2>/dev/null \
    | rg -m 1 '^output='
)"
echo "Java: ${java_output}"

mojo_output="$(
  cd "${repository_root}/pipeline_services"
  pixi run mojo run -I ../src/Mojo ../src/Mojo/pipeline_services/examples/example07_prompt_codegen_pipeline.mojo 2>/dev/null \
    | rg -m 1 '^output='
)"
echo "Mojo: ${mojo_output}"

python_output="$(
  cd "${repository_root}/src/Python"
  python3 -m pipeline_services.examples.example07_prompt_codegen_pipeline 2>/dev/null | rg -m 1 '^output='
)"
echo "Python: ${python_output}"

typescript_output="$(
  cd "${repository_root}/src/typescript"
  if [[ ! -d "node_modules" ]]; then
    npm install >/dev/null
  fi
  npm run -s build >/dev/null
  node dist/src/pipeline_services/examples/example07_prompt_codegen_pipeline.js 2>/dev/null | rg -m 1 '^output='
)"
echo "TypeScript: ${typescript_output}"

rust_output="$(
  cd "${repository_root}/src/Rust"
  cargo run --quiet --example example07_prompt_codegen_pipeline 2>/dev/null | rg -m 1 '^output='
)"
echo "Rust: ${rust_output}"

cpp_output="$(
  cd "${repository_root}/src/Cpp"
  cmake -S . -B build >/dev/null 2>&1
  cmake --build build -j >/dev/null 2>&1
  ./build/example07_prompt_codegen_pipeline 2>/dev/null | rg -m 1 '^output='
)"
echo "C++: ${cpp_output}"

go_binary="${HOME}/.local/go/bin/go"
if [[ -x "${go_binary}" ]]; then
  go_output="$(
    cd "${repository_root}/src/Go"
    "${go_binary}" run ./examples/example07_prompt_codegen_pipeline 2>/dev/null | rg -m 1 '^output='
  )"
  echo "Go: ${go_output}"
else
  echo "Go: (skipped - go binary not found at ${go_binary})"
  go_output="(skipped)"
fi

echo "----"

exit_code=0
for actual_output in "${java_output}" "${mojo_output}" "${python_output}" "${typescript_output}" "${rust_output}" "${cpp_output}"; do
  if [[ "${actual_output}" != "${expected_output}" ]]; then
    exit_code=1
  fi
done

if [[ "${go_output}" != "(skipped)" && "${go_output}" != "${expected_output}" ]]; then
  exit_code=1
fi

if [[ "${exit_code}" -ne 0 ]]; then
  echo "Mismatch detected (expected '${expected_output}')." >&2
  exit "${exit_code}"
fi

echo "All checked ports matched."

