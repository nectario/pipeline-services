package com.pipeline.api.tests;

import com.pipeline.api.Pipeline;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class MetricsErrorTest {

  @Test
  void errorRecordedAndShortCircuited() {
    var m = new TestMetrics();
    var p = com.pipeline.api.Pipeline.<String,String>named("err", true).metrics(m)
        .addAction("ok", s -> s + "ok")
        .addAction("boom", s -> { throw new IllegalStateException("x"); });

    Exception ex = assertThrows(Exception.class, () -> p.run(""));
    assertTrue(m.events.stream().anyMatch(e -> e.startsWith("step.error:1:boom:IllegalStateException")));
    assertTrue(m.events.stream().anyMatch(e -> e.equals("pipeline.end:false")));
  }
}
