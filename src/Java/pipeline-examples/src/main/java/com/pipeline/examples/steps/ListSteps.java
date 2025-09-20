package com.pipeline.examples.steps;

import com.pipeline.core.ShortCircuit;
import java.util.*;
import java.util.stream.Collectors;

public final class ListSteps {
  private ListSteps() {}
  public static List<String> dedup(List<String> in) throws Exception {
    return new ArrayList<>(new LinkedHashSet<>(in));
  }
  public static List<String> sortNatural(List<String> in) throws Exception {
    return in.stream().sorted().collect(Collectors.toList());
  }
  public static List<String> nonEmptyOrShortCircuit(List<String> in) throws Exception {
    return in.isEmpty() ? ShortCircuit.now(in) : in;
  }
}

