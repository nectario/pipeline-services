package com.pipeline.examples;

import com.pipeline.core.Pipe;
import com.pipeline.examples.steps.CsvSteps;
import com.pipeline.examples.steps.JsonSteps;

public final class Example03_CsvToJson {
  private Example03_CsvToJson() {}

  public static void run() throws Exception {
    Pipe pipe = Pipe.<String>named("ex03")
        .step(CsvSteps::parse)
        .step(JsonSteps::toJson)
        .to(String.class);

    String input = "name,age\nNektarios,49\nTheodore,7";
    String out = (String) pipe.run(input);
    System.out.println("[ex03] => " + out);
  }
}
