package com.pipeline.examples.conditions;

public final class PricePredicates {
  private PricePredicates(){}
  public static boolean isEmptyData(String s) { return s == null || s.trim().isEmpty(); }
}
