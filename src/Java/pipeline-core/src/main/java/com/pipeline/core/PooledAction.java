package com.pipeline.core;

import java.util.Objects;
import java.util.function.UnaryOperator;

public final class PooledAction<C> implements StepAction<C> {
    private final ActionPoolCache.SeededPool<Object> pool;
    private final ActionInvokeStyle invokeStyle;
    private final String actionReference;

    public PooledAction(ActionPoolCache.SeededPool<Object> pool, ActionInvokeStyle invokeStyle, String actionReference) {
        this.pool = Objects.requireNonNull(pool, "pool");
        this.invokeStyle = Objects.requireNonNull(invokeStyle, "invokeStyle");
        this.actionReference = Objects.requireNonNull(actionReference, "actionReference");
    }

    @Override
    public C apply(C ctx, ActionControl<C> control) {
        Object instance = pool.borrow();
        RuntimeException actionException = null;
        try {
            return invoke(instance, ctx, control);
        } catch (RuntimeException exception) {
            actionException = exception;
            throw exception;
        } finally {
            try {
                ((ResettableAction) instance).reset();
            } catch (RuntimeException resetException) {
                if (actionException != null) {
                    actionException.addSuppressed(resetException);
                } else {
                    throw resetException;
                }
            } finally {
                pool.release(instance);
            }
        }
    }

    private C invoke(Object instance, C ctx, ActionControl<C> control) {
        if (invokeStyle == ActionInvokeStyle.UNARY_OPERATOR) {
            @SuppressWarnings("unchecked") UnaryOperator<C> unaryOperator = (UnaryOperator<C>) instance;
            return unaryOperator.apply(ctx);
        }

        @SuppressWarnings("unchecked") StepAction<C> stepAction = (StepAction<C>) instance;
        return stepAction.apply(ctx, control);
    }

    @Override
    public String toString() {
        return "PooledAction(" + actionReference + ")";
    }
}

