package com.pipeline.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pipeline.core.ThrowingFn;
import com.pipeline.core.ThrowingConsumer;
import com.pipeline.core.ThrowingBiFn;
import com.pipeline.core.ThrowingBiConsumer;
import com.pipeline.core.ThrowingPred;
import com.pipeline.core.Steps;
import com.pipeline.core.Jumps;
import com.pipeline.core.metrics.Metrics;
import com.pipeline.core.metrics.NoopMetrics;
import com.pipeline.remote.http.HttpStep;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.*;
import java.util.function.Function;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Unified, subclassable Pipeline facade (unary + typed) with:
 *  - pre/steps/post, labels, beans, static/instance $method, $local/$prompt/$remote
 *  - JSON append (unary or typed), including per-step 'jumpWhen' predicate wrapper
 *  - Jump-by-label engine (opt-in) for polling/workflows: Jumps.now/after inside steps
 *  - Optional jumpTo(label) (one-shot next-run start) for ad-hoc starts
 *  - Metrics hooks for pipeline and per-step timing, errors, jumps
 *  - Seals on first run to immutable core when jumps are disabled
 */
public class Pipeline<I, C> {
  private static final ObjectMapper M = new ObjectMapper();

  private String name = "pipeline";
  private boolean shortCircuit = true;
  private Function<Exception, ? super C> onErrorReturn; // used when sealing typed

  // Phase lists
  private final List<ThrowingFn<?, ?>> pre  = new ArrayList<>();  // C->C
  private final List<ThrowingFn<?, ?>> main = new ArrayList<>();  // evolving types
  private final List<ThrowingFn<?, ?>> post = new ArrayList<>();  // C->C
  private final List<String> preLabels  = new ArrayList<>();
  private final List<String> mainLabels = new ArrayList<>();
  private final List<String> postLabels = new ArrayList<>();
  private final List<Class<?>> preInTypes  = new ArrayList<>();   // nullable; set for typed JSON
  private final List<Class<?>> mainInTypes = new ArrayList<>();
  private final List<Class<?>> postInTypes = new ArrayList<>();

  // Compiled cores (used when jumps disabled)
  private volatile com.pipeline.core.Pipeline<C> compiledUnary;
  private volatile com.pipeline.core.Pipe<I, ?> compiledTyped;

  // Beans for instance targets
  private final Map<String,Object> beans = new HashMap<>();

  // ----- Metrics -----
  private volatile Metrics metrics = NoopMetrics.INSTANCE;
  private final ThreadLocal<Metrics.RunScope> currentRunScope = new ThreadLocal<>();

  public Pipeline<I,C> metrics(Metrics m) { this.metrics = (m == null) ? NoopMetrics.INSTANCE : m; return this; }

  // ----- Jump engine config -----
  private boolean jumpsEnabled = false;
  private int maxJumpsPerRun = 128;
  public interface Sleeper { void sleep(long millis) throws InterruptedException; }
  private Sleeper sleeper = millis -> { if (millis > 0) Thread.sleep(millis); };
  private String queuedStartLabel;

  // Flattened plan for jump engine
  private boolean controlPlanBuilt = false;
  private final List<ThrowingFn<Object,Object>> flatFns = new ArrayList<>();
  private final List<String> flatLabels = new ArrayList<>();
  private final List<Class<?>> flatExpectedIn = new ArrayList<>();
  private final Map<String,Integer> labelIndex = new HashMap<>();
  private int preCount = 0; // number of items from pre (to block jumps into pre)

  // ---- factories ----
  public Pipeline() {}
  @SafeVarargs public Pipeline(ThrowingFn<C,C>... actions) { for (var a: actions) push(Section.MAIN, a, null, null); }
  public static <T> Pipeline<T,T> named(String name) { return new Pipeline<T,T>().name(name); }
  public static <I> Pipeline<I,I> named(String name, boolean shortCircuit) {
    return new Pipeline<I,I>().name(name).shortCircuit(shortCircuit);
  }
  public Pipeline(String jsonOrPath) { addPipelineConfig(jsonOrPath); }
  public Pipeline(Path path) { addPipelineConfig(path); }

  // ---- config (mutable until sealed) ----
  protected void ensureMutable() {
    if (compiledUnary != null || compiledTyped != null || controlPlanBuilt)
      throw new IllegalStateException("Pipeline '" + name + "' is sealed");
  }
  public Pipeline<I,C> name(String n) { ensureMutable(); this.name = Objects.requireNonNull(n); return this; }
  public Pipeline<I,C> shortCircuit(boolean b) { ensureMutable(); this.shortCircuit = b; return this; }
  public Pipeline<I,C> onErrorReturn(Function<Exception, ? super C> f) { ensureMutable(); this.onErrorReturn = f; return this; }

