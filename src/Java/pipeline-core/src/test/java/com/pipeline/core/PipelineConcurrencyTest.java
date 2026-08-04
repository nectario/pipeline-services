package com.pipeline.core;

import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static com.pipeline.core.PipelineExecution.shortCircuit;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class PipelineConcurrencyTest {

  @Test
  void overlappingRunsOnOnePooledInstanceKeepShortCircuitStateIsolated() throws Exception {
    CountDownLatch bothRunsStarted = new CountDownLatch(2);
    CountDownLatch allowCompletion = new CountDownLatch(1);

    PipelineProvider<String> provider = PipelineProvider.pooled(
        () -> new Pipeline<String>("shared_instance")
            .addAction(value -> {
              bothRunsStarted.countDown();
              await(allowCompletion);
              if (value.startsWith("stop")) shortCircuit();
              return value + "|A";
            })
            .addAction(value -> value + "|B")
            .addPostAction(value -> value + "|P"),
        1);

    CapturedResult stoppedRun = new CapturedResult();
    CapturedResult continuingRun = new CapturedResult();

    Thread firstThread = new Thread(
        () -> stoppedRun.value = provider.run("stop"),
        "pipeline-stop-run");
    Thread secondThread = new Thread(
        () -> continuingRun.value = provider.run("continue"),
        "pipeline-continue-run");

    firstThread.start();
    secondThread.start();

    assertTrue(
        bothRunsStarted.await(2, TimeUnit.SECONDS),
        "both runs should overlap on the same pooled instance");
    allowCompletion.countDown();

    firstThread.join(2_000);
    secondThread.join(2_000);

    assertEquals("stop|A|P", stoppedRun.value);
    assertEquals("continue|A|B|P", continuingRun.value);
  }

  @Test
  void nestedShortCircuitTargetsOnlyTheInnermostActiveRun() {
    Pipeline<String> innerPipeline = new Pipeline<String>("inner")
        .addAction(value -> {
          shortCircuit();
          return value + "|I1";
        })
        .addAction(value -> value + "|I2")
        .addPostAction(value -> value + "|IP");

    Pipeline<String> outerPipeline = new Pipeline<String>("outer")
        .addAction(value -> value + ":" + innerPipeline.run("inner"))
        .addAction(value -> value + "|O2")
        .addPostAction(value -> value + "|OP");

    assertEquals(
        "outer:inner|I1|IP|O2|OP",
        outerPipeline.run("outer"));
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
