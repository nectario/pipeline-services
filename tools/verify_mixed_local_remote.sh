#!/usr/bin/env bash
set -euo pipefail

repository_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

fixture_host="127.0.0.1"
fixture_port="8765"
fixture_base_url="http://${fixture_host}:${fixture_port}"

server_pid=""

cleanup() {
  if [[ -n "${server_pid}" ]]; then
    kill "${server_pid}" 2>/dev/null || true
    wait "${server_pid}" 2>/dev/null || true
  fi
}

trap cleanup EXIT

python3 "${repository_root}/tools/pipeline_fixture_server.py" --host "${fixture_host}" --port "${fixture_port}" >/dev/null 2>&1 &
server_pid="$!"

health_url="${fixture_base_url}/health"
attempt_index=0
while [[ "${attempt_index}" -lt 50 ]]; do
  if curl -fsS "${health_url}" >/dev/null 2>&1; then
    break
  fi
  attempt_index=$((attempt_index + 1))
  sleep 0.05
done

if ! curl -fsS "${health_url}" >/dev/null 2>&1; then
  echo "fixture server failed to start at ${health_url}" >&2
  exit 1
fi

expected_output="output=hello remote|"

echo "Expected: ${expected_output}"
echo "----"

java_output="$(
  cd "${repository_root}"
  ./mvnw -q -DskipTests=true install >/dev/null 2>&1
  ./mvnw -q -f src/Java/pipeline-examples/pom.xml exec:java -Dexec.mainClass=com.pipeline.examples.Example15MixedLocalRemote 2>/dev/null \
    | rg -m 1 '^output='
)"
echo "Java: ${java_output}"

mojo_output="$(
  cd "${repository_root}/pipeline_services"
  pixi run mojo run -I ../src/Mojo ../src/Mojo/pipeline_services/examples/example06_mixed_local_remote.mojo 2>/dev/null \
    | rg -m 1 '^output='
)"
echo "Mojo: ${mojo_output}"

python_output="$(
  cd "${repository_root}/src/Python"
  python3 -m pipeline_services.examples.example06_mixed_local_remote 2>/dev/null | rg -m 1 '^output='
)"
echo "Python: ${python_output}"

typescript_output="$(
  cd "${repository_root}/src/typescript"
  if [[ ! -d "node_modules" ]]; then
    npm install >/dev/null
  fi
  npm run -s build >/dev/null
  node dist/src/pipeline_services/examples/example06_mixed_local_remote.js 2>/dev/null | rg -m 1 '^output='
)"
echo "TypeScript: ${typescript_output}"

rust_output="$(
  cd "${repository_root}/src/Rust"
  cargo run --quiet --example example06_mixed_local_remote 2>/dev/null | rg -m 1 '^output='
)"
echo "Rust: ${rust_output}"

cpp_output="$(
  cd "${repository_root}/src/Cpp"
  cmake -S . -B build >/dev/null 2>&1
  cmake --build build -j >/dev/null 2>&1
  ./build/example06_mixed_local_remote 2>/dev/null | rg -m 1 '^output='
)"
echo "C++: ${cpp_output}"

go_binary="${HOME}/.local/go/bin/go"
if [[ -x "${go_binary}" ]]; then
  go_output="$(
    cd "${repository_root}/src/Go"
    "${go_binary}" run ./examples/example06_mixed_local_remote 2>/dev/null | rg -m 1 '^output='
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
