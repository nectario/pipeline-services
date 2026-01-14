# Pipeline Services REST verification (multi-language)

## Common endpoint

All runs target the same local REST endpoint:

```
http://127.0.0.1:8765/remote_hello.txt
```

The server was started from the Python fixtures directory:

```bash
python3 -m http.server 8765 --bind 127.0.0.1 -d src/Python/pipeline_services/examples/fixtures
```

Expected response body:

```
Hello from remote fixture
```

## Results

### Python

Command:

```bash
cd src/Python
python3 -m pipeline_services.examples.example04_json_loader_remote_get
```

Output:

```
Hello from remote fixture
```

### TypeScript

Commands:

```bash
cd src/typescript
npm install
npm run build
node dist/src/pipeline_services/examples/example04_json_loader_remote_get.js
```

Output:

```
Hello from remote fixture
```

### Rust

Command (note explicit `NO_PROXY` to bypass the proxy for localhost):

```bash
cd src/Rust
NO_PROXY=localhost,127.0.0.1,::1 cargo run --example example04_json_loader_remote_get
```

Output:

```
Hello from remote fixture
```

### Java

Attempted build command:

```bash
mvn -q -pl src/Java/pipeline-config -am -DskipTests package
```

Result:

```
[ERROR] Plugin org.apache.maven.plugins:maven-resources-plugin:3.3.1 ...
[ERROR] Could not transfer artifact ... from/to central (https://repo.maven.apache.org/maven2): status code: 403
```

### Mojo

Attempted to validate the Mojo toolchain:

```bash
cd pipeline_services
pixi --version
```

Result:

```
bash: command not found: pixi
```

## Notes

- Rust required `NO_PROXY=localhost,127.0.0.1,::1` because the environment sets an HTTP proxy and the default
  uppercase `NO_PROXY` value did not include localhost. Without that override, the request returned `HTTP 403`.
- Java could not build due to Maven Central access returning `HTTP 403` from this environment.
- Mojo could not be exercised because `pixi` is not installed in the container.
