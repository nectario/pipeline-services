package com.pipeline.examples.steps;

import com.pipeline.core.ShortCircuit;

public final class TextSteps {
  private TextSteps() {}
  public static String strip(String s) throws Exception { return s == null ? "" : s.strip(); }
  public static String normalizeWhitespace(String s) throws Exception { return s.replaceAll("\\s+", " "); }
  /** Throws if the string contains emoji (simulates a validation error). */
  public static String disallowEmoji(String s) throws Exception {
    if (s.matches(".*\\p{So}.*")) throw new IllegalArgumentException("Emoji not allowed");
    return s;
  }
  /** If length exceeds 280, short-circuit the pipeline with a truncated value. */
  public static String truncateAt280(String s) throws Exception {
    return (s.length() > 280) ? ShortCircuit.now(s.substring(0, 280)) : s;
  }
  public static String upper(String s) throws Exception { return s.toUpperCase(); }
}

