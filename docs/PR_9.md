# PR 9 — `core/docs: honor runtime short‑circuit in recording; README polish; add tests`

**Branch name:** `feat/runtime-freeze-finalize-and-docs`

## Why this PR

- Codex implemented `RuntimePipeline` recording + `toImmutable()`, but **still records steps after a short‑circuit** (it only skips executing them). That means `freeze()` can include steps that never ran.
- README’s **Table of Contents** links point to external ChatGPT URLs; switch to **local anchors**.
- README is missing a **Maven BOM** quick‑add and the **no‑args freeze** snippet.
- Add a **JUnit test** to lock the runtime/freeze semantics.
- Clean up committed `target/` outputs.

------

## 1) RuntimePipeline: don’t record after short‑circuit

**File:** `pipeline-core/src/main/java/com/pipeline/core/RuntimePipeline.java`
 **Change:** Guard `addPreAction`, `addStep`, `addPostAction` so they **do not record or execute** when the session has ended (explicit `ShortCircuit.now(...)` or implicit error with `shortCircuit=true`). This ensures `toImmutable()`/`freeze()` always reflect **what will actually run**.

**Replace the three add\* methods with:**

```java
  /** Apply a pre action (record + execute unless ended) and return the updated value. */
  public T addPreAction(ThrowingFn<T,T> preFn) {
    if (ended) return current;           // <-- do not record after short-circuit
    pre.add(preFn);
    return apply(preFn, "pre" + preIdx++);
  }

  /** Apply a main step (record + execute unless ended) and return the updated value. */
  public T addStep(ThrowingFn<T,T> stepFn) {
    if (ended) return current;           // <-- do not record after short-circuit
    main.add(stepFn);
    return apply(stepFn, "s" + stepIdx++);
  }

  /** Apply a post action (record + execute unless ended) and return the updated value. */
  public T addPostAction(ThrowingFn<T,T> postFn) {
    if (ended) return current;           // <-- do not record after short-circuit
    post.add(postFn);
    return apply(postFn, "post" + postIdx++);
  }
```

> Rationale: The current code calls `pre.add(...)` / `main.add(...)` / `post.add(...)` **before** the ended check (which happens inside `apply`). That records steps even though they won’t run in this session and will be included when freezing—surprising for users and not what we discussed.

*(Keep the rest of the class as‑is. Your `toImmutable()` and `freeze()` are already present; `freezeAndClear()` is optional—we can add it later if you want.)*

------

## 2) Add tests for runtime freeze & recording

**New file:** `pipeline-core/src/test/java/com/pipeline/core/RuntimePipelineFreezeTest.java`

```java
package com.pipeline.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

final class RuntimePipelineFreezeTest {

  static String strip(String s) { return s == null ? "" : s.strip(); }
  static String upper(String s) { return s.toUpperCase(); }
  static String scToX(String s) { return ShortCircuit.now("X"); }

  @Test
  void freezeBuildsEquivalentPipeline() {
    var rt = new RuntimePipeline<>("t", false, "  hello  ");
    rt.addPreAction(RuntimePipelineFreezeTest::strip);
    rt.addStep(RuntimePipelineFreezeTest::upper);

    assertEquals("HELLO", rt.value());

    var frozen = rt.toImmutable();
    assertEquals("HELLO", frozen.run("  hello  "));
  }

  @Test
  void afterShortCircuitAddsAreIgnoredUntilReset() {
    var rt = new RuntimePipeline<>("t", false, "abc");
    rt.addStep(RuntimePipelineFreezeTest::scToX);
    assertEquals("X", rt.value());
    assertEquals(1, rt.recordedStepCount());

    // These should be NO-OPs (not recorded and not executed)
    rt.addStep(RuntimePipelineFreezeTest::upper);
    rt.addPostAction(RuntimePipelineFreezeTest::strip);
    assertEquals(1, rt.recordedStepCount());
    assertEquals(0, rt.recordedPostCount());

    // After reset, recording resumes
    rt.reset("again");
    rt.addStep(RuntimePipelineFreezeTest::upper);
    assertEquals(2, rt.recordedStepCount());
  }
}
```

------

## 3) README.md — fix TOC links, add BOM, document no‑arg freeze

**File:** `README.md`

### A. Replace the **Contents** block with local anchors

```markdown
## Contents
- [Why](#why)
- [Features at a glance](#features-at-a-glance)
- [Modules](#modules)
- [Install & build](#install--build)
- [Quick start](#quick-start)
  - [Unary pipelines with `Pipeline<T>`](#unary-pipelines-with-pipelinet)
  - [Typed pipelines with `Pipe<I,O>`](#typed-pipelines-with-pipeio)
  - [Imperative runtime style with `RuntimePipeline<T>`](#imperative-runtime-style-with-runtimepipelinet)
  - [Per‑pipeline JSON config (optional)](#per-pipeline-json-config-optional)
  - [HTTP remote step](#http-remote-step)
- [Short‑circuit semantics](#shortcircuit-semantics)
- [Prompt‑generated steps (scaffold)](#prompt-generated-steps-scaffold)
- [Metrics](#metrics)
- [Examples](#examples)
- [Roadmap](#roadmap)
- [Contributing](#contributing)
- [License](#license)
```

### B. Add a **Maven BOM** snippet to “Install & build”

Under *Install & build*, add:

~~~markdown
**Using the BOM (when published to Maven Central)**
```xml
<dependencyManagement>
  <dependencies>
    <dependency>
      <groupId>io.github.nectario</groupId>
      <artifactId>pipeline-bom</artifactId>
      <version>0.1.0</version>
      <type>pom</type>
      <scope>import</scope>
    </dependency>
  </dependencies>
</dependencyManagement>

<dependencies>
  <dependency>
    <groupId>io.github.nectario</groupId>
    <artifactId>pipeline-core</artifactId>
  </dependency>
</dependencies>
*(Keep the existing `mvnw` build instructions.)*

### C. Document **no‑args freeze** in the Runtime section

Replace the freeze snippet with:

```markdown
// Freeze the recorded steps into an immutable pipeline
Pipeline<String> frozen = rt.toImmutable();   // or rt.freeze()
~~~

And add:

> After a short‑circuit in a session, further `add*` calls are **ignored and not recorded** until `reset(...)`, so `freeze()` always reflects the steps that actually run.

------

## 4) Clean repo: remove committed build outputs

Run once so the repo is clean (you already have `target/` in `.gitignore`):

```bash
git rm -r --cached pipeline-core/target pipeline-config/target pipeline-remote/target \
  pipeline-prompt/target pipeline-disruptor/target pipeline-examples/target
```

------

## 5) Verify locally

```bash
./mvnw -q -DskipTests clean package
./mvnw -q -pl pipeline-core -Dtest=RuntimePipelineFreezeTest test
./mvnw -q -pl pipeline-examples exec:java -Dexec.mainClass=com.pipeline.examples.ExamplesMain
```

You should see the three runtime examples plus the rest of the suite print as before.

------

## Commit message

```
core/docs: honor runtime short‑circuit in recording; README polish; add tests

- RuntimePipeline: do not record add* after short‑circuit; freeze() now always matches executed steps
- Add RuntimePipelineFreezeTest (JUnit 5) to lock semantics
- README: fix Table of Contents to local anchors; add Maven BOM snippet; document no‑args freeze
- Clean committed target/ outputs
```

