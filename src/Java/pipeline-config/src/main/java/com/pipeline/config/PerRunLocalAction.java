package com.pipeline.config;

import com.pipeline.core.StepAction;
import com.pipeline.core.ActionControl;

import java.lang.reflect.Constructor;
import java.util.Objects;
import java.util.function.UnaryOperator;

public final class PerRunLocalAction<C> implements StepAction<C> {
    private final Constructor<?> constructor;
    private final LocalActionInvokeStyle invokeStyle;
    private final String actionReference;

    public PerRunLocalAction(Constructor<?> constructor, LocalActionInvokeStyle invokeStyle, String actionReference) {
        this.constructor = Objects.requireNonNull(constructor, "constructor");
        this.invokeStyle = Objects.requireNonNull(invokeStyle, "invokeStyle");
        this.actionReference = Objects.requireNonNull(actionReference, "actionReference");
    }

    @Override
    public C apply(C ctx, ActionControl<C> control) {
        Object instance = newInstance();
        try {
            return invoke(instance, ctx, control);
        } finally {
            closeIfNeeded(instance);
        }
    }

    private Object newInstance() {
        try {
            return constructor.newInstance();
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to instantiate action: " + actionReference, exception);
        }
    }

    private C invoke(Object instance, C ctx, ActionControl<C> control) {
        if (invokeStyle == LocalActionInvokeStyle.UNARY_OPERATOR) {
            @SuppressWarnings("unchecked") UnaryOperator<C> unaryOperator = (UnaryOperator<C>) instance;
            return unaryOperator.apply(ctx);
        }

        @SuppressWarnings("unchecked") StepAction<C> stepAction = (StepAction<C>) instance;
        return stepAction.apply(ctx, control);
    }

    private static void closeIfNeeded(Object instance) {
        if (instance instanceof AutoCloseable closeable) {
            try {
                closeable.close();
            } catch (Exception exception) {
                throw new IllegalStateException("Failed to close action instance", exception);
            }
        }
    }

    @Override
    public String toString() {
        return "PerRunLocalAction(" + actionReference + ")";
    }
}
