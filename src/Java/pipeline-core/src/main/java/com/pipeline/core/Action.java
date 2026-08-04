package com.pipeline.core;

import java.util.Objects;
import java.util.function.UnaryOperator;

/** A normal Pipeline Services action: one context in, one context out. */
@FunctionalInterface
public interface Action<C> extends UnaryOperator<C> {
  /** Executes this Action and may propagate a checked exception to the Pipeline runner. */
  C execute(C context) throws Exception;

  /**
   * UnaryOperator compatibility. Pipeline Services invokes {@link #execute(Object)} directly so checked
   * exceptions remain visible to the canonical error policy.
   */
  @Override
  default C apply(C context) {
    try {
      return execute(context);
    } catch (RuntimeException runtimeException) {
      throw runtimeException;
    } catch (Exception exception) {
      throw new RuntimeException("Action execution failed", exception);
    }
  }

  static <C> Action<C> from(UnaryOperator<C> action) {
    Objects.requireNonNull(action, "action");
    if (action instanceof Action<?> existingAction) {
      @SuppressWarnings("unchecked") Action<C> typedAction = (Action<C>) existingAction;
      return typedAction;
    }
    return action::apply;
  }
}