  /** Register a bean instance to target instance methods from JSON or code. */
  public Pipeline<I,C> addBean(String id, Object instance) {
    ensureMutable();
    String key = id.startsWith("@") ? id.substring(1) : id;
    if (key.equals("this") || key.equals("self")) {
      throw new IllegalArgumentException("Bean id '" + id + "' is reserved for the pipeline instance");
    }
    beans.put(key, Objects.requireNonNull(instance));
    return this;
  }

  // pre/post (C->C)
  @SuppressWarnings("unchecked")
  public Pipeline<I,C> before(ThrowingFn<? super C, ? extends C> preFn) { ensureMutable(); push(Section.PRE, (ThrowingFn<C,C>) preFn, null, null); return this; }
  @SuppressWarnings("unchecked")
  public Pipeline<I,C> after(ThrowingFn<? super C, ? extends C> postFn) { ensureMutable(); push(Section.POST, (ThrowingFn<C,C>) postFn, null, null); return this; }

  // Labeled variants
  @SuppressWarnings("unchecked")
  public Pipeline<I,C> before(String label, ThrowingFn<? super C, ? extends C> preFn) {
    ensureMutable(); validateNewLabel(label); push(Section.PRE, (ThrowingFn<C,C>) preFn, label, null); return this;
  }
  @SuppressWarnings("unchecked")
  public Pipeline<I,C> after(String label, ThrowingFn<? super C, ? extends C> postFn) {
    ensureMutable(); validateNewLabel(label); push(Section.POST, (ThrowingFn<C,C>) postFn, label, null); return this;
  }

  // addAction overloads (unlabeled)
  @SuppressWarnings("unchecked")
  public <M> Pipeline<I,M> addAction(ThrowingFn<? super C, ? extends M> fn) {
    ensureMutable(); push(Section.MAIN, (ThrowingFn<C,?>) fn, null, null); return (Pipeline<I,M>) this;
  }
  public Pipeline<I,C> addAction(ThrowingConsumer<? super C> consumer) {
    return addAction(Steps.tap(consumer));
  }
  public <U,M> Pipeline<I,M> addAction(ThrowingBiFn<? super C, ? super U, ? extends M> fn, U arg) {
    return addAction(Steps.bind(fn, arg));
  }
  public <U> Pipeline<I,C> addAction(ThrowingBiConsumer<? super C, ? super U> cons, U arg) {
    return addAction(Steps.bind(cons, arg));
  }

  // Labeled addAction overloads
  @SuppressWarnings("unchecked")
  public <M> Pipeline<I,M> addAction(String label, ThrowingFn<? super C, ? extends M> fn) {
    ensureMutable(); validateNewLabel(label); push(Section.MAIN, (ThrowingFn<C,?>) fn, label, null); return (Pipeline<I,M>) this;
  }
  public Pipeline<I,C> addAction(String label, ThrowingConsumer<? super C> consumer) {
    return addAction(label, Steps.tap(consumer));
  }
  public <U,M> Pipeline<I,M> addAction(String label, ThrowingBiFn<? super C, ? super U, ? extends M> fn, U arg) {
    return addAction(label, Steps.bind(fn, arg));
  }
  public <U> Pipeline<I,C> addAction(String label, ThrowingBiConsumer<? super C, ? super U> cons, U arg) {
    return addAction(label, Steps.bind(cons, arg));
  }

  // ---- JSON append (unary or typed), with beans and labels ----
  public Pipeline<I,C> addPipelineConfig(String jsonOrPath) {
    ensureMutable();
    // Expose @this/@self so JSON can target instance methods on subclasses
    beans.put("this", this);
    beans.put("self", this);

    String s = (jsonOrPath == null) ? "" : jsonOrPath.strip();
    if (looksLikeJson(s)) appendFromJson(s);
    else {
      Path p = Paths.get(jsonOrPath);
      if (Files.exists(p)) appendFromJson(read(p));
      else appendFromJson(s);
    }
    return this;
  }
  public Pipeline<I,C> addPipelineConfig(Path path) {
    ensureMutable();
    beans.put("this", this);
    beans.put("self", this);
    appendFromJson(read(path));
    return this;
  }

