package com.pipeline.core;

import java.util.Objects;

record RegisteredAction<C>(String name, Action<C> action) {
  RegisteredAction {
    action = Objects.requireNonNull(action, "action");
  }

  static <C> RegisteredAction<C> of(String name, Action<C> action) {
    String normalizedName = (name == null || name.isBlank()) ? null : name.strip();
    return new RegisteredAction<>(normalizedName, action);
  }
}
