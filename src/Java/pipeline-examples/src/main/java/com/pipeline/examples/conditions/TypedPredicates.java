package com.pipeline.examples.conditions;

import com.pipeline.examples.steps.FinanceSteps;

import java.util.concurrent.atomic.AtomicInteger;

public final class TypedPredicates {
  private static final AtomicInteger cnt = new AtomicInteger();
  private TypedPredicates(){}

  // Demo: say we need to await twice before scoring
  public static boolean needsAwait(FinanceSteps.Features f) {
    return cnt.incrementAndGet() <= 2;
  }
}
