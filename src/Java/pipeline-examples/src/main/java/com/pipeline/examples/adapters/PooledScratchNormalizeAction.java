package com.pipeline.examples.adapters;

import com.pipeline.core.ResettableAction;

import java.util.Objects;
import java.util.function.UnaryOperator;

/**
 * Example of a stateful action that reuses internal buffers (not thread-safe if shared).
 *
 * <p>In JSON singleton mode, configure this action as lifecycle "pooled" so each invocation borrows
 * an instance and the loader calls {@link #reset()} before returning it to the pool.
 */
public final class PooledScratchNormalizeAction implements UnaryOperator<String>, ResettableAction {
  private final StringBuilder scratch = new StringBuilder(256);

  public PooledScratchNormalizeAction() {}

  @Override
  public String apply(String input) {
    Objects.requireNonNull(input, "input");
    scratch.setLength(0);

    String trimmed = input.strip();
    boolean previousWhitespace = false;
    for (int index = 0; index < trimmed.length(); index++) {
      char nextChar = trimmed.charAt(index);
      boolean currentWhitespace = Character.isWhitespace(nextChar);
      if (currentWhitespace) {
        if (!previousWhitespace) scratch.append(' ');
      } else {
        scratch.append(nextChar);
      }
      previousWhitespace = currentWhitespace;
    }
    return scratch.toString();
  }

  @Override
  public void reset() {
    scratch.setLength(0);
  }
}
