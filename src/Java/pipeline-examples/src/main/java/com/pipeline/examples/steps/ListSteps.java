package com.pipeline.examples.steps;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.stream.Collectors;

import static com.pipeline.core.PipelineExecution.shortCircuit;

public final class ListSteps {
  private ListSteps() {}

  public static List<String> dedup(List<String> input) {
    return new ArrayList<>(new LinkedHashSet<>(input));
  }

  public static List<String> sortNatural(List<String> input) {
    return input.stream().sorted().collect(Collectors.toList());
  }

  public static List<String> nonEmptyOrShortCircuit(List<String> input) {
    if (input.isEmpty()) shortCircuit();
    return input;
  }
}
