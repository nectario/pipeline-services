package com.pipeline.config;

import java.lang.reflect.Constructor;
import java.util.Objects;
import java.util.function.Supplier;

final class ReflectiveNoArgFactory implements Supplier<Object> {
    private final Constructor<?> constructor;
    private final String actionReference;

    ReflectiveNoArgFactory(Constructor<?> constructor, String actionReference) {
        this.constructor = Objects.requireNonNull(constructor, "constructor");
        this.actionReference = Objects.requireNonNull(actionReference, "actionReference");
    }

    @Override
    public Object get() {
        try {
            return constructor.newInstance();
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to instantiate action: " + actionReference, exception);
        }
    }
}

