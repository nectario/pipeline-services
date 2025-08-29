package com.ps.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

final class PipeTest {

    record Req(String symbol, int qty) {}
    sealed interface Res permits Ok, Rejected {}
    record Ok(double px) implements Res {}
    record Rejected(String reason) implements Res {}

    @Test
    void shortCircuitTrueOnErrorReturnUsed() throws Exception {
        var pipe = Pipe.from(Req.class)
                .step((Req r) -> r)
                .step((Req r) -> { throw new RuntimeException("pricing"); })
                .shortCircuit(true)
                .onErrorReturn(e -> new Rejected("PricingError"))
                .to(Res.class);
        Res r = pipe.run(new Req("AAPL", 10));
        assertInstanceOf(Rejected.class, r);
    }

    @Test
    void shortCircuitTrueWithoutOnErrorRethrows() {
        var pipe = Pipe.from(Req.class)
                .step((Req r) -> r)
                .step((Req r) -> { throw new RuntimeException("pricing"); })
                .shortCircuit(true)
                .to(Res.class);
        assertThrows(Exception.class, () -> pipe.run(new Req("AAPL", 10)));
    }

    @Test
    void shortCircuitFalseKeepsCurrent() throws Exception {
        var pipe = Pipe.from(Req.class)
                .step((Req r) -> r)
                .step(Steps.withFallback((Req r) -> { throw new RuntimeException("pricing"); }, e -> new Ok(1.0)))
                .shortCircuit(false)
                .to(Res.class);
        Res r = pipe.run(new Req("AAPL", 10));
        assertInstanceOf(Ok.class, r);
    }
}

