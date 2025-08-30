## ✅ PR for Codex — “build: make Maven compile pass; finish core implementations”

**Branch name:** `build/maven-compile-pass`

### 1) Clean up repository

- **Delete** all module `target/` directories (they’re in `.gitignore` already).

- Ensure `.gitignore` at root contains:

  ```
  target/
  .mvn/wrapper/maven-wrapper.jar
  .idea/
  .project
  .classpath
  .settings/
  *.iml
  ```

### 2) Add the pipelines folder for the prompt generator

- Create directory: `pipeline-config/pipelines/`
- Add file: `pipeline-config/pipelines/.keep` (empty)

------

### 3) Replace the following **Java files** with the exact content below

> All packages are `com.pipeline.*`. These implement the agreed `shortCircuit` semantics and keep things minimal but correct.

#### **pipeline-core/src/main/java/com/pipeline/core/ThrowingFn.java** *(already OK — no change)*

#### **pipeline-core/src/main/java/com/pipeline/core/ShortCircuit.java**

```java
package com.pipeline.core;

public final class ShortCircuit {
    private ShortCircuit() {}

    static final class Signal extends RuntimeException {
        final Object value;
        Signal(Object v) { super(null, null, false, false); this.value = v; }
    }

    @SuppressWarnings("unchecked")
    public static <T> T now(T finalValue) {
        throw new Signal(finalValue);
    }
}
```

#### **pipeline-core/src/main/java/com/pipeline/core/Steps.java** *(already OK — no change)*

#### **pipeline-core/src/main/java/com/pipeline/core/PipelineRegistry.java** *(already OK — no change)*

#### **pipeline-core/src/main/java/com/pipeline/core/Pipeline.java**

```java
package com.pipeline.core;

import com.pipeline.metrics.Metrics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class Pipeline<T> {
    private static final Logger log = LoggerFactory.getLogger(Pipeline.class);

    private final String name;
    private final List<ThrowingFn<T, T>> steps;
    private final boolean shortCircuit;
    private final List<ThrowingFn<T, T>> beforeEach;
    private final List<ThrowingFn<T, T>> afterEach;

    private Pipeline(String name, boolean shortCircuit,
                     List<ThrowingFn<T, T>> beforeEach,
                     List<ThrowingFn<T, T>> steps,
                     List<ThrowingFn<T, T>> afterEach) {
        this.name = name;
        this.shortCircuit = shortCircuit;
        this.beforeEach = List.copyOf(beforeEach);
        this.steps = List.copyOf(steps);
        this.afterEach = List.copyOf(afterEach);
    }

    @SafeVarargs
    public static <T> Pipeline<T> build(String name, boolean shortCircuit, ThrowingFn<T, T>... steps) {
        return new Builder<T>(name).shortCircuit(shortCircuit).steps(steps).build();
    }

    public static final class Builder<T> {
        private final String name;
        private boolean shortCircuit = true;
        private final List<ThrowingFn<T, T>> steps = new ArrayList<>();
        private final List<ThrowingFn<T, T>> beforeEach = new ArrayList<>();
        private final List<ThrowingFn<T, T>> afterEach = new ArrayList<>();

        public Builder(String name) { this.name = name; }

        public Builder<T> shortCircuit(boolean b) { this.shortCircuit = b; return this; }
        public Builder<T> beforeEach(ThrowingFn<T, T> pre) { this.beforeEach.add(pre); return this; }
        public Builder<T> step(ThrowingFn<T, T> s) { this.steps.add(s); return this; }
        @SafeVarargs public final Builder<T> steps(ThrowingFn<T, T>... ss) { this.steps.addAll(Arrays.asList(ss)); return this; }
        public Builder<T> afterEach(ThrowingFn<T, T> post) { this.afterEach.add(post); return this; }
        public Pipeline<T> build() { return new Pipeline<>(name, shortCircuit, beforeEach, steps, afterEach); }
    }

    public T run(T input) {
        var rec = Metrics.recorder();
        T cur = input;

        // pre
        for (int i = 0; i < beforeEach.size(); i++) {
            var fn = beforeEach.get(i);
            var stepName = "pre" + i;
            try {
                long t0 = System.nanoTime();
                cur = fn.apply(cur);
                rec.onStepSuccess(name, stepName, System.nanoTime() - t0);
            } catch (ShortCircuit.Signal sc) {
                rec.onShortCircuit(name, stepName);
                @SuppressWarnings("unchecked") T v = (T) sc.value;
                return v;
            } catch (Exception ex) {
                rec.onStepError(name, stepName, ex);
                if (shortCircuit) return cur; // last good value
                // else skip
            }
        }

        // main
        for (int i = 0; i < steps.size(); i++) {
            var fn = steps.get(i);
            var stepName = "s" + i;
            try {
                long t0 = System.nanoTime();
                cur = fn.apply(cur);
                rec.onStepSuccess(name, stepName, System.nanoTime() - t0);
            } catch (ShortCircuit.Signal sc) {
                rec.onShortCircuit(name, stepName);
                @SuppressWarnings("unchecked") T v = (T) sc.value;
                return v;
            } catch (Exception ex) {
                rec.onStepError(name, stepName, ex);
                if (shortCircuit) {
                    log.debug("short-circuit '{}' at {}", name, stepName, ex);
                    return cur; // last good
                }
            }
        }

        // post
        for (int i = 0; i < afterEach.size(); i++) {
            var fn = afterEach.get(i);
            var stepName = "post" + i;
            try {
                long t0 = System.nanoTime();
                cur = fn.apply(cur);
                rec.onStepSuccess(name, stepName, System.nanoTime() - t0);
            } catch (ShortCircuit.Signal sc) {
                rec.onShortCircuit(name, stepName);
                @SuppressWarnings("unchecked") T v = (T) sc.value;
                return v;
            } catch (Exception ex) {
                rec.onStepError(name, stepName, ex);
                if (shortCircuit) return cur;
            }
        }
        return cur;
    }

    public String name() { return name; }
    public boolean shortCircuit() { return shortCircuit; }
    public int size() { return steps.size(); }
}
```

