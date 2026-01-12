package com.pipeline.examples;

import com.pipeline.api.Pipeline;
import com.pipeline.examples.steps.CsvSteps;
import com.pipeline.examples.steps.JsonSteps;

public final class Example03CsvToJson {
  private Example03CsvToJson() {}

  public static void run() throws Exception {
    Pipeline<String, String> pipe = Pipeline.<String>named("ex03", /*shortCircuit=*/true)
        .addAction(CsvSteps::parse)   // String -> List<Map<String,String>>
        .addAction(JsonSteps::toJson); // List<Map<...>> -> String

    String input = "name,age\nNektarios,49\nTheodore,7";
    String out = pipe.run(input, String.class);
    System.out.println("[ex03] => " + out);
  }
}
