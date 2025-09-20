package com.pipeline.examples;

import com.pipeline.api.Pipeline;

public final class ExampleJsonPromptOnly {
  private ExampleJsonPromptOnly() {}

  public static void run() {
    String json = """      { "pipeline":"normalize_name", "type":"unary", "shortCircuit":false,
        "steps":[
          {"$local":"com.pipeline.examples.adapters.TextStripStep"},
          {"$prompt":{"class":"com.pipeline.generated.NormalizeName",
                      "in":"java.lang.String","out":"java.lang.String",
                      "goal":"Normalize human names to Title Case while collapsing whitespace.",
                      "rules":["Trim","Collapse internal whitespace","Title-case tokens"],
                      "examples":[{"in":"  john   SMITH ","out":"John Smith"}],
                      "p50Micros":200}},
          {"$local":"com.pipeline.examples.adapters.TextNormalizeStep"}
        ] }
    """;

    Pipeline<String> p = new Pipeline<>(json);
    System.out.println("[json+prompt] -> " + p.run("   jOhN   SMITH   "));
  }
}
