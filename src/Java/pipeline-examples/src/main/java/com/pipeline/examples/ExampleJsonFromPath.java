package com.pipeline.examples;

import com.pipeline.api.Pipeline;
import com.pipeline.examples.steps.TextSteps;

public final class ExampleJsonFromPath {
  private ExampleJsonFromPath() {}

  public static void run() throws Exception {
    String path = "pipeline-examples/src/main/resources/pipelines/normalize_name.json";

    Pipeline<String, String> p = new Pipeline<String, String>(path)
        .addAction((com.pipeline.core.ThrowingConsumer<String>) s -> System.out.println("[tap] = " + s))
        .addAction(TextSteps::upper);

    System.out.println("[json-path+mixed] -> " + p.run("  john  SMITH "));
  }
}
