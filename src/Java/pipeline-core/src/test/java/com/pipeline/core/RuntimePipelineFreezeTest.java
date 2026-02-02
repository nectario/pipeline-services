package com.pipeline.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

final class RuntimePipelineFreezeTest {

  static String strip(String s) { return s == null ? "" : s.strip(); }
  static String upper(String s) { return s.toUpperCase(); }
  static String scToX(String s, ActionControl<String> control) { control.shortCircuit(); return "X"; }

  @Test
  void freezeBuildsEquivalentPipeline() {
    var rt = new RuntimePipeline<>("t", false, "  hello  ");
    rt.addPreAction(RuntimePipelineFreezeTest::strip);
    rt.addAction(RuntimePipelineFreezeTest::upper);

    assertEquals("HELLO", rt.value());

    var frozen = rt.toImmutable();
    assertEquals("HELLO", frozen.run("  hello  ").context());
  }

  @Test
  void afterShortCircuitAddsAreIgnoredUntilReset() {
    var rt = new RuntimePipeline<>("t", false, "abc");
    rt.addAction(RuntimePipelineFreezeTest::scToX);
    assertEquals("X", rt.value());
    assertEquals(1, rt.recordedActionCount());

    // These should be NO-OPs (not recorded and not executed)
    rt.addAction(RuntimePipelineFreezeTest::upper);
    rt.addPostAction(RuntimePipelineFreezeTest::strip);
    assertEquals(1, rt.recordedActionCount());
    assertEquals(0, rt.recordedPostActionCount());

    // After reset, recording resumes
    rt.reset("again");
    rt.addAction(RuntimePipelineFreezeTest::upper);
    assertEquals(2, rt.recordedActionCount());
  }
}
