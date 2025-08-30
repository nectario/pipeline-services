## 📦 Codex PR 5: “fix: clean compile, tidy examples, exec plugin”

**Branch name:** `fix/clean-compile-and-examples`

Perform all steps **from** the repo root in your zip (i.e., the folder that has `pom.xml` and the `pipeline-*` modules).

### A) Remove committed build outputs

```bash
git rm -r --cached pipeline-core/target pipeline-config/target pipeline-remote/target pipeline-prompt/target pipeline-disruptor/target pipeline-examples/target
```

Make sure root `.gitignore` contains:

```
target/
.mvn/wrapper/maven-wrapper.jar
.idea/
.project
.classpath
.settings/
*.iml
```

*(It already does; keep as-is.)*

------



### B) Fix generics in examples (avoid raw `Pipe`)

**`pipeline-examples/src/main/java/com/pipeline/examples/Example03_CsvToJson.java`**

```java
package com.pipeline.examples;

import com.pipeline.core.Pipe;
import com.pipeline.examples.steps.CsvSteps;
import com.pipeline.examples.steps.JsonSteps;

public final class Example03_CsvToJson {
  private Example03_CsvToJson() {}

  public static void run() throws Exception {
    Pipe<String, String> pipe = Pipe.<String>named("ex03")
        .step(CsvSteps::parse)   // String -> List<Map<String,String>>
        .step(JsonSteps::toJson) // List<Map<...>> -> String
        .to(String.class);

    String input = "name,age\nNektarios,49\nTheodore,7";
    String out = pipe.run(input);
    System.out.println("[ex03] => " + out);
  }
}
```

**`pipeline-examples/src/main/java/com/pipeline/examples/Example04_FinanceOrderFlow.java`**

```java
package com.pipeline.examples;

import com.pipeline.core.Pipe;
import com.pipeline.examples.steps.FinanceSteps;

public final class Example04_FinanceOrderFlow {
  private Example04_FinanceOrderFlow() {}

  public static void run() throws Exception {
    Pipe<FinanceSteps.Tick, FinanceSteps.OrderResponse> pipe =
        Pipe.<FinanceSteps.Tick>named("ex04")
            .step(FinanceSteps::computeFeatures)
            .step(FinanceSteps::score)
            .step(FinanceSteps::decide)
            .to(FinanceSteps.OrderResponse.class);

    var tick = new FinanceSteps.Tick("AAPL", 30.0);
    var res = pipe.run(tick);
    System.out.println("[ex04] => " + res);
  }
}
```

**`pipeline-examples/src/main/java/com/pipeline/examples/Example05_TypedWithFallback.java`**

```java
package com.pipeline.examples;

import com.pipeline.core.Pipe;
import com.pipeline.examples.steps.ErrorHandlers;
import com.pipeline.examples.steps.QuoteSteps;

public final class Example05_TypedWithFallback {
  private Example05_TypedWithFallback() {}

  public static void run() throws Exception {
    Pipe<QuoteSteps.Req, QuoteSteps.Res> pipe =
        Pipe.<QuoteSteps.Req>named("ex05")
            .shortCircuit(true)
            .onErrorReturn(ErrorHandlers::quoteError)
            .step(QuoteSteps::validate)
            .step(QuoteSteps::price)
            .to(QuoteSteps.Res.class);

    var res = pipe.run(new QuoteSteps.Req("FAIL", 10));
    System.out.println("[ex05] => " + res);
  }
}
```

------

### C) Remove (or convert) the lambda example

**Option 1 (recommended):** delete the scratch class

```
git rm pipeline-examples/src/main/java/com/pipeline/examples/Main.java
```

**Option 2:** rewrite it to use method references (e.g., `TextStripStep` / `TextNormalizeStep` adapters) and keep it. Since we already have `ExamplesMain`, deletion is simplest.

------

### D) Simplify `pipeline-examples/pom.xml` exec plugin

Replace the `<build>` section of `pipeline-examples/pom.xml` with:

```xml
<build>
  <plugins>
    <plugin>
      <groupId>org.codehaus.mojo</groupId>
      <artifactId>exec-maven-plugin</artifactId>
      <executions>
        <execution>
          <id>run-examples</id>
          <phase>none</phase>
          <goals><goal>java</goal></goals>
          <configuration>
            <mainClass>com.pipeline.examples.ExamplesMain</mainClass>
          </configuration>
        </execution>
      </executions>
    </plugin>
  </plugins>
</build>
```

