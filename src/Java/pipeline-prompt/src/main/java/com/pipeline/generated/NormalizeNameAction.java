package com.pipeline.generated;

import java.util.function.UnaryOperator;
import java.util.regex.Pattern;

public final class NormalizeNameAction implements UnaryOperator<String> {
  private static final Pattern WHITESPACE = Pattern.compile("\\s+");

  @Override public String apply(String textValue) {
    String outputValue = textValue;
    outputValue = WHITESPACE.matcher(outputValue).replaceAll(" ");
    outputValue = outputValue.trim();
    String[] tokens = outputValue.split(" ");
    StringBuilder builder = new StringBuilder(outputValue.length());
    for (String token : tokens) {
      if (token == null || token.isEmpty()) continue;
      if (builder.length() > 0) builder.append(' ');
      String lowerValue = token.toLowerCase();
      builder.append(Character.toUpperCase(lowerValue.charAt(0)));
      if (lowerValue.length() > 1) builder.append(lowerValue.substring(1));
    }
    outputValue = builder.toString();
    return outputValue;
  }
}