  // ---- Jump config ----
  public Pipeline<I,C> enableJumps(boolean on) { ensureMutable(); this.jumpsEnabled = on; return this; }
  public Pipeline<I,C> maxJumpsPerRun(int n) { ensureMutable(); this.maxJumpsPerRun = Math.max(1, n); return this; }
  public Pipeline<I,C> sleeper(Sleeper s) { ensureMutable(); this.sleeper = Objects.requireNonNull(s); return this; }
  /** Schedule next run to start at a label (one-shot). */
  public Pipeline<I,C> jumpTo(String label) { this.queuedStartLabel = Objects.requireNonNull(label); return this; }

  // ---- sealing & run ----
  @SuppressWarnings("unchecked")
  public C run(I input) throws Exception {
    String runId = newRunId();
    Metrics.RunScope scope = metrics.onPipelineStart(name, runId, queuedStartLabel);
    currentRunScope.set(scope);
    long t0 = System.nanoTime();
    Throwable lastErr = null;
    try {
      Object out;
      if (shouldUseJumpEngine()) out = runWithJumps(input, /*typedOut*/ null, scope);
      else {
        if (compiledUnary == null) sealUnaryWithMetrics();
        out = compiledUnary.run((C) input);
      }
      scope.onPipelineEnd(true, System.nanoTime()-t0, null);
      return (C) out;
    } catch (Throwable ex) {
      lastErr = ex;
      scope.onPipelineEnd(false, System.nanoTime()-t0, ex);
      if (ex instanceof Exception e) throw e;
      throw new RuntimeException(ex);
    } finally {
      currentRunScope.remove();
    }
  }

  @SuppressWarnings("unchecked")
  public <O> O run(I input, Class<O> outType) throws Exception {
    String runId = newRunId();
    Metrics.RunScope scope = metrics.onPipelineStart(name, runId, queuedStartLabel);
    currentRunScope.set(scope);
    long t0 = System.nanoTime();
    try {
      Object out;
      if (shouldUseJumpEngine()) out = runWithJumps(input, outType, scope);
      else {
        if (compiledTyped == null) sealTypedWithMetrics(outType);
        out = compiledTyped.run(input);
      }
      scope.onPipelineEnd(true, System.nanoTime()-t0, null);
      return (O) out;
    } catch (Throwable ex) {
      scope.onPipelineEnd(false, System.nanoTime()-t0, ex);
      if (ex instanceof Exception e) throw e;
      throw new RuntimeException(ex);
    } finally {
      currentRunScope.remove();
    }
  }

  private boolean shouldUseJumpEngine() { return jumpsEnabled || queuedStartLabel != null; }

  private String newRunId() {
    long r = ThreadLocalRandom.current().nextLong();
    return Long.toHexString(System.nanoTime()) + "-" + Long.toHexString(r);
  }

  public synchronized com.pipeline.core.Pipeline<C> sealUnaryWithMetrics() {
    if (compiledUnary == null) {
      var b = com.pipeline.core.Pipeline.<C>builder(name).shortCircuit(shortCircuit);
      int idx = 0;
      for (int i=0;i<pre.size();i++,idx++)  b.beforeEach(wrapForMetrics(idx, labelOf(preLabels,i), castFn(pre.get(i))));
      for (int i=0;i<main.size();i++,idx++) b.step      (wrapForMetrics(idx, labelOf(mainLabels,i), castFn(main.get(i))));
      for (int i=0;i<post.size();i++,idx++) b.afterEach (wrapForMetrics(idx, labelOf(postLabels,i), castFn(post.get(i))));
      compiledUnary = b.build();
    }
    return compiledUnary;
  }

  public synchronized <O> com.pipeline.core.Pipe<I,O> sealTypedWithMetrics(Class<O> outType) {
    if (compiledTyped == null) {
      var b = com.pipeline.core.Pipe.<I>named(name).shortCircuit(shortCircuit);
      int idx = 0;
      for (int i=0;i<pre.size();i++,idx++)  b.step(wrapForMetrics(idx, labelOf(preLabels,i), castFn(pre.get(i))));
      for (int i=0;i<main.size();i++,idx++) b.step(wrapForMetrics(idx, labelOf(mainLabels,i), castFn(main.get(i))));
      for (int i=0;i<post.size();i++,idx++) b.step(wrapForMetrics(idx, labelOf(postLabels,i), castFn(post.get(i))));
      compiledTyped = b.to(outType);
    }
    @SuppressWarnings("unchecked")
    var typed = (com.pipeline.core.Pipe<I,O>) compiledTyped;
    return typed;
  }