#### **pipeline-core/src/main/java/com/pipeline/core/Pipe.java**

```java
package com.pipeline.core;

import com.pipeline.metrics.Metrics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

public final class Pipe<I, O> {
    private static final Logger log = LoggerFactory.getLogger(Pipe.class);

    private final List<ThrowingFn<?, ?>> steps;
    private final boolean shortCircuit;
    private final java.util.function.Function<Exception, O> onErrorReturn;
    private final String name;

    private Pipe(String name, boolean shortCircuit, java.util.function.Function<Exception, O> onErrorReturn,
                 List<ThrowingFn<?, ?>> steps) {
        this.name = name;
        this.shortCircuit = shortCircuit;
        this.onErrorReturn = onErrorReturn;
        this.steps = List.copyOf(steps);
    }

    public static <I> Builder<I> from(Class<I> inType) { return new Builder<>("pipe"); }
    public static <I> Builder<I> named(String name) { return new Builder<>(name); }

    public static final class Builder<I> {
        private final String name;
        private boolean shortCircuit = true;
        private final List<ThrowingFn<?, ?>> steps = new ArrayList<>();
        private java.util.function.Function<Exception, ?> onErrorReturn;

        private Builder(String name) { this.name = name; }

        public Builder<I> shortCircuit(boolean b) { this.shortCircuit = b; return this; }
        public <O> Builder<I> onErrorReturn(java.util.function.Function<Exception, O> f) { this.onErrorReturn = f; return this; }
        public <M> Builder<M> step(ThrowingFn<I, M> f) { steps.add(f); return (Builder<M>) this; }
        public <O> Pipe<I, O> to(Class<O> out) { return new Pipe<>(name, shortCircuit, (java.util.function.Function<Exception,O>) onErrorReturn, steps); }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    public O run(I in) throws Exception {
        var rec = Metrics.recorder();
        Object cur = in;
        for (int i = 0; i < steps.size(); i++) {
            var fn = (ThrowingFn) steps.get(i);
            var stepName = "s" + i;
            try {
                long t0 = System.nanoTime();
                cur = fn.apply(cur);
                rec.onStepSuccess(name, stepName, System.nanoTime() - t0);
            } catch (ShortCircuit.Signal sc) {
                rec.onShortCircuit(name, stepName);
                return (O) sc.value;
            } catch (Exception ex) {
                rec.onStepError(name, stepName, ex);
                if (shortCircuit) {
                    if (onErrorReturn != null) return onErrorReturn.apply(ex);
                    throw ex;
                }
                // continue: keep current value
            }
        }
        return (O) cur;
    }
}
```

