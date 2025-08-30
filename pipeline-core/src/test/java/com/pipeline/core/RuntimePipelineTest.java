package com.pipeline.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

final class RuntimePipelineTest {

  private static String upper(String s) throws Exception { return s.toUpperCase(); }

  @Test
  void continuesOnErrorWhenShortCircuitFalse() throws Exception {
    RuntimePipeline<String> rt = new RuntimePipeline<>("t", false, "hi");
    // step that throws
    rt.addStep(s -> { throw new RuntimeException("boom"); });
    // continues with current unchanged
    assertEquals("hi", rt.value());
    // next step still runs
    rt.addStep(RuntimePipelineTest::upper); // method ref
    assertEquals("HI", rt.value());
  }

  @Test
  void shortCircuitsOnErrorWhenShortCircuitTrue() {
    RuntimePipeline<String> rt = new RuntimePipeline<>("t", true, "hello");
    rt.addStep(s -> { throw new RuntimeException("boom"); });
    // last good value returned/kept
    assertEquals("hello", rt.value());
    // with shortCircuit=true, session ends; subsequent adds are ignored until reset
    rt.addStep(RuntimePipelineTest::upper);
    assertEquals("hello", rt.value());
  }

  @Test
  void explicitShortCircuitNowStopsEarly() throws Exception {
    RuntimePipeline<String> rt = new RuntimePipeline<>("t", false, "hello");
    rt.addStep(s -> ShortCircuit.now("FINISH"));
    assertEquals("FINISH", rt.value());
    // further steps ignored until reset
    rt.addStep(RuntimePipelineTest::upper);
    assertEquals("FINISH", rt.value());
  }
}
