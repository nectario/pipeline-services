package com.pipeline.core;

import java.util.Objects;
import java.util.function.UnaryOperator;

/** A normal Pipeline Services action: one context in, one context out. */
@FunctionalInterface
public interface Action<C> extends UnaryOperator<C> {
  @Override
  C apply(C context);

  static <C> Action<C> from(UnaryOperator<C> action) {
    Objects.requireNonNull(action, "action");
    if (action instanceof Action<?> existingAction) {
      @SuppressWarnings("unchecked") Action<C> typedAction = (Action<C>) existingAction;
      return typedAction;
    }
    return action::apply;
  }
}
