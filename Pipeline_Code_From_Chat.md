Love this direction. Here’s how the **renamed, programmatic API** looks in practice—**one class** your users touch for unary pipelines (**`com.pipeline.api.Pipeline<T>`**) and one for typed flows (**`com.pipeline.api.Flow<I>`**). JSON config lives *in the same class* (no separate loader in user code), and you can **mix & match** JSON + method references. The prompt step is **build‑time generated** and then used like any other step.

Below are three concrete examples you can drop into `pipeline-examples` once the API is added.

------

## 1) Pure JSON: instantiate directly from a JSON string

**JSON with a `$prompt` step** (build‑time generated code):

```json
{
  "pipeline": "normalize_name",
  "type": "unary",
  "shortCircuit": false,
  "steps": [
    {"$local": "com.pipeline.examples.adapters.TextStripStep"},
    {
      "$prompt": {
        "class": "com.pipeline.generated.NormalizeName",
        "in": "java.lang.String",
        "out": "java.lang.String",
        "goal": "Normalize human names to Title Case while collapsing whitespace.",
        "rules": [
          "Trim leading/trailing whitespace",
          "Collapse internal whitespace to one space",
          "Title-case tokens"
        ],
        "examples": [
          {"in": "  john   SMITH ", "out": "John Smith"},
          {"in": "ALICE    deLANEY", "out": "Alice Delaney"}
        ],
        "p50Micros": 200
      }
    },
    {"$local": "com.pipeline.examples.adapters.TextNormalizeStep"}
  ]
}
```

**Usage (no separate loader):**

```java
package com.pipeline.examples;

import com.pipeline.api.Pipeline;

public final class ExampleJsonPromptOnly {
  public static void run() {
    String json = """
      { "pipeline":"normalize_name", "type":"unary", "shortCircuit":false,
        "steps":[
          {"$local":"com.pipeline.examples.adapters.TextStripStep"},
          {"$prompt":{"class":"com.pipeline.generated.NormalizeName",
                      "in":"java.lang.String","out":"java.lang.String",
                      "goal":"Normalize human names to Title Case while collapsing whitespace.",
                      "rules":["Trim","Collapse internal whitespace","Title-case tokens"],
                      "examples":[{"in":"  john   SMITH ","out":"John Smith"}],
                      "p50Micros":200}},
          {"$local":"com.pipeline.examples.adapters.TextNormalizeStep"}
        ] }
    """;

    // Builds a programmatic Pipeline<String>, sealed on first run
    Pipeline<String> p = new Pipeline<>(json);
    System.out.println("[json+prompt] -> " + p.run("   jOhN   SMITH   "));
  }
}
```

> **How the prompt step works:** At `mvn package`, your `pipeline-prompt` codegen scans JSON for `$prompt`, generates `com.pipeline.generated.NormalizeName` into `target/generated-sources/prompt`, and it’s compiled like any other class. At runtime, the pipeline just reflects that class—no runtime LLM call.

------

## 2) Mix & match: start with method references, then append JSON

```java
package com.pipeline.examples;

import com.pipeline.api.Pipeline;
import com.pipeline.examples.steps.TextSteps;
import com.pipeline.examples.steps.PolicySteps;

public final class ExampleJsonPromptMixed {
  public static void run() {
    // Start programmatically (no builder)
    Pipeline<String> p = new Pipeline<String>()
        .shortCircuit(false)
        .before(PolicySteps::rateLimit)
        .addAction(TextSteps::strip);

    // Append actions from JSON at runtime (still mutable because not run yet)
    String json = """
      {"pipeline":"normalize_name","type":"unary","shortCircuit":false,
       "steps":[
         {"$prompt":{"class":"com.pipeline.generated.NormalizeName",
                     "in":"java.lang.String","out":"java.lang.String",
                     "goal":"Normalize names","rules":["Trim","Collapse spaces","Title-case"]}},
         {"$local":"com.pipeline.examples.adapters.TextNormalizeStep"}
       ]}
    """;
    p.addPipelineConfig(json)                 // appends to the current plan
     .addAction(TextSteps::truncateAt280)     // more programmatic actions
     .after(PolicySteps::audit);

    // First run seals the plan; subsequent addAction/addPipelineConfig will throw
    System.out.println("[mixed] -> " + p.run("   aLiCe   deLANEY   "));
  }
}
```

------

## 3) JSON from a resource path, then a prompt step + tap

