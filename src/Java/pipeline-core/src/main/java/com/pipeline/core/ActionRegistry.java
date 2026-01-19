package com.pipeline.core;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.UnaryOperator;

public final class ActionRegistry<C> {
    private final Map<String, UnaryOperator<C>> unaryActions = new ConcurrentHashMap<>();
    private final Map<String, StepAction<C>> stepActions = new ConcurrentHashMap<>();

    public void registerUnary(String name, UnaryOperator<C> action) {
        unaryActions.put(Objects.requireNonNull(name, "name"), Objects.requireNonNull(action, "action"));
    }

    public void registerAction(String name, StepAction<C> action) {
        stepActions.put(Objects.requireNonNull(name, "name"), Objects.requireNonNull(action, "action"));
    }

    public boolean hasUnary(String name) { return unaryActions.containsKey(name); }

    public boolean hasAction(String name) { return stepActions.containsKey(name); }

    public UnaryOperator<C> getUnary(String name) {
        UnaryOperator<C> action = unaryActions.get(name);
        if (action == null) throw new IllegalArgumentException("Unknown unary action: " + name);
        return action;
    }

    public StepAction<C> getAction(String name) {
        StepAction<C> action = stepActions.get(name);
        if (action == null) throw new IllegalArgumentException("Unknown step action: " + name);
        return action;
    }
}

