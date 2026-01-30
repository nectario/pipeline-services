package com.pipeline.config.tests;

import com.pipeline.core.ResettableAction;

import java.util.function.UnaryOperator;

public final class PooledStatefulEchoAction implements UnaryOperator<String>, ResettableAction {
    private String cachedValue;

    public PooledStatefulEchoAction() {}

    @Override
    public String apply(String input) {
        if (cachedValue != null) {
            throw new IllegalStateException("State leak: cachedValue should have been reset before reuse");
        }
        cachedValue = input;
        sleepBriefly();
        return cachedValue;
    }

    @Override
    public void reset() {
        cachedValue = null;
    }

    private static void sleepBriefly() {
        try {
            Thread.sleep(2);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(interrupted);
        }
    }
}
