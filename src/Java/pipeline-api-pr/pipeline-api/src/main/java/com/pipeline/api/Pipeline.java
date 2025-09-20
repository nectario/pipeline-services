package com.pipeline.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pipeline.core.ThrowingFn;
import com.pipeline.core.ThrowingConsumer;
import com.pipeline.core.ThrowingBiFn;
import com.pipeline.core.ThrowingBiConsumer;
import com.pipeline.core.Steps;
import com.pipeline.remote.http.HttpStep;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

public final class Pipeline<T> {
  private static final ObjectMapper M = new ObjectMapper();

  private String name = "pipeline";
  private boolean shortCircuit = true;

  private final List<ThrowingFn<T,T>> pre  = new ArrayList<>();
  private final List<ThrowingFn<T,T>> main = new ArrayList<>();
  private final List<ThrowingFn<T,T>> post = new ArrayList<>();

  private volatile com.pipeline.core.Pipeline<T> compiled;

  public Pipeline() {}

  @SafeVarargs
  public Pipeline(ThrowingFn<T,T>... actions) {
    Collections.addAll(this.main, actions);
  }

  public Pipeline(String jsonOrPath) {
    addPipelineConfig(jsonOrPath);
  }

  public Pipeline(Path jsonPath) {
    addPipelineConfig(jsonPath);
  }

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

  public Pipeline<T> addPipelineConfig(String jsonOrPath) {
    ensureMutable();
    String s = jsonOrPath == null ? "" : jsonOrPath.strip();
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

  private static boolean looksLikeJson(String s) {
    if (s == null) return false;
    String t = s.strip();
    return !t.isEmpty() && (t.charAt(0) == '{' || t.charAt(0) == '[');
  }
  private static String readFile(Path p) {
    try { return Files.readString(p, StandardCharsets.UTF_8); }
    catch (IOException e) { throw new RuntimeException("Failed to read " + p, e); }
  }

  @SuppressWarnings("unchecked")
  private void appendFromJsonString(String json) {
    try {
      JsonNode root = M.readTree(json);
      String type = root.path("type").asText("unary");
      if (!"unary".equals(type))
        throw new IllegalArgumentException("Only unary pipelines supported here (type=" + type + ")");
      if (root.has("pipeline")) this.name = root.get("pipeline").asText(name);
      if (root.has("shortCircuit")) this.shortCircuit = root.get("shortCircuit").asBoolean(this.shortCircuit);

      JsonNode steps = root.path("steps");
      if (!steps.isArray()) return;

      for (JsonNode s : steps) {
        if (s.has("$local")) {
          String cls = s.get("$local").asText();
          main.add((ThrowingFn<T,T>) instantiateFn(cls));

        } else if (s.has("$prompt")) {
          JsonNode p = s.get("$prompt");
          String fqcn = req(p, "class").asText(); // generated class at build time
          main.add((ThrowingFn<T,T>) instantiateFn(fqcn));

        } else if (s.has("$remote")) {
          JsonNode r = s.get("$remote");
          var spec = new HttpStep.RemoteSpec<String,String>();
          spec.endpoint = req(r, "endpoint").asText();
          spec.timeoutMillis = r.path("timeoutMillis").asInt(1000);
          spec.retries = r.path("retries").asInt(0);
          spec.headers = Collections.emptyMap();
          spec.toJson = body -> body;
          spec.fromJson = body -> body;
          main.add((ThrowingFn<T,T>) HttpStep.jsonPost(spec));

        } else {
          throw new IllegalArgumentException("Unsupported step: " + s.toString());
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
