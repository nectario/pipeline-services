package com.pipeline.core;

import com.pipeline.metrics.Metrics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.UnaryOperator;

public final class Pipeline<C> {
    private static final Logger log = LoggerFactory.getLogger(Pipeline.class);

    private final String name;
    private final boolean shortCircuitOnException;
    private volatile BiFunction<C, PipelineError, C> onError = (ctx, err) -> ctx;

    private final List<RegisteredAction<C>> preActions = new ArrayList<>();
    private final List<RegisteredAction<C>> actions = new ArrayList<>();
    private final List<RegisteredAction<C>> postActions = new ArrayList<>();

    public Pipeline(String name) {
        this(name, true);
    }

    public Pipeline(String name, boolean shortCircuitOnException) {
        this.name = Objects.requireNonNull(name, "name");
        this.shortCircuitOnException = shortCircuitOnException;
    }

    @SafeVarargs
    public static <C> Pipeline<C> build(String name, boolean shortCircuitOnException, StepAction<C>... actions) {
        var p = new Pipeline<C>(name, shortCircuitOnException);
        if (actions != null) for (var a : actions) p.addAction(a);
        return p;
    }

    @SafeVarargs
    public static <C> Pipeline<C> build(String name, boolean shortCircuitOnException, UnaryOperator<C>... actions) {
        var p = new Pipeline<C>(name, shortCircuitOnException);
        if (actions != null) for (var a : actions) p.addAction(a);
        return p;
    }

    public static <C> Builder<C> builder(String name) {
        return new Builder<>(name);
    }

    public static final class Builder<C> {
        private final String name;
        private boolean shortCircuitOnException = true;
        private BiFunction<C, PipelineError, C> onError = (ctx, err) -> ctx;
        private final List<RegisteredAction<C>> pre = new ArrayList<>();
        private final List<RegisteredAction<C>> main = new ArrayList<>();
        private final List<RegisteredAction<C>> post = new ArrayList<>();

        public Builder(String name) { this.name = name; }

        public Builder<C> shortCircuitOnException(boolean b) { this.shortCircuitOnException = b; return this; }
        public Builder<C> shortCircuit(boolean b) { return shortCircuitOnException(b); } // legacy alias

        public Builder<C> onError(BiFunction<C, PipelineError, C> handler) {
            this.onError = (handler == null) ? ((ctx, err) -> ctx) : handler;
            return this;
        }

        public Builder<C> addPreAction(String name, StepAction<C> action) {
            pre.add(RegisteredAction.named(name, action));
            return this;
        }
        public Builder<C> addAction(String name, StepAction<C> action) {
            main.add(RegisteredAction.named(name, action));
            return this;
        }
        public Builder<C> addPostAction(String name, StepAction<C> action) {
            post.add(RegisteredAction.named(name, action));
            return this;
        }

        public Builder<C> addPreAction(StepAction<C> action) { return addPreAction(null, action); }
        public Builder<C> addAction(StepAction<C> action) { return addAction(null, action); }
        public Builder<C> addPostAction(StepAction<C> action) { return addPostAction(null, action); }

        public Builder<C> addPreAction(UnaryOperator<C> fn) { return addPreAction(null, fn); }
        public Builder<C> addAction(UnaryOperator<C> fn) { return addAction(null, fn); }
        public Builder<C> addPostAction(UnaryOperator<C> fn) { return addPostAction(null, fn); }

        public Builder<C> addPreAction(String name, UnaryOperator<C> fn) { return addPreAction(name, adapt(fn)); }
        public Builder<C> addAction(String name, UnaryOperator<C> fn) { return addAction(name, adapt(fn)); }
        public Builder<C> addPostAction(String name, UnaryOperator<C> fn) { return addPostAction(name, adapt(fn)); }

        public Pipeline<C> build() {
            Pipeline<C> p = new Pipeline<>(name, shortCircuitOnException);
            p.onError(onError);
            for (var a : pre) p.preActions.add(a);
            for (var a : main) p.actions.add(a);
            for (var a : post) p.postActions.add(a);
            return p;
        }

    }

    public Pipeline<C> onError(BiFunction<C, PipelineError, C> handler) {
        this.onError = (handler == null) ? ((ctx, err) -> ctx) : handler;
        return this;
    }

    public Pipeline<C> addPreAction(StepAction<C> action) { return addPreAction(null, action); }
    public Pipeline<C> addAction(StepAction<C> action) { return addAction(null, action); }
    public Pipeline<C> addPostAction(StepAction<C> action) { return addPostAction(null, action); }

    public Pipeline<C> addPreAction(UnaryOperator<C> fn) { return addPreAction(null, fn); }
    public Pipeline<C> addAction(UnaryOperator<C> fn) { return addAction(null, fn); }
    public Pipeline<C> addPostAction(UnaryOperator<C> fn) { return addPostAction(null, fn); }

    public Pipeline<C> addPreAction(String actionName, StepAction<C> action) {
        preActions.add(RegisteredAction.named(actionName, action));
        return this;
    }
    public Pipeline<C> addAction(String actionName, StepAction<C> action) {
        actions.add(RegisteredAction.named(actionName, action));
        return this;
    }
    public Pipeline<C> addPostAction(String actionName, StepAction<C> action) {
        postActions.add(RegisteredAction.named(actionName, action));
        return this;
    }

    public Pipeline<C> addPreAction(String actionName, UnaryOperator<C> fn) { return addPreAction(actionName, adapt(fn)); }
    public Pipeline<C> addAction(String actionName, UnaryOperator<C> fn) { return addAction(actionName, adapt(fn)); }
    public Pipeline<C> addPostAction(String actionName, UnaryOperator<C> fn) { return addPostAction(actionName, adapt(fn)); }