  private String labelOf(List<String> labels, int i) {
    String l = (labels.size() > i) ? labels.get(i) : null;
    return (l == null ? "step#" + i : l);
  }

  private <A,B> ThrowingFn<A,B> wrapForMetrics(int idx, String label, ThrowingFn<A,B> fn) {
    return a -> {
      Metrics.RunScope s = currentRunScope.get();
      long t0 = System.nanoTime();
      if (s != null) s.onStepStart(idx, label);
      try {
        B out = fn.apply(a);
        if (s != null) s.onStepEnd(idx, label, System.nanoTime()-t0, true);
        return out;
      } catch (Exception ex) {
        if (s != null) s.onStepError(idx, label, ex);
        throw ex;
      }
    };
  }

  public boolean isSealed() { return compiledUnary != null || compiledTyped != null || controlPlanBuilt; }

  public Pipeline<I,C> fork() {
    var p = new Pipeline<I,C>();
    p.name = this.name;
    p.shortCircuit = this.shortCircuit;
    p.onErrorReturn = this.onErrorReturn;
    p.pre.addAll(this.pre);    p.preLabels.addAll(this.preLabels);    p.preInTypes.addAll(this.preInTypes);
    p.main.addAll(this.main);  p.mainLabels.addAll(this.mainLabels);  p.mainInTypes.addAll(this.mainInTypes);
    p.post.addAll(this.post);  p.postLabels.addAll(this.postLabels);  p.postInTypes.addAll(this.postInTypes);
    p.beans.putAll(this.beans);
    p.metrics = this.metrics;
    p.jumpsEnabled = this.jumpsEnabled;
    p.maxJumpsPerRun = this.maxJumpsPerRun;
    p.sleeper = this.sleeper;
    return p;
  }

  // ---- Jump runner ----
  private Object runWithJumps(Object input, Class<?> typedOut, Metrics.RunScope scope) throws Exception {
    buildControlPlanIfNeeded();
    int start = 0;
    if (queuedStartLabel != null) {
      Integer idx = labelIndex.get(queuedStartLabel);
      if (idx == null) throw new IllegalArgumentException("Unknown start label: " + queuedStartLabel);
      start = idx;
      queuedStartLabel = null; // one-shot
    }
    Object value = input;
    int i = start;
    int jumps = 0;

    while (i < flatFns.size()) {
      ThrowingFn<Object,Object> fn = flatFns.get(i);
      String curLabel = labelOrIndex(i);
      long t0 = System.nanoTime();
      if (scope != null) scope.onStepStart(i, curLabel);
      try {
        value = fn.apply(value);
        if (scope != null) scope.onStepEnd(i, curLabel, System.nanoTime()-t0, true);
        i++;
      } catch (Jumps.Signal sig) {
        if (scope != null) scope.onStepEnd(i, curLabel, System.nanoTime()-t0, true);
        if (++jumps > maxJumpsPerRun) throw new IllegalStateException("Too many jumps in one run (>" + maxJumpsPerRun + ")");
        String to = sig.label();
        Integer target = labelIndex.get(to);
        if (target == null) throw new IllegalArgumentException("Unknown jump label: " + to);
        if (target < preCount) throw new IllegalArgumentException("Jump into 'pre' is not allowed: " + to);
        if (scope != null) scope.onJump(curLabel, to, sig.sleepMillis());
        long sleep = sig.sleepMillis();
        if (sleep > 0) sleeper.sleep(sleep);
        Class<?> expected = flatExpectedIn.get(target);
        if (expected != null && value != null && !expected.isInstance(value)) {
          throw new IllegalStateException("Jump type mismatch: value at '" + curLabel + "' is "
            + value.getClass().getName() + " but target '" + to + "' expects " + expected.getName());
        }
        i = target; // perform jump
      } catch (Exception ex) {
        if (scope != null) scope.onStepError(i, curLabel, ex);
        if (shortCircuit) throw ex; // abort
        // else continue with same value
        i++;
      }
    }
    // typed out post-check
    if (typedOut != null && value != null && !typedOut.isInstance(value)) {
      throw new IllegalStateException("Pipeline completed but result type " + value.getClass().getName() +
          " is not assignable to requested " + typedOut.getName());
    }
    return value;
  }

