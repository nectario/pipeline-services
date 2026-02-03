package com.pipeline.core;

import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class PooledLocalActionsProgrammaticTest {

    @Test
    void pooledLocalActionsAllowConcurrentRunsWithoutSharingActionInstances() throws Exception {
        BlockingResettableAction.installLatches(new CountDownLatch(2), new CountDownLatch(1));
        BlockingResettableAction.resetCounters();

        Supplier<Pipeline<String>> pipelineFactory = () -> new Pipeline<String>("pooled_local_actions", true)
            .addAction("stateful", new BlockingResettableAction());

        ActionPoolCache actionPoolCache = new ActionPoolCache(/*maxPerAction=*/2);
        PipelineProvider<String> provider = PipelineProvider.pooled(pipelineFactory, /*poolMax=*/2)
            .withPooledLocalActions(actionPoolCache);

        CapturedResult firstCaptured = new CapturedResult();
        CapturedResult secondCaptured = new CapturedResult();

        Thread firstThread = new Thread(() -> firstCaptured.value = provider.run("a").context(), "test-run-1");
        Thread secondThread = new Thread(() -> secondCaptured.value = provider.run("b").context(), "test-run-2");

        firstThread.start();
        secondThread.start();

        assertTrue(BlockingResettableAction.awaitBothStarted(2, TimeUnit.SECONDS), "both action instances should start");
        BlockingResettableAction.allowBothToProceed();

        firstThread.join(2_000);
        secondThread.join(2_000);

        assertEquals(2, BlockingResettableAction.resetCount(), "each run should reset its action instance");
    }

    private static final class CapturedResult {
        private volatile String value = "";
    }

    private static final class BlockingResettableAction implements StepAction<String>, ResettableAction {
        private static final AtomicInteger instanceCounter = new AtomicInteger(0);
        private static final AtomicInteger resetCounter = new AtomicInteger(0);
        private static volatile CountDownLatch startedLatch;
        private static volatile CountDownLatch proceedLatch;

        private final int instanceId;
        private final AtomicBoolean inUse;

        private BlockingResettableAction() {
            this.instanceId = instanceCounter.incrementAndGet();
            this.inUse = new AtomicBoolean(false);
        }

        static void installLatches(CountDownLatch startedLatchInput, CountDownLatch proceedLatchInput) {
            startedLatch = startedLatchInput;
            proceedLatch = proceedLatchInput;
        }

        static boolean awaitBothStarted(long timeout, TimeUnit unit) throws InterruptedException {
            CountDownLatch currentLatch = startedLatch;
            if (currentLatch == null) throw new IllegalStateException("startedLatch is not installed");
            return currentLatch.await(timeout, unit);
        }

        static void allowBothToProceed() {
            CountDownLatch currentLatch = proceedLatch;
            if (currentLatch == null) throw new IllegalStateException("proceedLatch is not installed");
            currentLatch.countDown();
        }

        static void resetCounters() {
            instanceCounter.set(0);
            resetCounter.set(0);
        }

        static int resetCount() {
            return resetCounter.get();
        }

        @Override
        public String apply(String input, ActionControl<String> control) {
            if (!inUse.compareAndSet(false, true)) {
                throw new IllegalStateException("Action instance used concurrently: " + instanceId);
            }

            CountDownLatch currentStartedLatch = startedLatch;
            if (currentStartedLatch != null) {
                currentStartedLatch.countDown();
            }

            try {
                CountDownLatch currentProceedLatch = proceedLatch;
                if (currentProceedLatch != null) {
                    if (!currentProceedLatch.await(2, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("Timed out waiting for proceedLatch");
                    }
                }
            } catch (InterruptedException interruptedException) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Interrupted while waiting for proceedLatch");
            } finally {
                inUse.set(false);
            }

            return input + "|" + instanceId;
        }

        @Override
        public void reset() {
            resetCounter.incrementAndGet();
        }
    }
}

