package com.pipeline.examples;

import com.pipeline.examples.disruptor.DisruptorStockAlertsExampleProgrammatic;
import com.pipeline.examples.disruptor.DisruptorStockAlertsExampleJson;

public final class ExamplesMain {
  public static void main(String[] args) {
    // Programmatic pipeline example
    DisruptorStockAlertsExampleProgrammatic.run();

    // JSON-configured pipeline example
    DisruptorStockAlertsExampleJson.run();
  }
}
