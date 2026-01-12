package com.pipeline.api.tests;

import com.pipeline.api.Pipeline;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class JumpEngineGuardsTest {

  @Test
  void cannotJumpIntoPre() throws Exception {
    var p = Pipeline.<String>named("no_pre_jump", false)
        .enableJumps(true)
        .sleeper(ms -> {})
        .before("init", s -> s + "#init")
        .addAction("main", s -> { com.pipeline.core.Jumps.now("init"); return s; });

    IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> p.run("x"));
    assertTrue(ex.getMessage().contains("Jump into 'pre' is not allowed"));
  }

  @Test
  void maxJumpsGuardTrips() {
    var p = Pipeline.<String>named("guard", false)
        .enableJumps(true)
        .maxJumpsPerRun(2)
        .sleeper(ms -> {})
        .addAction("loop", s -> { com.pipeline.core.Jumps.now("loop"); return s; });

    IllegalStateException ex = assertThrows(IllegalStateException.class, () -> p.run("x"));
    assertTrue(ex.getMessage().contains("Too many jumps"));
  }
}
