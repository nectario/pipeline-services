package com.pipeline.examples;

import com.pipeline.core.Pipe;
import com.pipeline.examples.steps.CsvSteps;
import com.pipeline.examples.steps.JsonSteps;

public final class Example03CsvToJson {
  private Example03CsvToJson() {}

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