```java
package com.pipeline.examples;

import com.pipeline.api.Pipeline;
import com.pipeline.examples.steps.TextSteps;

public final class ExampleJsonFromPath {
  public static void run() {
    // resources/pipelines/normalize_name.json contains the same structure as Example #1
    String path = "pipeline-examples/src/main/resources/pipelines/normalize_name.json";

    Pipeline<String> p = new Pipeline<>(path)     // constructor auto-detects path vs JSON
        .addAction(s -> System.out.println("[tap] = " + s))  // Consumer tap
        .addAction(TextSteps::upper);                         // continue programmatically

    System.out.println("[json-path+mixed] -> " + p.run("  john  SMITH "));
  }
}
```

------

# What Codex needs to implement (rename + API)

> You said “Let’s rename as you suggested”—this implements that minimal, programmatic API with JSON **inside the class**. Users never touch a loader.

### New module (small): `pipeline-api`

Depends on `pipeline-core` and (for JSON) Jackson + `pipeline-remote` (for `$remote` steps). Internally calls your codegen output by class name; no dependency on `pipeline-prompt`.

### 1) `com.pipeline.api.Pipeline<T>` (programmatic unary, seals on first run)

```java
package com.pipeline.api;

import com.fasterxml.jackson.databind.*;
import com.pipeline.core.*;
import com.pipeline.remote.http.HttpStep;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

public final class Pipeline<T> {
  private final ObjectMapper M = new ObjectMapper();
  private String name = "pipeline";
  private boolean shortCircuit = true;

  private final List<ThrowingFn<T,T>> pre  = new ArrayList<>();
  private final List<ThrowingFn<T,T>> main = new ArrayList<>();
  private final List<ThrowingFn<T,T>> post = new ArrayList<>();

  private volatile com.pipeline.core.Pipeline<T> compiled;

  // === Constructors ===
  public Pipeline() {}
  @SafeVarargs public Pipeline(ThrowingFn<T,T>... actions) { Collections.addAll(this.main, actions); }
  public Pipeline(String jsonOrPath) { addPipelineConfig(jsonOrPath); }
  public Pipeline(Path jsonPath) { addPipelineConfig(jsonPath); }

  // === Mutators (fail if sealed) ===
  private void ensureMutable() {
    if (compiled != null) throw new IllegalStateException("Pipeline '" + name + "' is sealed");
  }
  public Pipeline<T> name(String n) { ensureMutable(); this.name = Objects.requireNonNull(n); return this; }
  public Pipeline<T> shortCircuit(boolean b) { ensureMutable(); this.shortCircuit = b; return this; }

  public Pipeline<T> before(ThrowingFn<T,T> preFn) { ensureMutable(); pre.add(preFn); return this; }
  public Pipeline<T> after(ThrowingFn<T,T> postFn) { ensureMutable(); post.add(postFn); return this; }

  public Pipeline<T> addAction(ThrowingFn<T,T> fn) { ensureMutable(); main.add(fn); return this; }
  public Pipeline<T> addAction(ThrowingConsumer<? super T> consumer) { ensureMutable(); main.add(Steps.tap(consumer)); return this; }
  public <U> Pipeline<T> addAction(ThrowingBiFn<? super T, ? super U, ? extends T> fn, U arg) {
    ensureMutable(); main.add(Steps.bind(fn, arg)); return this;
  }
  public <U> Pipeline<T> addAction(ThrowingBiConsumer<? super T, ? super U> cons, U arg) {
    ensureMutable(); main.add(Steps.bind(cons, arg)); return this;
  }

  // === JSON config append ===
  public Pipeline<T> addPipelineConfig(String jsonOrPath) {
    ensureMutable();
    String s = jsonOrPath.strip();
    if (looksLikeJson(s)) {
      appendFromJsonString(s);
    } else {
      Path p = Paths.get(jsonOrPath);
      if (Files.exists(p)) appendFromJsonString(readFile(p));
      else appendFromJsonString(s); // treat as inline JSON even if not starting with '{'
    }
    return this;
  }

  public Pipeline<T> addPipelineConfig(Path path) {
    ensureMutable();
    appendFromJsonString(readFile(path));
    return this;
  }

  // === Seal & Run ===
  public synchronized com.pipeline.core.Pipeline<T> seal() {
    if (compiled == null) {
      var b = com.pipeline.core.Pipeline.<T>builder(name).shortCircuit(shortCircuit);
      for (var f : pre)  b.beforeEach(f);
      for (var f : main) b.step(f);
      for (var f : post) b.afterEach(f);
      compiled = b.build();
    }
    return compiled;
  }
  public T run(T input) { return seal().run(input); }
  public boolean isSealed() { return compiled != null; }

  // === Helpers ===
  private static boolean looksLikeJson(String s) {
    String t = s.strip();
    return (!t.isEmpty() && (t.charAt(0) == '{' || t.charAt(0) == '['));
  }
  private static String readFile(Path p) {
    try { return Files.readString(p, StandardCharsets.UTF_8); }
    catch (IOException e) { throw new RuntimeException("Failed to read " + p, e); }
  }

  @SuppressWarnings("unchecked")
  private void appendFromJsonString(String json) {
    try {
      JsonNode root = M.readTree(json);
      if (!"unary".equals(root.path("type").asText("unary")))
        throw new IllegalArgumentException("Only unary pipelines supported by this API");
      if (root.has("pipeline")) this.name = root.get("pipeline").asText(name);
      this.shortCircuit = root.path("shortCircuit").asBoolean(this.shortCircuit);

      JsonNode steps = root.path("steps");
      if (steps.isArray()) {
        for (JsonNode s : steps) {
          if (s.has("$local")) {
            String cls = s.get("$local").asText();
            main.add((ThrowingFn<T,T>) instantiateFn(cls));
          } else if (s.has("$prompt")) {
            String cls = s.get("$prompt").get("class").asText();
            main.add((ThrowingFn<T,T>) instantiateFn(cls));
          } else if (s.has("$remote")) {
            JsonNode r = s.get("$remote");
            var spec = new HttpStep.RemoteSpec<String,String>();
            spec.endpoint = req(r, "endpoint").asText();
            spec.timeoutMillis = r.path("timeoutMillis").asInt(1000);
            spec.retries = r.path("retries").asInt(0);
            spec.headers = Map.of(); // extend if needed
            spec.toJson = body -> body;
            spec.fromJson = body -> body;
            main.add((ThrowingFn<T,T>) HttpStep.jsonPost(spec));
          } else {
            throw new IllegalArgumentException("Unsupported step: " + s);
          }
        }
      }
    } catch (IOException e) {
      throw new RuntimeException("Invalid JSON pipeline config", e);
    }
  }

  private static JsonNode req(JsonNode n, String field) {
    if (!n.has(field)) throw new IllegalArgumentException("Missing required field: " + field);
    return n.get(field);
  }

  private static Object instantiateFn(String fqcn) {
    try {
      Class<?> c = Class.forName(fqcn);
      var ctor = c.getDeclaredConstructor();
      ctor.setAccessible(true);
      Object o = ctor.newInstance();
      if (!(o instanceof ThrowingFn<?,?>)) {
        throw new IllegalArgumentException("Class does not implement ThrowingFn: " + fqcn);
      }
      return o;
    } catch (Exception e) {
      throw new RuntimeException("Failed to instantiate " + fqcn, e);
    }
  }
}
```