  private String labelOrIndex(int i) {
    String l = (i < flatLabels.size()) ? flatLabels.get(i) : null;
    return (l == null) ? ("step#" + i) : l;
  }

  private void buildControlPlanIfNeeded() {
    if (controlPlanBuilt) return;
    int idx = 0;
    for (int k = 0; k < pre.size(); k++, idx++) {
      flatFns.add(castFn(pre.get(k)));
      flatLabels.add(preLabels.size() > k ? preLabels.get(k) : null);
      flatExpectedIn.add(preInTypes.size() > k ? preInTypes.get(k) : null);
      String lab = flatLabels.get(idx);
      if (lab != null) labelIndex.put(lab, idx);
    }
    preCount = idx;
    for (int k = 0; k < main.size(); k++, idx++) {
      flatFns.add(castFn(main.get(k)));
      flatLabels.add(mainLabels.size() > k ? mainLabels.get(k) : null);
      flatExpectedIn.add(mainInTypes.size() > k ? mainInTypes.get(k) : null);
      String lab = flatLabels.get(idx);
      if (lab != null) {
        if (labelIndex.containsKey(lab)) throw new IllegalArgumentException("Duplicate label: " + lab);
        labelIndex.put(lab, idx);
      }
    }
    for (int k = 0; k < post.size(); k++, idx++) {
      flatFns.add(castFn(post.get(k)));
      flatLabels.add(postLabels.size() > k ? postLabels.get(k) : null);
      flatExpectedIn.add(postInTypes.size() > k ? postInTypes.get(k) : null);
      String lab = flatLabels.get(idx);
      if (lab != null) {
        if (labelIndex.containsKey(lab)) throw new IllegalArgumentException("Duplicate label: " + lab);
        labelIndex.put(lab, idx);
      }
    }
    controlPlanBuilt = true;
  }

  // ---- helpers ----
  private enum Section { PRE, MAIN, POST }

  private static boolean looksLikeJson(String s) {
    if (s == null) return false;
    String t = s.strip();
    return !t.isEmpty() && (t.charAt(0) == '{' || t.charAt(0) == '[');
  }
  private static String read(Path p) {
    try { return Files.readString(p, StandardCharsets.UTF_8); }
    catch (IOException e) { throw new RuntimeException("Failed to read " + p, e); }
  }

  private void validateNewLabel(String label) {
    if (label == null || label.isBlank()) throw new IllegalArgumentException("label must be non-empty");
    if (preLabels.contains(label) || mainLabels.contains(label) || postLabels.contains(label)) {
      throw new IllegalArgumentException("Duplicate label: " + label);
    }
  }

  @SuppressWarnings("unchecked")
  private void push(Section sec, ThrowingFn<?,?> fn, String label, Class<?> expectedIn) {
    ThrowingFn<?,?> wrapped = (label == null) ? fn : label(fn, label);
    switch (sec) {
      case PRE -> { pre.add(wrapped); preLabels.add(label); preInTypes.add(expectedIn); }
      case MAIN -> { main.add(wrapped); mainLabels.add(label); mainInTypes.add(expectedIn); }
      case POST -> { post.add(wrapped); postLabels.add(label); postInTypes.add(expectedIn); }
    }
  }

  private void appendFromJson(String json) {
    try {
      JsonNode root = M.readTree(json);
      // Beans
      if (root.has("beans")) {
        JsonNode beansNode = root.get("beans");
        if (!beansNode.isObject()) throw new IllegalArgumentException("'beans' must be an object");
        var it = beansNode.fields();
        while (it.hasNext()) {
          var e = it.next();
          String id = e.getKey();
          if ("this".equals(id) || "self".equals(id)) {
            throw new IllegalArgumentException("Bean id '" + id + "' is reserved");
          }
          beans.put(id, createBean(e.getValue()));
        }
      }

      String type = root.path("type").asText("unary");
      if (root.has("pipeline")) this.name = root.get("pipeline").asText(name);
      if (root.has("shortCircuit")) this.shortCircuit = root.get("shortCircuit").asBoolean(this.shortCircuit);

      if ("unary".equals(type)) {
        appendUnarySection(root.get("pre"),   Section.PRE);
        appendUnarySection(root.get("steps"), Section.MAIN);
        appendUnarySection(root.get("post"),  Section.POST);
      } else if ("typed".equals(type)) {
        appendTypedSection(root.get("pre"),   Section.PRE, null);
        Class<?> start = requireClass(root, "inType");
        Class<?> end   = optClass(root, "outType");
        Class<?> current = appendTypedSection(root.get("steps"), Section.MAIN, start);
        Class<?> after  = appendTypedSection(root.get("post"),  Section.POST, current);
        if (end != null && after != null && !end.equals(after)) {
          throw new IllegalArgumentException("Typed pipeline outType mismatch. Expected " + end.getName() + " but got " + after.getName());
        }
      } else {
        throw new IllegalArgumentException("Unsupported pipeline type: " + type);
      }
    } catch (IOException e) {
      throw new RuntimeException("Invalid JSON pipeline config", e);
    }
  }

