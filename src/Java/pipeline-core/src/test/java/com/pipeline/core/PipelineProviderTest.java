package com.pipeline.core;

import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;

final class PipelineProviderTest {

  @Test
  void sharedReusesTheSamePipelineInstance() {
    AtomicInteger instanceCounter = new AtomicInteger(0);
    Supplier<Pipeline<String>> pipelineFactory = () -> new Pipeline<String>("shared", true)
        .addAction(new InstanceIdAppendAction(instanceCounter));

    PipelineProvider<String> provider = PipelineProvider.shared(pipelineFactory);

    String first = provider.run("a").context();
    String second = provider.run("b").context();

    assertEquals(idSuffix(first), idSuffix(second));
  }

  @Test
  void perRunCreatesANewPipelineInstanceEachRun() {
    AtomicInteger instanceCounter = new AtomicInteger(0);
    Supplier<Pipeline<String>> pipelineFactory = () -> new Pipeline<String>("per_run", true)
        .addAction(new InstanceIdAppendAction(instanceCounter));

    PipelineProvider<String> provider = PipelineProvider.perRun(pipelineFactory);

    String first = provider.run("a").context();
    String second = provider.run("b").context();

    assertNotEquals(idSuffix(first), idSuffix(second));
  }

  @Test
  void pooledNeverSharesAPipelineInstanceConcurrently() throws Exception {
    AtomicInteger instanceCounter = new AtomicInteger(0);
    CountDownLatch startedLatch = new CountDownLatch(2);
    CountDownLatch proceedLatch = new CountDownLatch(1);

    Supplier<Pipeline<String>> pipelineFactory = () -> new Pipeline<String>("pooled", true)
        .addAction(new BlockingIdAppendAction(instanceCounter, startedLatch, proceedLatch));

    PipelineProvider<String> provider = PipelineProvider.pooled(pipelineFactory, 2);

    CapturedResult firstCaptured = new CapturedResult();
    CapturedResult secondCaptured = new CapturedResult();

    Thread firstThread = new Thread(() -> firstCaptured.value = provider.run("a").context(), "test-run-1");
    Thread secondThread = new Thread(() -> secondCaptured.value = provider.run("b").context(), "test-run-2");

    firstThread.start();
    secondThread.start();

    assertTrue(startedLatch.await(2, TimeUnit.SECONDS), "both runs should start");
    proceedLatch.countDown();

    firstThread.join(2_000);
    secondThread.join(2_000);

    assertNotEquals(idSuffix(firstCaptured.value), idSuffix(secondCaptured.value));
  }

  private static String idSuffix(String value) {
    int split = value.lastIndexOf('|');
    if (split < 0) return "";
    return value.substring(split + 1);
  }

  private static final class CapturedResult {
    private volatile String value = "";
  }

  private static final class InstanceIdAppendAction implements UnaryOperator<String> {
    private final int instanceId;

    private InstanceIdAppendAction(AtomicInteger counter) {
      this.instanceId = counter.incrementAndGet();
    }

    @Override
    public String apply(String input) {
      return input + "|" + instanceId;
    }
  }

  private static final class BlockingIdAppendAction implements UnaryOperator<String> {
    private final int instanceId;
    private final CountDownLatch startedLatch;
    private final CountDownLatch proceedLatch;

    private BlockingIdAppendAction(AtomicInteger counter, CountDownLatch startedLatch, CountDownLatch proceedLatch) {
      this.instanceId = counter.incrementAndGet();
      this.startedLatch = startedLatch;
      this.proceedLatch = proceedLatch;
    }

    @Override
    public String apply(String input) {
      startedLatch.countDown();
      try {
        if (!proceedLatch.await(2, TimeUnit.SECONDS)) {
          throw new IllegalStateException("Timed out waiting for proceedLatch");
        }
      } catch (InterruptedException interruptedException) {
        Thread.currentThread().interrupt();
        throw new IllegalStateException("Interrupted while waiting for proceedLatch");
      }
      return input + "|" + instanceId;
    }
  }
}

