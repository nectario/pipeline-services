# API Quickstart (Programmatic + JSON in the same class)

This add-on introduces a small `pipeline-api` module with a **zero-ceremony** class:

- `com.pipeline.api.Pipeline<T>` — programmatic unary pipelines (seal on first run), with **inline JSON** support.

## Install (module & deps)

1) Add `<module>pipeline-api</module>` to the parent `pom.xml` modules list.
2) In `pipeline-examples/pom.xml`, add:

```xml
<dependency>
  <groupId>io.github.nectario</groupId>
  <artifactId>pipeline-api</artifactId>
  <version>${project.version}</version>
</dependency>
```

## Use

```java
// Pure JSON
Pipeline<String> p1 = new Pipeline<>(Files.readString(Path.of("pipeline-examples/src/main/resources/pipelines/normalize_name.json")));
System.out.println(p1.run("  john   SMITH  "));

// Mix JSON + method refs
Pipeline<String> p2 = new Pipeline<String>(com.pipeline.examples.steps.TextSteps::strip)
    .addPipelineConfig(json)
    .addAction(com.pipeline.examples.steps.TextSteps::normalizeWhitespace);
System.out.println(p2.run("  aLiCe   deLANEY  "));
```

> `$prompt` steps are **generated at build time** by your existing prompt codegen (e.g., `CodegenMain`) and compiled as normal classes. No runtime prompt evaluation.
