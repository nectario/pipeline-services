package com.ps.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

final class StepsTest {
    @Test
    void ignoreErrorsPassesThrough() throws Exception {
        var fn = Steps.ignoreErrors((String s) -> { throw new RuntimeException("boom"); });
        assertEquals("x", fn.apply("x"));
    }

    @Test
    void withFallbackReturnsFallback() throws Exception {
        var fn = Steps.withFallback((String s) -> { throw new RuntimeException("boom"); }, e -> "fallback");
        assertEquals("fallback", fn.apply("x"));
    }
}

