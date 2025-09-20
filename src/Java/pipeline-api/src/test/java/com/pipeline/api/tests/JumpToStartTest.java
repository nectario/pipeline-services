package com.pipeline.api.tests;

import com.pipeline.api.Pipeline;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.util.concurrent.atomic.AtomicInteger;

public class JumpToStartTest {

  @Test
  void startAtLabelSkipsEarlierSteps() throws Exception {
    AtomicInteger a = new AtomicInteger(), b = new AtomicInteger(), c = new AtomicInteger();
    var p = Pipeline.<String,String>named("start_label", false)
        .enableJumps(true)
        .sleeper(ms -> {})
        .addAction("a", s -> { a.incrementAndGet(); return s + "a"; })
        .addAction("b", s -> { b.incrementAndGet(); return s + "b"; })
        .addAction("c", s -> { c.incrementAndGet(); return s + "c"; });

    p.jumpTo("b");
    String out = p.run("");
    assertEquals("bc", out);
    assertEquals(0, a.get(), "a should not run");
    assertEquals(1, b.get());
    assertEquals(1, c.get());
  }
}
