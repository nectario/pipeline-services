package com.pipeline.examples;

import com.pipeline.core.Pipeline;
import com.pipeline.core.actions.MetricsOutputAction;
import com.pipeline.examples.steps.TextSteps;

public final class Example14MetricsPostAction {
  private Example14MetricsPostAction() {}

  public static void main(String[] args) {
    Pipeline<String> pipeline = new Pipeline<String>("ex14-metrics", true)
        .addAction(TextSteps::strip)
        .addAction(TextSteps::upper)
        .addPostAction(new MetricsOutputAction<>());

    String outputValue = pipeline.run("  Hello Metrics  ").context();
    System.out.println("output=" + outputValue);
  }
}
