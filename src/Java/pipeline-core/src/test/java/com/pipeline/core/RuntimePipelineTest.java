package com.pipeline.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

final class RuntimePipelineTest {

  private static String upper(String s) { return s.toUpperCase(); }

  @Test
  void continuesOnErrorWhenShortCircuitFalse() {
    RuntimePipeline<String> rt = new RuntimePipeline<>("t", false, "hi");
    // step that throws
    rt.addAction((s, control) -> { throw new RuntimeException("boom"); });
    // continues with current unchanged
    assertEquals("hi", rt.value());
    // next step still runs
    rt.addAction(RuntimePipelineTest::upper); // method ref
    assertEquals("HI", rt.value());
  }

  @Test
  void shortCircuitsOnErrorWhenShortCircuitTrue() {
    RuntimePipeline<String> rt = new RuntimePipeline<>("t", true, "hello");
    rt.addAction((s, control) -> { throw new RuntimeException("boom"); });
    // last good value returned/kept
    assertEquals("hello", rt.value());
    // with shortCircuit=true, session ends; subsequent adds are ignored until reset
    rt.addAction(RuntimePipelineTest::upper);
    assertEquals("hello", rt.value());
  }

  @Test
  void explicitShortCircuitStopsEarly() {
    RuntimePipeline<String> rt = new RuntimePipeline<>("t", false, "hello");
    rt.addAction((s, control) -> { control.shortCircuit(); return "FINISH"; });
    assertEquals("FINISH", rt.value());
    // further steps ignored until reset
    rt.addAction(RuntimePipelineTest::upper);
    assertEquals("FINISH", rt.value());
  }
}