#### **pipeline-core/src/main/java/com/pipeline/metrics/MetricsRecorder.java**

```java
package com.pipeline.metrics;

import io.micrometer.core.instrument.MeterRegistry;

public interface MetricsRecorder {
    void onStepSuccess(String pipeline, String stepName, long nanos);
    void onStepError(String pipeline, String stepName, Throwable t);
    void onShortCircuit(String pipeline, String stepName);
    MeterRegistry registry();
}
```

#### **pipeline-core/src/main/java/com/pipeline/metrics/SimpleMetricsRecorder.java**

```java
package com.pipeline.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

import java.util.concurrent.TimeUnit;

public final class SimpleMetricsRecorder implements MetricsRecorder {
    private final MeterRegistry registry = new SimpleMeterRegistry();

    @Override
    public void onStepSuccess(String pipeline, String stepName, long nanos) {
        Timer.builder(metric(pipeline, stepName, "duration"))
                .register(registry)
                .record(nanos, TimeUnit.NANOSECONDS);
    }

    @Override
    public void onStepError(String pipeline, String stepName, Throwable t) {
        Counter.builder(metric(pipeline, stepName, "errors"))
                .register(registry)
                .increment();
    }

    @Override
    public void onShortCircuit(String pipeline, String stepName) {
        Counter.builder(metric(pipeline, stepName, "short_circuits"))
                .register(registry)
                .increment();
    }

    @Override
    public MeterRegistry registry() { return registry; }

    private static String metric(String pipeline, String step, String name) {
        return "ps.pipeline." + pipeline + ".step." + step + "." + name;
    }
}
```

#### **pipeline-core/src/main/java/com/pipeline/metrics/Metrics.java**

```java
package com.pipeline.metrics;

import java.util.Objects;

public final class Metrics {
    private static volatile MetricsRecorder recorder = new SimpleMetricsRecorder();
    private Metrics() {}

    public static MetricsRecorder recorder() { return recorder; }

    public static void setRecorder(MetricsRecorder r) {
        recorder = Objects.requireNonNull(r, "recorder");
    }
}
```

------

#### **pipeline-remote/src/main/java/com/pipeline/remote/http/HttpStep.java**

```java
package com.pipeline.remote.http;

import com.pipeline.core.ThrowingFn;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.function.Function;

public final class HttpStep {
    private HttpStep() {}

    public static <I, O> ThrowingFn<I, O> jsonPost(RemoteSpec<I, O> spec) {
        return in -> invoke(spec, "POST", in);
    }

    public static <I, O> ThrowingFn<I, O> jsonGet(RemoteSpec<I, O> spec) {
        return in -> invoke(spec, "GET", in);
    }

    private static <I, O> O invoke(RemoteSpec<I, O> spec, String method, I in) throws IOException, InterruptedException {
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(spec.timeoutMillis))
                .build();

        String body = spec.toJson.apply(in);
        HttpRequest.Builder b = HttpRequest.newBuilder()
                .uri(URI.create(spec.endpoint))
                .timeout(Duration.ofMillis(spec.timeoutMillis));

        if ("POST".equalsIgnoreCase(method)) {
            b = b.POST(HttpRequest.BodyPublishers.ofString(body));
        } else {
            b = b.GET();
        }
        for (Map.Entry<String, String> e : spec.headers.entrySet()) {
            b.header(e.getKey(), e.getValue());
        }
        b.header("Content-Type", "application/json");

        IOException last = null;
        for (int attempt = 0; attempt <= spec.retries; attempt++) {
            try {
                HttpResponse<String> resp = client.send(b.build(), HttpResponse.BodyHandlers.ofString());
                int code = resp.statusCode();
                if (code >= 200 && code < 300) {
                    return spec.fromJson.apply(resp.body());
                }
                last = new IOException("HTTP " + code + " body=" + resp.body());
            } catch (IOException ioe) {
                last = ioe;
            }
        }
        throw last != null ? last : new IOException("Unknown HTTP error");
    }

    public static final class RemoteSpec<I, O> {
        public String endpoint;
        public int timeoutMillis = 1000;
        public int retries = 0;
        public Map<String, String> headers = Map.of();
        public Function<I, String> toJson;   // I -> JSON body or query
        public Function<String, O> fromJson; // JSON -> O
    }
}
```

