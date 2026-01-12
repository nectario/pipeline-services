package com.pipeline.examples.steps;

public final class TextSteps {
  private TextSteps() {}
  public static String strip(String s) { return s == null ? "" : s.strip(); }
  public static String normalizeWhitespace(String s) { return s.replaceAll("\\s+", " "); }
  /** Throws if the string contains emoji (simulates a validation error). */
  public static String disallowEmoji(String s) {
    if (s.matches(".*\\p{So}.*")) throw new IllegalArgumentException("Emoji not allowed");
    return s;
  }
  /** Truncate at 280 chars (use a StepAction if you want to short-circuit). */
  public static String truncateAt280(String s) { return (s.length() > 280) ? s.substring(0, 280) : s; }
  public static String upper(String s) { return s.toUpperCase(); }
}
