# `pipeline-api` Quickstart (Programmatic + JSON)

`pipeline-api` provides a higher-level facade (`com.pipeline.api.Pipeline<I,C>`) that can:
- Mix programmatic steps + JSON in the same pipeline
- Support labels/jumps/beans (`@this` / `@self`)
- Seal to a compiled runner on first run (when jumps are disabled)

## Unary (no type changes)

```java
import com.pipeline.api.Pipeline;
import com.pipeline.examples.steps.TextSteps;

Pipeline<String, String> p = Pipeline.<String>named("normalize_name", /*shortCircuit=*/true)
    .addAction(TextSteps::strip)
    .addAction(TextSteps::normalizeWhitespace);

System.out.println(p.run("  john   SMITH  "));
```

## Typed (type changes)
If any action changes the value type, use `run(input, Out.class)`:

```java
import com.pipeline.api.Pipeline;
import com.pipeline.examples.steps.CsvSteps;
import com.pipeline.examples.steps.JsonSteps;

var p = Pipeline.<String>named("csv_to_json", /*shortCircuit=*/true)
    .addAction(CsvSteps::parse)    // String -> List<Map<...>>
    .addAction(JsonSteps::toJson); // List<Map<...>> -> String

String out = p.run("name,age\nNektarios,49\nTheodore,7", String.class);
System.out.println(out);
```

## JSON (inline or path)

```java
import com.pipeline.api.Pipeline;

// From a JSON string (or a file path)
var p = new Pipeline<String, String>(jsonOrPath)
    .addAction(s -> s + "!");

System.out.println(p.run("hello"));
```

