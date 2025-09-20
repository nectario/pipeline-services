package com.pipeline.examples;

import com.pipeline.api.Pipeline;
import com.pipeline.core.StepsCond;
import com.pipeline.examples.conditions.PricePredicates;
import com.pipeline.examples.steps.TextSteps;

import java.time.Duration;

public final class ExampleConditionalUnaryProgrammatic {
  private ExampleConditionalUnaryProgrammatic(){}

  public static void run() throws Exception {
    var p = Pipeline.<String,String>named("conditional_unary", false)
        .enableJumps(true)
        .sleeper(ms -> {}) // no real sleep for demo
        // If empty, jump to 'check' every 10ms; else continue to normalize
        .addAction("check", TextSteps::strip)
        .addAction("pre-check", StepsCond.jumpIf("check", PricePredicates::isEmptyData, Duration.ofMillis(10)))
        .addAction("normalize", TextSteps::normalizeWhitespace);

    System.out.println("[cond-unary] -> " + p.run("   "));
  }
}
