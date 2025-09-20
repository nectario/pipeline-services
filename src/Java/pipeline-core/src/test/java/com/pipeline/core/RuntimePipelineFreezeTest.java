package com.pipeline.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

final class RuntimePipelineFreezeTest {

  static String strip(String s) { return s == null ? "" : s.strip(); }
  static String upper(String s) { return s.toUpperCase(); }
  static String scToX(String s) { return ShortCircuit.now("X"); }

  @Test
  void freezeBuildsEquivalentPipeline() {
    var rt = new RuntimePipeline<>("t", false, "  hello  ");
    rt.addPreAction(RuntimePipelineFreezeTest::strip);
    rt.addStep(RuntimePipelineFreezeTest::upper);

    assertEquals("HELLO", rt.value());

    var frozen = rt.toImmutable();
    assertEquals("HELLO", frozen.run("  hello  "));
  }

  @Test
  void afterShortCircuitAddsAreIgnoredUntilReset() {
    var rt = new RuntimePipeline<>("t", false, "abc");
    rt.addStep(RuntimePipelineFreezeTest::scToX);
    assertEquals("X", rt.value());
    assertEquals(1, rt.recordedStepCount());

    // These should be NO-OPs (not recorded and not executed)
    rt.addStep(RuntimePipelineFreezeTest::upper);
    rt.addPostAction(RuntimePipelineFreezeTest::strip);
    assertEquals(1, rt.recordedStepCount());
    assertEquals(0, rt.recordedPostCount());

    // After reset, recording resumes
    rt.reset("again");
    rt.addStep(RuntimePipelineFreezeTest::upper);
    assertEquals(2, rt.recordedStepCount());
  }
}

