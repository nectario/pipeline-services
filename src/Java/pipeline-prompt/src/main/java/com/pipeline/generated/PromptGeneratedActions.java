package com.pipeline.generated;

import com.pipeline.core.ActionRegistry;

public final class PromptGeneratedActions {
  private PromptGeneratedActions() {}

  public static void register(ActionRegistry<String> registry) {
    if (registry == null) throw new IllegalArgumentException("registry is required");
    registry.registerUnary("prompt:normalize_name", new NormalizeNameAction());
  }
}
