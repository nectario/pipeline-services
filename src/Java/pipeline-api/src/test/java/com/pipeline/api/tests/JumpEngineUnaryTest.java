package com.pipeline.api.tests;

import com.pipeline.api.Pipeline;
import com.pipeline.core.Jumps;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

public class JumpEngineUnaryTest {

  @Test
  void selfLoopUnaryStopsAndReturns() throws Exception {
    AtomicInteger attempts = new AtomicInteger();
    var p = Pipeline.<String>named("unary_loop", false)
        .enableJumps(true)
        .sleeper(ms -> {}) // don't actually sleep
        .addAction("await", s -> {
          if (attempts.incrementAndGet() < 3) {
            com.pipeline.core.Jumps.now("await"); // self loop
          }
          return s;
        })
        .addAction(s -> s + ":done");

    String out = p.run("x");
    assertEquals("x:done", out);
    assertEquals(3, attempts.get(), "should loop exactly 3 times");
  }
}