------

#### **pipeline-config/src/main/java/com/pipeline/config/PipelineJsonLoader.java**

```java
package com.pipeline.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pipeline.core.Pipeline;
import com.pipeline.core.ThrowingFn;
import com.pipeline.remote.http.HttpStep;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.List;

/** Minimal JSON loader for unary pipelines. */
public final class PipelineJsonLoader {
    private static final ObjectMapper M = new ObjectMapper();
    private PipelineJsonLoader() {}

    public static Pipeline<String> loadUnary(InputStream in) throws IOException {
        JsonNode root = M.readTree(in);
        String name = req(root, "pipeline").asText();
        String type = root.path("type").asText("unary");
        if (!"unary".equals(type)) throw new IOException("Only unary pipelines supported by this loader");
        boolean shortCircuit = root.path("shortCircuit").asBoolean(true);

        List<ThrowingFn<String,String>> steps = new ArrayList<>();
        JsonNode arr = root.path("steps");
        if (arr.isArray()) {
            for (JsonNode s : arr) {
                if (s.has("$local")) {
                    String cls = s.get("$local").asText();
                    steps.add(instantiateFn(cls));
                } else if (s.has("$remote")) {
                    JsonNode r = s.get("$remote");
                    var spec = new HttpStep.RemoteSpec<String,String>();
                    spec.endpoint = req(r, "endpoint").asText();
                    spec.timeoutMillis = r.path("timeoutMillis").asInt(1000);
                    spec.retries = r.path("retries").asInt(0);
                    spec.toJson = body -> body;
                    spec.fromJson = body -> body;
                    steps.add(HttpStep.jsonPost(spec));
                } else {
                    throw new IOException("Unsupported step: " + s.toString());
                }
            }
        }

        @SuppressWarnings("unchecked")
        ThrowingFn<String,String>[] arrSteps = steps.toArray(new ThrowingFn[0]);
        return Pipeline.build(name, shortCircuit, arrSteps);
    }

    @SuppressWarnings("unchecked")
    private static ThrowingFn<String,String> instantiateFn(String fqcn) throws IOException {
        try {
            Class<?> c = Class.forName(fqcn);
            Constructor<?> ctor = c.getDeclaredConstructor();
            ctor.setAccessible(true);
            Object o = ctor.newInstance();
            if (o instanceof ThrowingFn<?,?> fn) {
                return (ThrowingFn<String,String>) fn;
            }
            throw new IOException("Class does not implement ThrowingFn: " + fqcn);
        } catch (Exception e) {
            throw new IOException("Failed to instantiate " + fqcn, e);
        }
    }

    private static JsonNode req(JsonNode n, String field) throws IOException {
        if (!n.has(field)) throw new IOException("Missing required field: " + field);
        return n.get(field);
    }
}
```

------

#### **pipeline-disruptor/src/main/java/com/pipeline/disruptor/DisruptorEngine.java**

```java
package com.pipeline.disruptor;

import com.pipeline.core.Pipeline;
import com.pipeline.metrics.Metrics;

import java.util.Objects;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class DisruptorEngine<T> implements AutoCloseable {
    private final String name;
    private final Pipeline<T> pipeline;
    private final ArrayBlockingQueue<T> queue;
    private final ExecutorService worker;
    private volatile boolean running = true;

    public DisruptorEngine(String name, int bufferSize, Pipeline<T> pipeline) {
        this.name = Objects.requireNonNull(name);
        this.pipeline = Objects.requireNonNull(pipeline);
        this.queue = new ArrayBlockingQueue<>(bufferSize);
        this.worker = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "engine-" + name);
            t.setDaemon(true);
            return t;
        });
        worker.execute(this::loop);
    }

    private void loop() {
        while (running) {
            try {
                T payload = queue.take();
                var rec = Metrics.recorder();
                long t0 = System.nanoTime();
                pipeline.run(payload);
                rec.onStepSuccess(name, "e2e", System.nanoTime() - t0);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                break;
            } catch (Throwable t) {
                Metrics.recorder().onStepError(name, "e2e", t);
            }
        }
    }

    public void publish(T payload) {
        if (!running) throw new IllegalStateException("engine stopped");
        queue.offer(payload);
    }

    public void shutdown() {
        running = false;
        worker.shutdownNow();
    }

    @Override public void close() { shutdown(); }
}
```

