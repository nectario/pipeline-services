package com.pipeline.api.tests;

import com.pipeline.api.Pipeline;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class JumpEngineTypedTest {

  public record Tick(String sym, double px) {}
  public record Features(double vol) {}
  public record Score(double v) {}
  public sealed interface Order permits Accept, Reject {}
  public record Accept() implements Order {}
  public record Reject() implements Order {}

  @Test
  void jumpTypeMismatchThrowsClearError() throws Exception {
    var p = new Pipeline<Features, Features>().enableJumps(true).sleeper(ms -> {});
    // features -> (badJump)-> features ; then there is a 'decide' step expecting Score
    p.addAction("badJump", f -> { com.pipeline.core.Jumps.now("decide"); return f; });
    p.addAction("toScore", f -> new Score(Math.max(0, 1.0 - f.vol())));
    p.addAction("decide",  (Score s) -> (s.v() >= 0.5 ? new Accept() : new Reject()));

    IllegalStateException ex = assertThrows(IllegalStateException.class, () -> p.run(new Features(2.0), Order.class));
    assertTrue(ex.getMessage().contains("Jump type mismatch"), ex.getMessage());
    assertTrue(ex.getMessage().contains("target 'decide'"), ex.getMessage());
  }
}