  @SuppressWarnings("unchecked")
  private void appendUnarySection(JsonNode arr, Section sec) {
    if (arr == null || !arr.isArray()) return;
    for (JsonNode s : arr) {
      // Optional jumpWhen wrapper BEFORE the real step
      if (s.has("jumpWhen")) {
        prependJumpWrapper(sec, s.get("jumpWhen"), String.class);
      }

      ThrowingFn<?,?> fn;
      String label = s.path("label").asText(null);

      if (s.has("$local")) {
        JsonNode local = s.get("$local");
        if (local.isTextual()) {
          fn = (ThrowingFn<?,?>) instantiateFn(local.asText());
        } else if (local.isObject() && local.has("bean")) {
          Object bean = resolveBean(local.get("bean").asText());
          if (!(bean instanceof ThrowingFn<?,?>)) {
            throw new IllegalArgumentException("Bean does not implement ThrowingFn: " + local.get("bean").asText());
          }
          fn = (ThrowingFn<?,?>) bean;
        } else {
          throw new IllegalArgumentException("Unsupported $local spec: " + local.toString());
        }

      } else if (s.has("$prompt")) {
        String cls = req(s.get("$prompt"), "class").asText();
        fn = (ThrowingFn<?,?>) instantiateFn(cls);

      } else if (s.has("$remote")) {
        JsonNode r = s.get("$remote");
        fn = makeRemoteFn(r, String.class, String.class);

      } else if (s.has("$method")) {
        JsonNode m = s.get("$method");
        String ref = m.has("ref") ? m.get("ref").asText()
            : (req(m, "class").asText() + "#" + req(m, "name").asText());
        String target = m.path("target").asText(null);
        if (target != null && !target.isBlank()) {
          Object bean = resolveBean(target);
          fn = instanceMethodFn(bean, ref, String.class, String.class);
        } else {
          fn = staticMethodFn(ref, String.class, String.class);
        }

      } else {
        throw new IllegalArgumentException("Unsupported unary step: " + s.toString());
      }

      validateOrIgnoreNull(label);
      push(sec, fn, label, null);
    }
  }

  @SuppressWarnings("unchecked")
  private Class<?> appendTypedSection(JsonNode arr, Section sec, Class<?> start) {
    if (arr == null) return start;
    if (!arr.isArray()) throw new IllegalArgumentException("Section " + sec + " must be an array");
    Class<?> current = start;
    for (JsonNode s : arr) {
      if (s.has("jumpWhen")) {
        // For typed, we need a wrapper of (current -> current); target type check is in engine
        prependJumpWrapper(sec, s.get("jumpWhen"), current);
      }

      Class<?> in  = requireClass(s, "in");
      Class<?> out = requireClass(s, "out");
      if (current != null && !in.equals(current)) {
        throw new IllegalArgumentException("Step 'in' mismatch in " + sec + ". Expected " + current.getName() + " but got " + in.getName());
      }

      ThrowingFn<?,?> fn;
      String label = s.path("label").asText(null);

      if (s.has("$method")) {
        JsonNode m = s.get("$method");
        String ref = m.has("ref") ? m.get("ref").asText()
            : (req(m, "class").asText() + "#" + req(m, "name").asText());
        String target = m.path("target").asText(null); // "@beanId" or null
        if (target != null && !target.isBlank()) {
          Object bean = resolveBean(target);
          fn = instanceMethodFn(bean, ref, in, out);
        } else {
          fn = staticMethodFn(ref, in, out);
        }

      } else if (s.has("$local")) {
        JsonNode local = s.get("$local");
        if (local.isTextual()) {
          fn = (ThrowingFn<?,?>) instantiateFn(local.asText());
        } else if (local.isObject() && local.has("bean")) {
          Object bean = resolveBean(local.get("bean").asText());
          if (!(bean instanceof ThrowingFn<?,?>)) {
            throw new IllegalArgumentException("Bean does not implement ThrowingFn: " + local.get("bean").asText());
          }
          fn = (ThrowingFn<?,?>) bean;
        } else {
          throw new IllegalArgumentException("Unsupported $local spec: " + local.toString());
        }

      } else if (s.has("$prompt")) {
        String cls = req(s.get("$prompt"), "class").asText();
        fn = (ThrowingFn<?,?>) instantiateFn(cls);

      } else if (s.has("$remote")) {
        JsonNode r = s.get("$remote");
        fn = makeRemoteFn(r, in, out);

      } else {
        throw new IllegalArgumentException("Unsupported typed step: " + s.toString());
      }

      validateOrIgnoreNull(label);
      push(sec, fn, label, in);
      current = out;
    }
    return current;
  }

