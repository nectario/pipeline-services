package com.pipeline.core;

import java.util.Objects;
import java.util.function.UnaryOperator;

/**
 * Small helpers for composing actions while keeping {@code Pipeline.addAction(...)} clean.
 */
public final class Actions {
    private Actions() {}

    /**
     * Wrap a unary action with an explicit reset hook so it can participate in pooled local-actions mode
     * without the underlying type implementing {@link ResettableAction}.
     */
    public static <C> UnaryOperator<C> resettable(UnaryOperator<C> action, Runnable reset) {
        Objects.requireNonNull(action, "action");
        Objects.requireNonNull(reset, "reset");
        return new ResettableUnaryOperator<>(action, reset);
    }

    /**
     * Wrap a control-aware action with an explicit reset hook so it can participate in pooled local-actions mode
     * without the underlying type implementing {@link ResettableAction}.
     */
    public static <C> StepAction<C> resettable(StepAction<C> action, Runnable reset) {
        Objects.requireNonNull(action, "action");
        Objects.requireNonNull(reset, "reset");
        return new ResettableStepAction<>(action, reset);
    }

    private static final class ResettableUnaryOperator<C> implements UnaryOperator<C>, ResettableAction {
        private final UnaryOperator<C> delegate;
        private final Runnable reset;

        private ResettableUnaryOperator(UnaryOperator<C> delegate, Runnable reset) {
            this.delegate = Objects.requireNonNull(delegate, "delegate");
            this.reset = Objects.requireNonNull(reset, "reset");
        }

        @Override
        public C apply(C input) {
            return delegate.apply(input);
        }

        @Override
        public void reset() {
            reset.run();
        }

        @Override
        public String toString() {
            return "resettable(" + delegate + ")";
        }
    }

    private static final class ResettableStepAction<C> implements StepAction<C>, ResettableAction {
        private final StepAction<C> delegate;
        private final Runnable reset;

        private ResettableStepAction(StepAction<C> delegate, Runnable reset) {
            this.delegate = Objects.requireNonNull(delegate, "delegate");
            this.reset = Objects.requireNonNull(reset, "reset");
        }

        @Override
        public C apply(C ctx, ActionControl<C> control) {
            return delegate.apply(ctx, control);
        }

        @Override
        public void reset() {
            reset.run();
        }

        @Override
        public String toString() {
            return "resettable(" + delegate + ")";
        }
    }
}

