# ✅ Codex Task: Rename modules + migrate Gradle → Maven

## Goals

- Rename `ps-*` modules to non-abbreviated names.
- Keep Java packages as-is (`com.pipeline.*`) for now (no mass refactor).
- Replace Gradle with Maven (multi-module, Java 21).
- Make examples runnable via `mvnw`.

------

## 1) File/Folder operations

From repo root:

1. **Rename modules** (move out of `ps/` to the root):

   - `ps/ps-core` → `pipeline-core`
   - `ps/ps-config` → `pipeline-config`
   - `ps/ps-remote` → `pipeline-remote`
   - `ps/ps-prompt` → `pipeline-prompt`
   - `ps/ps-disruptor` → `pipeline-disruptor`
   - `ps/ps-examples` → `pipeline-examples`
   - Remove the now-empty `ps/` directory.

2. **Delete Gradle build files**

   - Delete root `build.gradle`, `settings.gradle`, and the `buildSrc/` folder (and any `*.gradle*` in module dirs).

3. **Add Maven Wrapper**

   - If Maven is installed: run `mvn -N -q io.takari:maven:wrapper` in repo root to create `mvnw`/`mvnw.cmd` and `.mvn/`.
   - If you can’t run commands, generate the standard Maven Wrapper files in the repo root.

4. **.gitignore**

   - Ensure root `.gitignore` contains Maven targets and IDE noise:

     ```
     target/
     .mvn/wrapper/maven-wrapper.jar
     .idea/
     .project
     .classpath
     .settings/
     *.iml
     ```

------

## 2) Create Maven POMs

### 2.1 Root `pom.xml` (parent/aggregator)

Create `pom.xml` at repo root:

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
  <description>Local-first, typed pipeline framework with shortCircuit, prompt-to-code, and optional remote adapters.</description>
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

    <!-- deps -->
    <junit.version>5.10.2</junit.version>
    <micrometer.version>1.13.0</micrometer.version>
    <jackson.version>2.17.1</jackson.version>
    <disruptor.version>3.4.4</disruptor.version>
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
    </dependencies>
  </dependencyManagement>

  <build>
    <pluginManagement>
      <plugins>
        <plugin>
          <groupId>org.apache.maven.plugins</groupId>
          <artifactId>maven-compiler-plugin</artifactId>
          <version>3.11.0</version>
          <configuration>
            <release>${maven.compiler.release}</release>
          </configuration>
        </plugin>
        <plugin>
          <groupId>org.apache.maven.plugins</groupId>
          <artifactId>maven-surefire-plugin</artifactId>
          <version>3.2.5</version>
          <configuration>
            <useModulePath>false</useModulePath>
          </configuration>
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

> Keep `groupId` if you prefer something else; this matches your GitHub namespace.

------

### 2.2 `pipeline-core/pom.xml`

```xml
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
  <modelVersion>4.0.0</modelVersion>
  <parent>
    <groupId>io.github.nectario</groupId>
    <artifactId>pipeline-services</artifactId>
    <version>0.1.0-SNAPSHOT</version>
  </parent>
  <artifactId>pipeline-core</artifactId>
  <name>pipeline-core</name>
  <dependencies>
    <dependency>
      <groupId>io.micrometer</groupId>
      <artifactId>micrometer-core</artifactId>
    </dependency>
    <dependency>
      <groupId>org.junit.jupiter</groupId>
      <artifactId>junit-jupiter</artifactId>
      <scope>test</scope>
    </dependency>
  </dependencies>
  <build>
    <plugins>
      <plugin>
        <groupId>org.apache.maven.plugins</groupId>
        <artifactId>maven-jar-plugin</artifactId>
        <configuration>
          <archive>
            <manifestEntries>
              <Automatic-Module-Name>com.pipeline.core</Automatic-Module-Name>
            </manifestEntries>
          </archive>
        </configuration>
      </plugin>
    </plugins>
  </build>
</project>
```

### 2.3 `pipeline-config/pom.xml`

