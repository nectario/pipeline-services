package com.pipeline.api.tests;

import com.pipeline.api.Pipeline;
import com.pipeline.core.Jumps;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class MetricsJumpEngineTest {

  @Test
  void recordsStepAndJumpEvents() throws Exception {
    var m = new TestMetrics();
    var p = new Pipeline<String,String>().enableJumps(true).sleeper(ms -> {}).metrics(m)
        .addAction("await", s -> { Jumps.now("await"); return s; }) // will jump once and hit guard later
        .maxJumpsPerRun(1)
        .addAction("done", s -> s + "x");

    IllegalStateException ex = assertThrows(IllegalStateException.class, () -> p.run("a"));
    assertTrue(m.jumps.get() >= 1);
    assertTrue(m.events.get(0).startsWith("pipeline.start"));
    assertTrue(m.events.stream().anyMatch(e -> e.startsWith("step.start:")));
    assertTrue(m.events.stream().anyMatch(e -> e.startsWith("step.end:")));
    assertTrue(m.events.stream().anyMatch(e -> e.startsWith("pipeline.end:false")));
  }
}
