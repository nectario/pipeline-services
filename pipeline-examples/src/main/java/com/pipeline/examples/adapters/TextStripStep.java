package com.pipeline.examples.adapters;

import com.pipeline.core.ThrowingFn;
import com.pipeline.examples.steps.TextSteps;

public final class TextStripStep implements ThrowingFn<String,String> {
  @Override public String apply(String in) throws Exception { return TextSteps.strip(in); }
}