```xml
<project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
  <modelVersion>4.0.0</modelVersion>
  <parent>
    <groupId>io.github.nectario</groupId>
    <artifactId>pipeline-services</artifactId>
    <version>0.1.0-SNAPSHOT</version>
  </parent>
  <artifactId>pipeline-config</artifactId>
  <name>pipeline-config</name>
  <dependencies>
    <dependency>
      <groupId>io.github.nectario</groupId>
      <artifactId>pipeline-core</artifactId>
      <version>${project.version}</version>
    </dependency>
    <dependency>
      <groupId>com.fasterxml.jackson.core</groupId>
      <artifactId>jackson-databind</artifactId>
    </dependency>
    <dependency>
      <groupId>org.junit.jupiter</groupId>
      <artifactId>junit-jupiter</artifactId>
      <scope>test</scope>
    </dependency>
  </dependencies>
</project>
```

### 2.4 `pipeline-remote/pom.xml`

```xml
<project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
  <modelVersion>4.0.0</modelVersion>
  <parent>
    <groupId>io.github.nectario</groupId>
    <artifactId>pipeline-services</artifactId>
    <version>0.1.0-SNAPSHOT</version>
  </parent>
  <artifactId>pipeline-remote</artifactId>
  <name>pipeline-remote</name>
  <dependencies>
    <dependency>
      <groupId>io.github.nectario</groupId>
      <artifactId>pipeline-core</artifactId>
      <version>${project.version}</version>
    </dependency>
    <dependency>
      <groupId>com.fasterxml.jackson.core</groupId>
      <artifactId>jackson-databind</artifactId>
    </dependency>
    <dependency>
      <groupId>org.junit.jupiter</groupId>
      <artifactId>junit-jupiter</artifactId>
      <scope>test</scope>
    </dependency>
  </dependencies>
</project>
```

### 2.5 `pipeline-prompt/pom.xml`

> This module holds the `Prompt` builder **and** a tiny generator entrypoint we run during `generate-sources`. Codex: create `com.pipeline.prompt.CodegenMain` if it doesn’t exist; it should scan `pipeline-config/pipelines/*.json` and emit generated sources into `${project.build.directory}/generated-sources/prompt`.

```xml

<project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <parent>
        <groupId>io.github.nectario</groupId>
        <artifactId>pipeline-services</artifactId>
        <version>0.1.0-SNAPSHOT</version>
    </parent>
    <artifactId>pipeline-prompt</artifactId>
    <name>pipeline-prompt</name>
    <dependencies>
        <dependency>
            <groupId>io.github.nectario</groupId>
            <artifactId>pipeline-core</artifactId>
            <version>${project.version}</version>
        </dependency>
        <dependency>
            <groupId>com.fasterxml.jackson.core</groupId>
            <artifactId>jackson-databind</artifactId>
        </dependency>
        <dependency>
            <groupId>org.junit.jupiter</groupId>
            <artifactId>junit-jupiter</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>
    <build>
        <plugins>
            <plugin>
                <groupId>org.codehaus.mojo</groupId>
                <artifactId>exec-maven-plugin</artifactId>
                <executions>
                    <execution>
                        <id>generate-prompt-sources</id>
                        <phase>generate-sources</phase>
                        <goals>
                            <goal>java</goal>
                        </goals>
                        <configuration>
                            <mainClass>com.pipeline.prompt.CodegenMain</mainClass>
                            <arguments>
                                <argument>${project.basedir}/../pipeline-config/pipelines</argument>
                                <argument>${project.build.directory}/generated-sources/prompt</argument>
                            </arguments>
                        </configuration>
                    </execution>
                </executions>
            </plugin>
        </plugins>
    </build>
</project>
```

### 2.6 `pipeline-disruptor/pom.xml`