  // Build a wrapper step (value -> value) that triggers a jump based on a predicate.
  @SuppressWarnings("unchecked")
  private void prependJumpWrapper(Section sec, JsonNode jumpWhen, Class<?> inType) {
    String targetLabel = req(jumpWhen, "label").asText();
    long delayMs = jumpWhen.path("delayMillis").asLong(0L);
    // Build predicate
    ThrowingPred<Object> pred = parsePredicate(jumpWhen.get("predicate"), inType);
    // Wrapper
    ThrowingFn<Object,Object> wrapper = o -> {
      Object v = o;
      boolean fire = pred == null ? true : pred.test(v);
      if (fire) {
        if (delayMs > 0) Jumps.after(targetLabel, Duration.ofMillis(delayMs));
        else Jumps.now(targetLabel);
      }
      return v;
    };
    // Prepend wrapper to the section
    switch (sec) {
      case PRE  -> { pre.add(wrapper); preLabels.add(null); preInTypes.add(inType); }
      case MAIN -> { main.add(wrapper); mainLabels.add(null); mainInTypes.add(inType); }
      case POST -> { post.add(wrapper); postLabels.add(null); postInTypes.add(inType); }
    }
  }

  @SuppressWarnings("unchecked")
  private ThrowingPred<Object> parsePredicate(JsonNode node, Class<?> inType) {
    if (node == null || node.isNull()) return null; // no predicate -> always jump
    if (node.has("$method")) {
      JsonNode m = node.get("$method");
      String ref = m.has("ref") ? m.get("ref").asText()
          : (req(m, "class").asText() + "#" + req(m, "name").asText());
      ThrowingFn<Object, Boolean> fn = (ThrowingFn<Object, Boolean>) staticMethodFn(ref, inType, boolean.class);
      return v -> Boolean.TRUE.equals(fn.apply(v));
    } else if (node.has("$local")) {
      String cls = node.get("$local").asText();
      Object o = instantiatePredicate(cls);
      return v -> ((com.pipeline.core.ThrowingPred<Object>) o).test(v);
    } else if (node.has("$prompt")) {
      String cls = req(node.get("$prompt"), "class").asText();
      Object o = instantiatePredicate(cls);
      return v -> ((com.pipeline.core.ThrowingPred<Object>) o).test(v);
    } else {
      throw new IllegalArgumentException("Unsupported predicate spec: " + node.toString());
    }
  }

  private Object instantiatePredicate(String fqcn) {
    try {
      Class<?> c = Class.forName(fqcn);
      var ctor = c.getDeclaredConstructor();
      ctor.setAccessible(true);
      Object o = ctor.newInstance();
      if (!(o instanceof com.pipeline.core.ThrowingPred)) {
        throw new IllegalArgumentException("Class does not implement ThrowingPred: " + fqcn);
      }
      return o;
    } catch (Exception e) {
      throw new RuntimeException("Failed to instantiate predicate " + fqcn, e);
    }
  }