------

#### **pipeline-prompt/src/main/java/com/pipeline/prompt/Prompt.java**

```java
package com.pipeline.prompt;

import com.pipeline.core.ThrowingFn;

import java.util.ArrayList;
import java.util.List;

public final class Prompt {
    private Prompt() {}

    public static <I, O> PromptBuilder<I, O> step(Class<I> in, Class<O> out) {
        return new PromptBuilder<>();
    }

    public static final class PromptBuilder<I, O> {
        private String name = "promptStep";
        private String goal = "";
        private final List<String> rules = new ArrayList<>();
        private final List<Example<I,O>> examples = new ArrayList<>();
        private final List<String> properties = new ArrayList<>();
        private int p50Micros = 0;

        public PromptBuilder<I, O> name(String n) { this.name = n; return this; }
        public PromptBuilder<I, O> goal(String text) { this.goal = text; return this; }
        public PromptBuilder<I, O> rules(String... lines) {
            if (lines != null) for (String s : lines) rules.add(s);
            return this;
        }
        public PromptBuilder<I, O> example(I input, O expected) { examples.add(new Example<>(input, expected)); return this; }
        public PromptBuilder<I, O> property(String assertion) { properties.add(assertion); return this; }
        public PromptBuilder<I, O> p50Micros(int budget) { this.p50Micros = budget; return this; }

        public ThrowingFn<I, O> build() {
            // Placeholder; build-time codegen should replace this implementation.
            return in -> {
                throw new UnsupportedOperationException("Prompt-generated code not available for step '" + name + "'");
            };
        }

        record Example<I, O>(I in, O out) {}
    }
}
```

#### **pipeline-prompt/src/main/java/com/pipeline/prompt/CodegenMain.java**

```java
package com.pipeline.prompt;

import java.nio.file.*;

public final class CodegenMain {
  public static void main(String[] args) throws Exception {
    if (args.length < 2) {
      System.err.println("Usage: CodegenMain <pipelinesDir> <outDir>");
      return;
    }
    Path in = Paths.get(args[0]);
    Path out = Paths.get(args[1]);
    Files.createDirectories(out);
    // v0.1: no-op placeholder. Future versions will scan JSON and emit sources.
  }
}
```

------

### 4) Replace **pom.xml** files with clean content

Use the POMs I provided earlier in our Maven migration message (they include `slf4j-api`, Micrometer, Jackson, Disruptor, Exec plugin for examples). If you prefer, Codex can re-create them from those exact blocks:

- Root `pom.xml` (parent/aggregator with `<modules>` and `<dependencyManagement>`)
- `pipeline-core/pom.xml` (depends on `micrometer-core` and `slf4j-api`)
- `pipeline-config/pom.xml` (depends on `pipeline-core`, `jackson-databind`)
- `pipeline-remote/pom.xml` (depends on `pipeline-core`, `jackson-databind`)
- `pipeline-prompt/pom.xml` (depends on `pipeline-core`, `jackson-databind`, and runs `CodegenMain` in `generate-sources`)
- `pipeline-disruptor/pom.xml` (depends on `pipeline-core`, `disruptor`)
- `pipeline-examples/pom.xml` (depends on all others; `exec-maven-plugin` wired to run `com.pipeline.examples.Main`)

------

### 5) Build & run (for Codex to verify)

```bash
./mvnw -q -DskipTests package
./mvnw -q -pl pipeline-examples exec:java -Dexec.mainClass=com.pipeline.examples.Main
```

Expected output (from the example): a normalized “Hello World…” line trimmed to 40 chars.

------

## Why this PR

- Restores a clean, compilable baseline after package rename and Maven migration.
- Locks in the **shortCircuit** semantics for both `Pipeline<T>` (unary) and `Pipe<I,O>` (typed).
- Keeps **prompt** and **remote** step types wired in (with safe placeholders), so we can iterate on codegen without blocking usage.
- Removes committed build outputs and prevents future noise.