    public C run(C input) {
        return execute(input).context();
    }

    public PipelineResult<C> execute(C input) {
        var rec = Metrics.recorder();

        long runStartNanos = System.nanoTime();
        C ctx = Objects.requireNonNull(input, "input");
        DefaultStepControl<C> control = new DefaultStepControl<>(name, onError);
        control.beginRun(runStartNanos);

        // pre: always run all pre-actions
        ctx = runPhase(control, rec, StepPhase.PRE, ctx, preActions, /*stopOnShortCircuit=*/false);

        // main: stop when control short-circuits
        if (!control.isShortCircuited()) {
            ctx = runPhase(control, rec, StepPhase.MAIN, ctx, actions, /*stopOnShortCircuit=*/true);
        }

        // post: always run all post-actions
        ctx = runPhase(control, rec, StepPhase.POST, ctx, postActions, /*stopOnShortCircuit=*/false);

        long totalNanos = System.nanoTime() - runStartNanos;
        return new PipelineResult<>(ctx, control.isShortCircuited(), control.errors(), control.actionTimings(), totalNanos);
    }

    public String name() { return name; }
    public boolean shortCircuitOnException() { return shortCircuitOnException; }
    public int size() { return actions.size(); }

    private C runPhase(DefaultStepControl<C> control,
                       com.pipeline.metrics.MetricsRecorder rec,
                       StepPhase phase,
                       C start,
                       List<RegisteredAction<C>> list,
                       boolean stopOnShortCircuit) {
        C ctx = start;
        for (int i = 0; i < list.size(); i++) {
            RegisteredAction<C> reg = list.get(i);
            String actionName = formatStepName(phase, i, reg.name());
            control.beginStep(phase, i, actionName);

            boolean wasShortCircuited = control.isShortCircuited();

            long actionStartNanos = System.nanoTime();
            boolean actionSucceeded = true;
            long elapsedNanos;
            try {
                C next = reg.action().apply(ctx, control);
                if (next == null) throw new IllegalStateException("Step returned null: " + actionName);
                ctx = next;
            } catch (Exception ex) {
                actionSucceeded = false;
                rec.onStepError(name, actionName, ex);
                ctx = control.recordError(ctx, ex);
                if (shortCircuitOnException) {
                    control.shortCircuit();
                    log.debug("short-circuit '{}' at {} due to exception", name, actionName, ex);
                }
            } finally {
                elapsedNanos = System.nanoTime() - actionStartNanos;
                control.recordTiming(elapsedNanos, actionSucceeded);
                if (actionSucceeded) {
                    rec.onStepSuccess(name, actionName, elapsedNanos);
                }
            }

            boolean isShortCircuitedNow = control.isShortCircuited();
            if (!wasShortCircuited && isShortCircuitedNow) {
                rec.onShortCircuit(name, actionName);
            }

            if (stopOnShortCircuit && isShortCircuitedNow) break;
        }
        return ctx;
    }

    private static String formatStepName(StepPhase phase, int idx, String labelOrNull) {
        String p = switch (phase) {
            case PRE -> "pre";
            case MAIN -> "s";
            case POST -> "post";
        };
        if (labelOrNull == null || labelOrNull.isBlank()) return p + idx;
        return p + idx + ":" + labelOrNull;
    }

    private static <C> StepAction<C> adapt(UnaryOperator<C> fn) {
        Objects.requireNonNull(fn, "fn");
        return (ctx, control) -> fn.apply(ctx);
    }

    private record RegisteredAction<C>(String name, StepAction<C> action) {
        private RegisteredAction {
            action = Objects.requireNonNull(action, "action");
        }

        static <C> RegisteredAction<C> named(String name, StepAction<C> action) {
            return new RegisteredAction<>(name, action);
        }
    }

    private static final class DefaultStepControl<C> implements StepControl<C> {
        private final String pipelineName;
        private final BiFunction<C, PipelineError, C> onError;
        private final List<PipelineError> errors = new ArrayList<>();
        private final List<ActionTiming> actionTimings = new ArrayList<>();

        private boolean shortCircuited;

        private StepPhase phase = StepPhase.MAIN;
        private int index = 0;
        private String stepName = "?";
        private long runStartNanos;

        private DefaultStepControl(String pipelineName, BiFunction<C, PipelineError, C> onError) {
            this.pipelineName = Objects.requireNonNull(pipelineName, "pipelineName");
            this.onError = Objects.requireNonNull(onError, "onError");
        }

        private void beginRun(long startNanos) {
            this.runStartNanos = startNanos;
        }

        private void beginStep(StepPhase phase, int index, String stepName) {
            this.phase = Objects.requireNonNull(phase, "phase");
            this.index = index;
            this.stepName = Objects.requireNonNull(stepName, "stepName");
        }

        private void recordTiming(long elapsedNanos, boolean success) {
            actionTimings.add(new ActionTiming(phase, index, stepName, elapsedNanos, success));
        }

        @Override
        public void shortCircuit() {
            this.shortCircuited = true;
        }

        @Override
        public boolean isShortCircuited() {
            return shortCircuited;
        }

        @Override
        public C recordError(C ctx, Exception exception) {
            PipelineError err = new PipelineError(pipelineName, phase, index, stepName, exception);
            errors.add(err);
            C next = onError.apply(ctx, err);
            if (next == null) throw new IllegalStateException("onError returned null");
            return next;
        }

        @Override
        public List<PipelineError> errors() {
            return List.copyOf(errors);
        }

        @Override
        public String pipelineName() {
            return pipelineName;
        }

        @Override
        public long runStartNanos() {
            return runStartNanos;
        }

        @Override
        public List<ActionTiming> actionTimings() {
            return List.copyOf(actionTimings);
        }
    }
}
