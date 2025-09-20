package com.pipeline.api.tests;

import com.pipeline.api.Pipeline;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** This test exercises the compiled core path (jumps disabled). */
public class MetricsCompiledPathTest {

  @Test
  void compiledPathReportsSteps() throws Exception {
    var m = new TestMetrics();
    var p = com.pipeline.api.Pipeline.<String,String>named("compiled", true)
        .metrics(m)
        .addAction("a", s -> s + "a")
        .addAction("b", s -> s + "b"); // jumps disabled -> core compiled path

    String out = p.run("");
    assertEquals("ab", out);
    assertTrue(m.events.stream().anyMatch(e -> e.equals("pipeline.end:true")));
    // Expect both steps recorded
    assertTrue(m.events.stream().anyMatch(e -> e.startsWith("step.start:0:a")));
    assertTrue(m.events.stream().anyMatch(e -> e.startsWith("step.start:1:b")));
  }
}
