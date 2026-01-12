package com.pipeline.examples.adapters;

import com.pipeline.examples.steps.TextSteps;

import java.util.function.UnaryOperator;

public final class TextStripStep implements UnaryOperator<String> {
  @Override public String apply(String in) { return TextSteps.strip(in); }
}
