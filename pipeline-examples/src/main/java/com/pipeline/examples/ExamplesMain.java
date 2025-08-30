package com.pipeline.examples;

public final class ExamplesMain {
  public static void main(String[] args) throws Exception {
    System.out.println("== Pipeline Services Examples ==");
    Example01_TextClean.run();
    Example02ShortCircuitOnException.run();
    Example03CsvToJson.run();
    Example04FinanceOrderFlow.run();
    Example05TypedWithFallback.run();
    Example06PrePostPolicies.run();
    Example07ListDedupSort.run();
    Example08IntArrayStats.run();
    Example09LoadFromJsonConfig.run();
    Example10DisruptorIntegration.run();
    ExampleRuntimeImperative.run();
    System.out.println("-- done --");
  }
}