*(No `additionalClasspathElements`—Maven handles module classpaths through dependencies.)*

------

### E) Use the clean parent `pom.xml`

If your current root `pom.xml` shows truncated text (I saw an ellipsis in `<description>`), replace it with this complete version (matches what we discussed earlier):

```xml
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
  <modelVersion>4.0.0</modelVersion>

  <groupId>io.github.nectario</groupId>
  <artifactId>pipeline-services</artifactId>
  <version>0.1.0-SNAPSHOT</version>
  <packaging>pom</packaging>
  <name>Pipeline Services</name>
  <description>Local-first, typed pipeline framework with shortCircuit, build-time prompt-to-code steps, and optional remote adapters.</description>
  <url>https://github.com/nectario/pipeline-services</url>

  <modules>
    <module>pipeline-core</module>
    <module>pipeline-config</module>
    <module>pipeline-remote</module>
    <module>pipeline-prompt</module>
    <module>pipeline-disruptor</module>
    <module>pipeline-examples</module>
  </modules>

  <properties>
    <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
    <maven.compiler.release>21</maven.compiler.release>
    <junit.version>5.10.2</junit.version>
    <micrometer.version>1.13.0</micrometer.version>
    <jackson.version>2.17.1</jackson.version>
    <disruptor.version>3.4.4</disruptor.version>
    <slf4j.version>2.0.13</slf4j.version>
  </properties>

  <dependencyManagement>
    <dependencies>
      <dependency>
        <groupId>io.micrometer</groupId>
        <artifactId>micrometer-core</artifactId>
        <version>${micrometer.version}</version>
      </dependency>
      <dependency>
        <groupId>com.fasterxml.jackson.core</groupId>
        <artifactId>jackson-databind</artifactId>
        <version>${jackson.version}</version>
      </dependency>
      <dependency>
        <groupId>org.junit.jupiter</groupId>
        <artifactId>junit-jupiter</artifactId>
        <version>${junit.version}</version>
        <scope>test</scope>
      </dependency>
      <dependency>
        <groupId>com.lmax</groupId>
        <artifactId>disruptor</artifactId>
        <version>${disruptor.version}</version>
      </dependency>
      <dependency>
        <groupId>org.slf4j</groupId>
        <artifactId>slf4j-api</artifactId>
        <version>${slf4j.version}</version>
      </dependency>
    </dependencies>
  </dependencyManagement>

  <build>
    <pluginManagement>
      <plugins>
        <plugin>
          <groupId>org.apache.maven.plugins</groupId>
          <artifactId>maven-compiler-plugin</artifactId>
          <version>3.11.0</version>
          <configuration><release>${maven.compiler.release}</release></configuration>
        </plugin>
        <plugin>
          <groupId>org.apache.maven.plugins</groupId>
          <artifactId>maven-surefire-plugin</artifactId>
          <version>3.2.5</version>
          <configuration><useModulePath>false</useModulePath></configuration>
        </plugin>
        <plugin>
          <groupId>org.apache.maven.plugins</groupId>
          <artifactId>maven-jar-plugin</artifactId>
          <version>3.3.0</version>
        </plugin>
        <plugin>
          <groupId>org.codehaus.mojo</groupId>
          <artifactId>exec-maven-plugin</artifactId>
          <version>3.1.0</version>
        </plugin>
      </plugins>
    </pluginManagement>
  </build>
</project>
```

*(If your module POMs also contain stray `...`, replace them with the versions I shared earlier—or ask and I’ll include those verbatim, too.)*

------

### G) Build & run to verify

```bash
./mvnw -q -DskipTests clean package
./mvnw -q -pl pipeline-examples exec:java -Dexec.mainClass=com.pipeline.examples.ExamplesMain
```

You should see all examples print, e.g., `[ex01] => ...`, `[ex03] => ...`, `[ex10] disruptor processed ~50 messages`, etc.

------

## Why these changes

- Fixes the **compile-time errors** caused by stray `...` inside Java tokens.
- Cleans up **raw types** for clarity and type‑safety.
- Ensures examples adhere to your **no lambdas** preference (method refs only).
- Simplifies the **exec plugin** to normal Maven usage (no manual classpath).
- Removes committed build outputs for a clean repo.

