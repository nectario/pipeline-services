package com.pipeline.examples.bloomberg;

import com.pipeline.api.Pipeline;
import com.pipeline.examples.steps.FinanceSteps;
import com.pipeline.examples.steps.FinanceSteps.*;

public class BloombergRFQPipeline extends Pipeline<FinanceSteps.Tick, FinanceSteps.Tick> implements AutoCloseable {
  private final BloombergSession session;

  public BloombergRFQPipeline(BloombergSession session) {
    this.session = session;
    // Also register as a bean so JSON can target '@this'
    this.addBean("@session", session);
    this.name("bloomberg_rfq").shortCircuit(true);
    // Programmatic composition; JSON may append more steps before first run
    this.before(this::ensureSession)
        .addAction(this::computeFeatures)     // Tick -> Features
        .addAction(FinanceSteps::score)       // Features -> Score
        .addAction(FinanceSteps::decide);     // Score -> OrderResponse
  }

  /* -------- instance actions -------- */
  private FinanceSteps.Tick ensureSession(FinanceSteps.Tick t) throws Exception {
    if (!session.isOpen()) session.open();
    return t;
  }

  private FinanceSteps.Features computeFeatures(FinanceSteps.Tick t) throws Exception {
    double r1 = 0.0;
    double vol = Math.abs(t.price()) * 0.01;
    // pretend we adjust using session/env
    if ("prod".equalsIgnoreCase(session.toString())) { /* nothing */ }
    return new FinanceSteps.Features(r1, vol);
  }

  @Override public void close() { if (session != null && session.isOpen()) session.close(); }
}
