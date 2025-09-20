package com.pipeline.examples;

import com.pipeline.api.Pipeline;
import com.pipeline.examples.steps.TextSteps;
import com.pipeline.examples.steps.PolicySteps;

public final class ExampleJsonPromptMixed {
  private ExampleJsonPromptMixed() {}

  public static void run() {
    Pipeline<String> p = new Pipeline<String>()
        .shortCircuit(false)
        .before(PolicySteps::rateLimit)
        .addAction(TextSteps::strip);

    String json = """      {"pipeline":"normalize_name","type":"unary","shortCircuit":false,
       "steps":[
         {"$prompt":{"class":"com.pipeline.generated.NormalizeName",
                     "in":"java.lang.String","out":"java.lang.String",
                     "goal":"Normalize names","rules":["Trim","Collapse spaces","Title-case"]}},
         {"$local":"com.pipeline.examples.adapters.TextNormalizeStep"}
       ]}
    """;

    p.addPipelineConfig(json)
     .addAction(TextSteps::truncateAt280)
     .after(PolicySteps::audit);

    System.out.println("[mixed] -> " + p.run("   aLiCe   deLANEY   "));
  }
}
