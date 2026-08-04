package com.pipeline.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class RuntimePipelineFreezeTest {

  static String strip(String value) { return value == null ? "" : value.strip(); }
  static String upper(String value) { return value.toUpperCase(); }
  static String shortCircuitToX(String value, ActionControl<String> control) {
    control.shortCircuit();
    return "X";
  }

  @Test
  void freezeBuildsEquivalentPipeline() {
    RuntimePipeline<String> runtimePipeline = new RuntimePipeline<>("t", false, "  hello  ");
    runtimePipeline.addPreAction(RuntimePipelineFreezeTest::strip);
    runtimePipeline.addAction(RuntimePipelineFreezeTest::upper);

    assertEquals("HELLO", runtimePipeline.value());

    Pipeline<String> frozen = runtimePipeline.toImmutable();
    assertEquals("HELLO", frozen.run("  hello  "));
  }

  @Test
  void afterShortCircuitAddsAreIgnoredUntilReset() {
    RuntimePipeline<String> runtimePipeline = new RuntimePipeline<>("t", false, "abc");
    runtimePipeline.addAction(RuntimePipelineFreezeTest::shortCircuitToX);
    assertEquals("X", runtimePipeline.value());
    assertEquals(1, runtimePipeline.recordedActionCount());

    // Legacy RuntimePipeline keeps its existing immediate-recording behavior in Phase 2.
    runtimePipeline.addAction(RuntimePipelineFreezeTest::upper);
    runtimePipeline.addPostAction(RuntimePipelineFreezeTest::strip);
    assertEquals(1, runtimePipeline.recordedActionCount());
    assertEquals(0, runtimePipeline.recordedPostActionCount());

    runtimePipeline.reset("again");
    runtimePipeline.addAction(RuntimePipelineFreezeTest::upper);
    assertEquals(2, runtimePipeline.recordedActionCount());
  }
}
