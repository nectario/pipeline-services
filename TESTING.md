# Unit tests for jump engine

This adds **JUnit 5** tests in `pipeline-api`:

- `JumpEngineUnaryTest` — self-looping unary step stops after 3 attempts.
- `JumpEngineTypedTest` — jumping to a mismatched typed target throws a clear error.
- `JumpEngineGuardsTest` — prevents jumping into `pre`; trips `maxJumpsPerRun`.
- `JumpToStartTest` — `jumpTo(label)` starts a run from a labeled step.

Make sure your parent/child `pom.xml` files have JUnit 5:

```xml
<dependency>
  <groupId>org.junit.jupiter</groupId>
  <artifactId>junit-jupiter</artifactId>
  <version>5.10.2</version>
  <scope>test</scope>
</dependency>

<build>
  <plugins>
    <plugin>
      <groupId>org.apache.maven.plugins</groupId>
      <artifactId>maven-surefire-plugin</artifactId>
      <version>3.2.5</version>
      <configuration>
        <useModulePath>false</useModulePath>
      </configuration>
    </plugin>
  </plugins>
</build>
```

Run:

```bash
./mvnw -pl pipeline-api test
```