> **Type note:** The JSON path above assumes **`String -> String`** steps. That matches your examples and keeps the API simple. We can extend the JSON schema to declare other types later.

### 2) `com.pipeline.api.Flow<I>` (programmatic typed flow; no JSON yet)

Same surface as the earlier `FlowPlan`, just renamed—users compose with `addAction(...)` and call `run(...)`. (You can add typed JSON later with step signatures.)

------

# Build-time prompt integration (unchanged for users)

- Keep the `pipeline-prompt` generator that turns `$prompt` into a class, e.g., `com.pipeline.generated.NormalizeName`.
- Ensure your Maven for `pipeline-prompt` runs in `generate-sources` for all modules (so examples see the class).
- Users never call a “loader” or generator—the **constructor does the JSON part**; the **Maven phase does the prompt part**.

------

## README snippet (drop-in)

~~~markdown
### Zero-ceremony pipelines (JSON + code, same class)

```java
import com.pipeline.api.Pipeline;

// Pure JSON
Pipeline<String> p1 = new Pipeline<>(Files.readString(Path.of("pipelines/normalize_name.json")));
System.out.println(p1.run("  john   SMITH  "));

// Mix JSON + method refs
Pipeline<String> p2 = new Pipeline<String>(TextSteps::strip)
    .addPipelineConfig(json)       // appends actions from JSON
    .addAction(TextSteps::normalizeWhitespace);
System.out.println(p2.run("  aLiCe   deLANEY  "));
~~~

> `$prompt` steps are **generated at build time** (no runtime LLM calls). The generated class is reflected automatically when you instantiate from JSON.

```
---

If you want, I can package all of this as a single Codex PR (“`feat/api: Pipeline<T> w/ JSON constructors + Flow<I>`”) with the example classes (`ExampleJsonPromptOnly`, `ExampleJsonPromptMixed`, `ExampleJsonFromPath`) and README edits ready to go.
::contentReference[oaicite:0]{index=0}
```