package com.pipeline.api.tests;

import com.pipeline.api.Pipeline;
import com.pipeline.core.ThrowingFn;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class JumpEngineTypedTest {

  public record Features(double vol) {}
  public record Score(double v) {}
  public sealed interface Order permits Accept, Reject {}
  public record Accept() implements Order {}
  public record Reject() implements Order {}

  static final class BadJump implements ThrowingFn<Features, Features> {
    @Override public Features apply(Features f) {
      com.pipeline.core.Jumps.now("decide");
      return f;
    }
  }

  static final class ToScore implements ThrowingFn<Features, Score> {
    @Override public Score apply(Features f) {
      return new Score(Math.max(0, 1.0 - f.vol()));
    }
  }

  static final class Decide implements ThrowingFn<Score, Order> {
    @Override public Order apply(Score s) {
      return (s.v() >= 0.5 ? new Accept() : new Reject());
    }
  }

  @Test
  void jumpTypeMismatchThrowsClearError() throws Exception {
    var p = new Pipeline<Features, Features>()
        .enableJumps(true)
        .sleeper(ms -> {})
        // features -> (badJump)-> features ; then there is a 'decide' step expecting Score
        .addAction("badJump", new BadJump())
        .addAction("toScore", new ToScore())
        .addAction("decide", new Decide());

    IllegalStateException ex = assertThrows(IllegalStateException.class, () -> p.run(new Features(2.0), Order.class));
    assertTrue(ex.getMessage().contains("Jump type mismatch"), ex.getMessage());
    assertTrue(ex.getMessage().contains("target 'decide'"), ex.getMessage());
  }
}
