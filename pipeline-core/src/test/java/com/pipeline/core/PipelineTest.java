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
    void shortCircuitTrueReturnsLastGood() {
        var p = Pipeline.build("t1", true,
                (String s) -> s + "A",
                (String s) -> { throw new RuntimeException("boom"); },
                (String s) -> s + "B");
        assertEquals("XA", p.run("X"));
    }

    @Test
    void shortCircuitFalseContinues() {
        var p = Pipeline.build("t2", false,
                (String s) -> s + "A",
                (String s) -> { throw new RuntimeException("boom"); },
                (String s) -> s + "B");
        assertEquals("XAB", p.run("X"));
    }

    @Test
    void nowReturnsImmediately() {
        var p = Pipeline.build("t3", true,
                (String s) -> ShortCircuit.now("END"),
                (String s) -> s + "B");
        assertEquals("END", p.run("X"));
    }
}

