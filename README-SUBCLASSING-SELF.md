# Subclassing the unified Pipeline and targeting instance methods (@this)

This PR enables domain pipelines like `BloombergRFQPipeline extends Pipeline<...>` where
all action methods live on the subclass and can be referenced from JSON via `@this`.

## What changed
- `com.pipeline.api.Pipeline` is **no longer final**.
- JSON loading registers two reserved beans: **`@this`** and **`@self`** (aliases) that refer to the Pipeline instance.
- New method: `Pipeline.addBean(String id, Object instance)` for additional collaborators.
- JSON supports **pre/steps/post**, **labels**, and **instance targets** via `$method.target`.

## Example
- `pipeline-examples/.../bloomberg/BloombergRFQPipeline.java` (subclass with instance methods)
- `pipeline-examples/.../pipelines/bloomberg_rfq.json` (typed JSON targeting `@this`)
- `pipeline-examples/.../ExampleBloombergRFQPipeline.java` (runner)

Run:
```bash
./mvnw -DskipTests clean package
./mvnw -pl pipeline-examples exec:java -Dexec.mainClass=com.pipeline.examples.ExamplesMain
```

Then call:
```java
ExampleBloombergRFQPipeline.run();
```

**Reserved ids:** `"this"` and `"self"` cannot be overridden in `beans` or via `addBean`.
