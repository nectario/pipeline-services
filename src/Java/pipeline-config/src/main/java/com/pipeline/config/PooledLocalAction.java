package com.pipeline.config;

import com.pipeline.core.ActionPool;
import com.pipeline.core.ResettableAction;
import com.pipeline.core.StepAction;
import com.pipeline.core.StepControl;

import java.util.Objects;
import java.util.function.UnaryOperator;

public final class PooledLocalAction<C> implements StepAction<C> {
    private final ActionPool<Object> pool;
    private final LocalActionInvokeStyle invokeStyle;
    private final String actionReference;

    public PooledLocalAction(ActionPool<Object> pool, LocalActionInvokeStyle invokeStyle, String actionReference) {
        this.pool = Objects.requireNonNull(pool, "pool");
        this.invokeStyle = Objects.requireNonNull(invokeStyle, "invokeStyle");
        this.actionReference = Objects.requireNonNull(actionReference, "actionReference");
    }

    @Override
    public C apply(C ctx, StepControl<C> control) {
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

    private C invoke(Object instance, C ctx, StepControl<C> control) {
        if (invokeStyle == LocalActionInvokeStyle.UNARY_OPERATOR) {
            @SuppressWarnings("unchecked") UnaryOperator<C> unaryOperator = (UnaryOperator<C>) instance;
            return unaryOperator.apply(ctx);
        }

        @SuppressWarnings("unchecked") StepAction<C> stepAction = (StepAction<C>) instance;
        return stepAction.apply(ctx, control);
    }

    @Override
    public String toString() {
        return "PooledLocalAction(" + actionReference + ", max=" + pool.max() + ")";
    }
}
