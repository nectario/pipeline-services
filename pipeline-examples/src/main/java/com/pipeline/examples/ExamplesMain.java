package com.pipeline.examples;

public final class ExamplesMain {
  public static void main(String[] args) throws Exception {
    System.out.println("== Pipeline Services Examples ==");
    Example01_TextClean.run();
    Example02_ShortCircuitOnException.run();
    Example03_CsvToJson.run();
    Example04_FinanceOrderFlow.run();
    Example05_TypedWithFallback.run();
    Example06_PrePostPolicies.run();
    Example07_ListDedupSort.run();
    Example08_IntArrayStats.run();
    Example09_LoadFromJsonConfig.run();
    Example10_DisruptorIntegration.run();
    System.out.println("-- done --");
  }
}