```xml
<project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
  <modelVersion>4.0.0</modelVersion>
  <parent>
    <groupId>io.github.nectario</groupId>
    <artifactId>pipeline-services</artifactId>
    <version>0.1.0-SNAPSHOT</version>
  </parent>
  <artifactId>pipeline-disruptor</artifactId>
  <name>pipeline-disruptor</name>
  <dependencies>
    <dependency>
      <groupId>io.github.nectario</groupId>
      <artifactId>pipeline-core</artifactId>
      <version>${project.version}</version>
    </dependency>
    <dependency>
      <groupId>com.lmax</groupId>
      <artifactId>disruptor</artifactId>
    </dependency>
    <dependency>
      <groupId>org.junit.jupiter</groupId>
      <artifactId>junit-jupiter</artifactId>
      <scope>test</scope>
    </dependency>
  </dependencies>
</project>
```

### 2.7 `pipeline-examples/pom.xml`

```xml

<project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <parent>
        <groupId>io.github.nectario</groupId>
        <artifactId>pipeline-services</artifactId>
        <version>0.1.0-SNAPSHOT</version>
    </parent>
    <artifactId>pipeline-examples</artifactId>
    <name>pipeline-examples</name>
    <dependencies>
        <dependency>
            <groupId>io.github.nectario</groupId>
            <artifactId>pipeline-core</artifactId>
            <version>${project.version}</version>
        </dependency>
        <dependency>
            <groupId>io.github.nectario</groupId>
            <artifactId>pipeline-config</artifactId>
            <version>${project.version}</version>
        </dependency>
        <dependency>
            <groupId>io.github.nectario</groupId>
            <artifactId>pipeline-remote</artifactId>
            <version>${project.version}</version>
        </dependency>
        <dependency>
            <groupId>io.github.nectario</groupId>
            <artifactId>pipeline-prompt</artifactId>
            <version>${project.version}</version>
        </dependency>
        <dependency>
            <groupId>io.github.nectario</groupId>
            <artifactId>pipeline-disruptor</artifactId>
            <version>${project.version}</version>
            <optional>true</optional>
        </dependency>
    </dependencies>

    <!-- (Optional) quick run for the demo main -->
    <build>
        <plugins>
            <plugin>
                <groupId>org.codehaus.mojo</groupId>
                <artifactId>exec-maven-plugin</artifactId>
                <executions>
                    <execution>
                        <id>run-demo</id>
                        <phase>none</phase>
                        <goals>
                            <goal>java</goal>
                        </goals>
                        <configuration>
                            <mainClass>com.pipeline.examples.Main</mainClass>
                        </configuration>
                    </execution>
                </executions>
            </plugin>
        </plugins>
    </build>
</project>
```

------

## 3) Minimal code changes (if needed)

- **Do not** change Java package names (`com.pipeline.core.*`, etc.). Module rename does not require package changes.
- In **pipeline-prompt**, add a tiny `CodegenMain` if missing:

```java
package com.pipeline.prompt;

import java.nio.file.*;

import com.fasterxml.jackson.databind.*;

public final class CodegenMain {
    public static void main(String[] args) throws Exception {
        Path in = Paths.get(args[0]);
        Path out = Paths.get(args[1]);
        Files.createDirectories(out);
        // TODO: scan JSON specs + Prompt.step(...) markers and emit Java sources into 'out'
        // For now, no-op or generate a placeholder to prove wiring works.
    }
}
```

- Ensure `pipeline-config/pipelines/` exists (even empty) so the generator runs cleanly.

------

## 4) Verify build & run

From repo root:

```bash
./mvnw -q -DskipTests package      # whole multi-module build
# (optional) run examples if exec plugin is wired:
./mvnw -q -pl pipeline-examples exec:java -Dexec.mainClass=com.pipeline.examples.Main
```

Windows PowerShell: use `mvnw.cmd`.

------

## 5) Commit

```bash
git add -A
git commit -m "build: rename modules and migrate to Maven (Java 21)"
git push -u origin main
```

------

## Definition of Done

- `./mvnw -q -DskipTests package` succeeds from the repo root.
- `pipeline-examples` compiles and can run a demo main.
- No Gradle files remain.
- Module names are `pipeline-*` at the root.
- Short-circuit semantics unchanged.

