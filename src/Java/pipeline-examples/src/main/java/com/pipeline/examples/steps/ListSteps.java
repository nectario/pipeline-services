package com.pipeline.examples.steps;

import com.pipeline.core.ActionControl;
import java.util.*;
import java.util.stream.Collectors;

public final class ListSteps {
  private ListSteps() {}
  public static List<String> dedup(List<String> in) {
    return new ArrayList<>(new LinkedHashSet<>(in));
  }
  public static List<String> sortNatural(List<String> in) {
    return in.stream().sorted().collect(Collectors.toList());
  }
  public static List<String> nonEmptyOrShortCircuit(List<String> in, ActionControl<List<String>> control) {
    if (in.isEmpty()) control.shortCircuit();
    return in;
  }
}
