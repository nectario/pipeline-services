package com.pipeline.examples;

import com.pipeline.api.Pipeline;
import com.pipeline.examples.steps.FinanceSteps;
import com.pipeline.examples.steps.FinanceSteps.*;

public final class ExampleTypedPollingJson {
  private ExampleTypedPollingJson() {}

  public static void run() throws Exception {
    String path = "pipeline-examples/src/main/resources/pipelines/await_features.json";
    var p = new Pipeline<FinanceSteps.Features, FinanceSteps.Features>(path)
        .enableJumps(true)
        .sleeper(ms -> {}); // no real sleep in example

    Score out = p.run(new Features(0.0, 1.0), Score.class);
    System.out.println("[typed-poll] -> " + out);
  }
}
