package com.pipeline.core;

import com.pipeline.metrics.Metrics;
import com.pipeline.metrics.SimpleMetricsRecorder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

final class PipelineTest {

    @BeforeEach
    void setup() {
        Metrics.setRecorder(new SimpleMetricsRecorder());
    }

    @Test
    void shortCircuitOnExceptionTrueStopsAndCaptures() {
        var p = new Pipeline<String>("t1", true)
            .addAction(s -> s + "A")
            .addAction((s, control) -> { throw new RuntimeException("boom"); })
            .addAction(s -> s + "B");

        PipelineResult<String> r = p.run("X");
        assertEquals("XA", r.context());
        assertTrue(r.shortCircuited());
        assertEquals(1, r.errors().size());
        assertEquals("boom", r.errors().getFirst().exception().getMessage());
    }

    @Test
    void shortCircuitOnExceptionFalseContinuesAndCaptures() {
        var p = new Pipeline<String>("t2", false)
            .addAction(s -> s + "A")
            .addAction((s, control) -> { throw new RuntimeException("boom"); })
            .addAction(s -> s + "B");

        PipelineResult<String> r = p.run("X");
        assertEquals("XAB", r.context());
        assertFalse(r.shortCircuited());
        assertEquals(1, r.errors().size());
    }

    @Test
    void explicitShortCircuitStopsMainActions() {
        var p = new Pipeline<String>("t3", true)
            .addAction((s, control) -> { control.shortCircuit(); return "END"; })
            .addAction(s -> s + "B");

        PipelineResult<String> r = p.run("X");
        assertEquals("END", r.context());
        assertTrue(r.shortCircuited());
        assertTrue(r.errors().isEmpty());
    }
}
