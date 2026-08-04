package com.pipeline.core;

import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static com.pipeline.core.PipelineExecution.shortCircuit;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class PipelineSingletonConcurrencyTest {

  @Test
  void overlappingSingletonRunsKeepExecutionStateIsolated() throws Exception {
    CountDownLatch bothRunsStarted = new CountDownLatch(2);
    CountDownLatch allowCompletion = new CountDownLatch(1);

    Pipeline<String> sharedPipeline = new Pipeline<String>("singleton_shared")
        .addAction(value -> {
          bothRunsStarted.countDown();
          await(allowCompletion);
          if (value.startsWith("stop")) shortCircuit();
          return value + "|A";
        })
        .addAction(value -> value + "|B")
        .addPostAction(value -> value + "|P");

    PipelineProvider<String> provider = PipelineProvider.singleton(sharedPipeline);
    CapturedResult stoppedRun = new CapturedResult();
    CapturedResult continuingRun = new CapturedResult();

    Thread stoppedThread = new Thread(
        () -> stoppedRun.value = provider.run("stop"),
        "singleton-stop-run");
    Thread continuingThread = new Thread(
        () -> continuingRun.value = provider.run("continue"),
        "singleton-continue-run");

    stoppedThread.start();
    continuingThread.start();

    assertTrue(
        bothRunsStarted.await(2, TimeUnit.SECONDS),
        "both singleton runs should overlap");
    allowCompletion.countDown();

    stoppedThread.join(2_000);
    continuingThread.join(2_000);

    assertEquals("stop|A|P", stoppedRun.value);
    assertEquals("continue|A|B|P", continuingRun.value);
  }

  private static void await(CountDownLatch latch) {
    try {
      if (!latch.await(2, TimeUnit.SECONDS)) {
        throw new IllegalStateException("Timed out waiting for test latch");
      }
    } catch (InterruptedException interruptedException) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("Interrupted while waiting for test latch", interruptedException);
    }
  }

  private static final class CapturedResult {
    private volatile String value = "";
  }
}