  // ---- Reflection helpers ----
  private static Class<?> requireClass(JsonNode n, String field) {
    if (!n.has(field)) throw new IllegalArgumentException("Missing required field: " + field);
    String fqn = n.get(field).asText();
    return loadClass(fqn);
  }
  private static Class<?> optClass(JsonNode n, String field) {
    if (!n.has(field)) return null;
    return loadClass(n.get(field).asText());
  }
  private static Class<?> loadClass(String fqn) {
    try { return Class.forName(fqn); }
    catch (ClassNotFoundException e) { throw new IllegalArgumentException("Class not found: " + fqn, e); }
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

  private static ThrowingFn<?,?> staticMethodFn(String ref, Class<?> in, Class<?> out) {
    int i = ref.lastIndexOf('#');
    if (i <= 0) throw new IllegalArgumentException("Invalid method ref: " + ref + " (expected Class#method)");
    String clsName = ref.substring(0, i);
    String method = ref.substring(i + 1);
    Class<?> cls = loadClass(clsName);
    try {
      Method m = cls.getDeclaredMethod(method, in);
      if (!Modifier.isStatic(m.getModifiers())) {
        throw new IllegalArgumentException("Method is not static: " + ref);
      }
      if (out != boolean.class && !out.isAssignableFrom(m.getReturnType())) {
        throw new IllegalArgumentException("Method return " + m.getReturnType().getName() + " not assignable to declared out " + out.getName());
      }
      m.setAccessible(true);
      return (ThrowingFn<Object,Object>) (Object inVal) -> {
        try { return m.invoke(null, inVal); }
        catch (InvocationTargetException ite) {
          Throwable cause = ite.getCause();
          if (cause instanceof Exception ex) throw ex;
          throw new RuntimeException(cause);
        }
      };
    } catch (NoSuchMethodException e) {
      throw new IllegalArgumentException("No such method: " + ref + " with parameter " + in.getName(), e);
    }
  }

  private static ThrowingFn<?,?> instanceMethodFn(Object bean, String ref, Class<?> in, Class<?> out) {
    int i = ref.lastIndexOf('#');
    if (i <= 0) throw new IllegalArgumentException("Invalid method ref: " + ref + " (expected Class#method)");
    String method = ref.substring(i + 1); // ignore class from ref; use bean.getClass()
    try {
      Method m = bean.getClass().getDeclaredMethod(method, in);
      if (out != boolean.class && !out.isAssignableFrom(m.getReturnType())) {
        throw new IllegalArgumentException("Method return " + m.getReturnType().getName() + " not assignable to declared out " + out.getName());
      }
      m.setAccessible(true);
      return (ThrowingFn<Object,Object>) (Object inVal) -> {
        try { return m.invoke(bean, inVal); }
        catch (InvocationTargetException ite) {
          Throwable cause = ite.getCause();
          if (cause instanceof Exception ex) throw ex;
          throw new RuntimeException(cause);
        }
      };
    } catch (NoSuchMethodException e) {
      throw new IllegalArgumentException("No such instance method on " + bean.getClass().getName() + ": " + method + "(" + in.getName() + ")", e);
    }
  }

  private static ThrowingFn<?,?> makeRemoteFn(JsonNode r, Class<?> inClass, Class<?> outClass) {
    var spec = new HttpStep.RemoteSpec<>();
    spec.endpoint = req(r, "endpoint").asText();
    spec.timeoutMillis = r.path("timeoutMillis").asInt(1000);
    spec.retries = r.path("retries").asInt(0);
    spec.headers = Collections.emptyMap();

    String serde = r.path("serde").asText("string");
    if ("jackson".equals(serde)) {
      spec.toJson = obj -> {
        try { return (obj == null) ? "null" : M.writeValueAsString(obj); }
        catch (Exception e) { throw new RuntimeException(e); }
      };
      spec.fromJson = body -> {
        try {
          if (outClass == String.class) return body;
          return M.readValue(body, outClass);
        } catch (Exception e) { throw new RuntimeException(e); }
      };
    } else {
      spec.toJson = obj -> (String) obj;
      spec.fromJson = body -> body;
    }

    @SuppressWarnings("unchecked")
    ThrowingFn<?,?> fn = (ThrowingFn<?,?>) HttpStep.jsonPost(spec);
    return fn;
  }

  // Label wrapper (friendly toString)
  private static <A,B> ThrowingFn<A,B> label(ThrowingFn<A,B> inner, String label) {
    return new ThrowingFn<>() {
      @Override public B apply(A a) throws Exception { return inner.apply(a); }
      @Override public String toString() { return label; }
    };
  }

  @SuppressWarnings({"rawtypes","unchecked"})
  private static <T> List<ThrowingFn<T,T>> castList(List<ThrowingFn<?,?>> list) {
    List<ThrowingFn<T,T>> out = new ArrayList<>(list.size());
    for (var fn : list) out.add((ThrowingFn) fn);
    return out;
  }
  @SuppressWarnings({"rawtypes","unchecked"})
  private static <A,B> ThrowingFn<A,B> castFn(ThrowingFn<?,?> fn) { return (ThrowingFn) fn; }
}
